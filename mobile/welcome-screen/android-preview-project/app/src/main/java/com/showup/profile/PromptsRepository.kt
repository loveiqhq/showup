/*
 * PromptsRepository.kt
 * ShowUp · what the prompts step asks the backend, and what the answers mean (SHOWUP-158)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE API IS KEYED ON THE TOPIC, WHICH IS WHY SAVE IS ONE CALL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `PUT /me/prompts/{topicId}` creates or overwrites. The screen already guarantees one prompt per
 * topic -- a used topic renders disabled in the picker -- so the topic IS the identity the client
 * already holds, and the first save and every later one are the same call. The alternative, a POST
 * returning an id and a PATCH by that id, would make one button two different requests depending
 * on whether it had been pressed before.
 *
 * It is also idempotent, which is what makes a double-tap on Save harmless.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT 400 MEANS HERE, AND WHY IT IS NOT SHOWN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The server refuses three things: a blank answer, one over 160 characters, and a FOURTH topic.
 * The screen prevents all three before the call -- Save nudges on an empty field, the field slices
 * at 160, and the suggestion block disappears at three saved -- so a 400 means the client and the
 * server disagree about a rule, which is a bug rather than something to word for a user. It is
 * reported as [SavePromptResult.Failed] and the slot keeps its draft.
 */
package com.showup.profile

import com.showup.BuildConfig
import com.showup.api.ApiError
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.UpsertPromptDto

/** The answer to "store this answer against this topic". */
sealed interface SavePromptResult {
    data class Saved(val prompt: SavedPrompt) : SavePromptResult

    /**
     * The write did not land.
     *
     * One case, not several. The three refusals the server can produce are all things the screen
     * has already prevented, so telling them apart would be wording three sentences nobody can
     * reach. The draft is kept either way.
     */
    data class Failed(val error: ApiError?) : SavePromptResult
}

/** The answer to "remove this prompt". */
sealed interface RemovePromptResult {
    data object Removed : RemovePromptResult
    data class Failed(val error: ApiError?) : RemovePromptResult
}

/**
 * Reads and writes the account's prompts.
 *
 * `open` for the same reason as [PhotosRepository]: a test subclasses it and answers from a queue.
 */
open class PromptsRepository(
    private val api: ShowUpApi,
    /**
     * The stand-in used when NOTHING ANSWERED, or null to let that failure be a failure.
     *
     * Injected rather than an inlined `BuildConfig.DEBUG` check, so the release behaviour of this
     * path can be asserted at all -- unit tests build DEBUG.
     */
    private val offline: DevOfflinePrompts? = if (BuildConfig.DEBUG) DevOfflinePrompts else null,
) {

    /** What the account already holds, in reading order. */
    open suspend fun list(): List<SavedPrompt>? = runCatching {
        val response = api.profiles.listPrompts()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            // Already ordered by the server; the sort is defensive and free at three rows.
            body.sortedBy { it.position }.map { SavedPrompt(it.topicId, it.answer) }
        } else {
            null
        }
    }.getOrElse { offline?.list() }

    /** Creates or overwrites the answer for [topicId]. */
    open suspend fun save(topicId: String, answer: String): SavePromptResult = runCatching {
        val response = api.profiles.upsertPrompt(topicId, UpsertPromptDto(answer = answer))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            SavePromptResult.Saved(SavedPrompt(body.topicId, body.answer))
        } else {
            SavePromptResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse {
        // Nothing answered. A server that replies -- with anything, including 500 -- never reaches
        // here, so this cannot hide a backend bug.
        offline?.save(topicId, answer) ?: SavePromptResult.Failed(null)
    }

    /** Removes the prompt for [topicId]. 204 on success, and deleting nothing is success. */
    open suspend fun remove(topicId: String): RemovePromptResult = runCatching {
        val response = api.profiles.deletePrompt(topicId)
        if (response.isSuccessful) {
            RemovePromptResult.Removed
        } else {
            RemovePromptResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { offline?.remove(topicId) ?: RemovePromptResult.Failed(null) }

    private fun errorOf(code: Int, body: String?): ApiError? =
        body?.let { ApiError.parse(code, it) }
}

/**
 * Walking the prompts step with no backend at all.
 *
 * The fourth of these, after `DevOfflineAuth`, `DevOfflineBasics` and `DevOfflinePhotos`. The three
 * rules are identical: DEBUG only, only when nothing answered, and the screen says so.
 *
 * This one always succeeds, unlike the photos stand-in. There is no failure state on the prompts
 * screen to make reachable -- Save either writes a prompt or nudges about an empty field, and the
 * nudge is reached by pressing Save on an empty field, which needs no help.
 */
object DevOfflinePrompts {
    private val stored = mutableListOf<SavedPrompt>()

    fun list(): List<SavedPrompt> = synchronized(stored) { stored.toList() }

    fun save(topicId: String, answer: String): SavePromptResult = synchronized(stored) {
        val prompt = SavedPrompt(topicId, answer)
        val existing = stored.indexOfFirst { it.topicId == topicId }
        // Overwrite in place or append at the BOTTOM, which is what the server does and what the
        // screen promises: "the reading order stays chronological".
        if (existing >= 0) stored[existing] = prompt else stored += prompt
        SavePromptResult.Saved(prompt)
    }

    fun remove(topicId: String): RemovePromptResult = synchronized(stored) {
        stored.removeAll { it.topicId == topicId }
        RemovePromptResult.Removed
    }

    /** Forgets everything. For tests, so one case cannot leak into the next. */
    fun reset() = synchronized(stored) { stored.clear() }
}
