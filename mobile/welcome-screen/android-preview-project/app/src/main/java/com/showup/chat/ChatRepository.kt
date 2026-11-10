/*
 * ChatRepository.kt
 * ShowUp · what the coordinate chat asks the backend (SHOWUP-194)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body. The
 * dev-only routes (the test match sending, ending) stay with the Date screen's on [DateStore].
 */
package com.showup.chat

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.callCatching
import com.showup.api.generated.model.ChatMessageDto
import com.showup.api.generated.model.SendChatMessageDto
import com.showup.api.outcome
import com.showup.date.DateStore
import com.showup.date.EndedInChat
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import com.showup.api.generated.model.ChatReason as WireReason

/** One message in the thread, in the app's own types. */
data class ChatMessage(
    val id: String,
    /** The chip it was sent with; null for a message typed with none, and for a name this build does not know. */
    val reason: ChatReason?,
    /** The text. An old message sent with a reason alone (SHOWUP-115) reads as the reason's label. */
    val body: String,
    val fromYou: Boolean,
    val sentAt: Instant,
    /** Mine: whether the other person has read the chat since. Theirs: null. */
    val read: Boolean?,
    /** Mine: the id this phone gave it, the same on every resend (Date 02 J). Theirs, and before then: null. */
    val clientId: String? = null,
    /** Mine, and not sent: its send failed and it waits for the person to tap its line (Date 02 J). */
    val failed: Boolean = false,
)

/** What `End date` came back with. */
sealed interface EndResult {
    data class Ended(val outcome: EndedInChat) : EndResult

    /** 409: the date had already ended -- the other person did it, or a second tap. The next date read says how. */
    data object AlreadyEnded : EndResult

    data class Failed(val why: CallFailure) : EndResult
}

/** The chat's calls, behind an interface so the model is tested with no network. */
interface ChatStore {
    /** The thread, oldest first. */
    suspend fun thread(dateId: String): Outcome<List<ChatMessage>>

    /**
     * One message: the trimmed text, with the selected chip's reason or none. [clientId] is the same on
     * every resend, so a message the server already has is answered with that copy, never added twice.
     */
    suspend fun send(dateId: String, reason: ChatReason?, body: String, clientId: String): Outcome<ChatMessage>

    /** The viewer has read the thread up to now. A stamp: a failed one is not reported, the next covers it. */
    suspend fun markRead(dateId: String)

    /** `Open the chat` on the tutorial: it does not show again for this date. A stamp, as [markRead]. */
    suspend fun markTutorialSeen(dateId: String)

    /** Ends the date for both, from the chat. */
    suspend fun endInChat(dateId: String): EndResult

    /** The viewer's outcome screen, G or H, has shown. A stamp, as [markRead]. */
    suspend fun markEndSeen(dateId: String)
}

class ApiChatStore(private val api: ShowUpApi) : ChatStore {

    override suspend fun thread(dateId: String): Outcome<List<ChatMessage>> = outcome {
        val response = api.dates.listDateChatMessages(dateId)
        response.body()?.takeIf { response.isSuccessful }?.map { it.toMessage() }
    }

    override suspend fun send(dateId: String, reason: ChatReason?, body: String, clientId: String): Outcome<ChatMessage> = outcome {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val wire = reason?.let { chip -> WireReason.entries.first { it.value == chip.wire } }
        val dto = SendChatMessageDto(body = body, clientId = UUID.fromString(clientId), reason = wire)
        val response = api.dates.sendDateChatMessage(dateId, dto)
        response.body()?.takeIf { response.isSuccessful }?.toMessage()
    }

    override suspend fun markRead(dateId: String) {
        callCatching { api.dates.markDateChatRead(dateId) }
    }

    override suspend fun markTutorialSeen(dateId: String) {
        callCatching { api.dates.markChatTutorialSeen(dateId) }
    }

    /**
     * [outcome]'s line between "no response" and "a response that is not a success", plus the one
     * answer that is neither: 409, the date already over, which the caller resolves with a date read.
     */
    override suspend fun endInChat(dateId: String): EndResult = try {
        val response = api.dates.endDateInChat(dateId)
        val body = response.body()
        when {
            response.isSuccessful && body != null -> EndResult.Ended(EndedInChat(body.dateId, body.name, body.endedByYou))
            response.code() == 409 -> EndResult.AlreadyEnded
            else -> EndResult.Failed(CallFailure.Server)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        EndResult.Failed(CallFailure.Network)
    } catch (e: Exception) {
        EndResult.Failed(CallFailure.Server)
    }

    override suspend fun markEndSeen(dateId: String) {
        callCatching { api.dates.markDateEndSeen(dateId) }
    }

    private fun ChatMessageDto.toMessage(): ChatMessage {
        val chip = ChatReason.fromWire(reason?.value)
        return ChatMessage(id, chip, body ?: chip?.label.orEmpty(), fromYou, createdAt.toInstant(), read, clientId?.toString())
    }
}
