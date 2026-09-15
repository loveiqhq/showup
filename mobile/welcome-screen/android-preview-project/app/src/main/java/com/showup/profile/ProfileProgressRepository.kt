/*
 * ProfileProgressRepository.kt
 * ShowUp · the one read that answers "where did this account get to" (flow rule 4a)
 *
 * One call, one job. It is separate from `BasicsRepository` because it spans both profile groups
 * and belongs to neither -- the basics repository would have to know about photos and prompts to
 * hold it, and it has no other reason to.
 *
 * Returns null when nothing answered. The caller reads that as "do not route on a guess": a cold
 * launch with no network shows the flow's own entry point rather than a step chosen from missing
 * facts, which is the difference between resuming and reshuffling.
 */
package com.showup.profile

import com.showup.api.ShowUpApi

open class ProfileProgressRepository(private val api: ShowUpApi) {

    open suspend fun fetch(): ProfileProgress? = runCatching {
        val response = api.profiles.getProfileProgress()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ProfileProgress(
                displayName = body.displayName,
                email = body.email,
                emailVerified = body.emailVerified,
                hasDateOfBirth = body.hasDateOfBirth,
                photoCount = body.photoCount,
                promptCount = body.promptCount,
            )
        } else {
            // A 401 lands here too, which is correct: an account that cannot be read has no
            // progress to resume onto, and the flow starts at the beginning.
            null
        }
    }.getOrNull()
}
