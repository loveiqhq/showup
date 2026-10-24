/*
 * Calls.kt
 * ShowUp · what a repository call returns, and the two wrappers that produce it
 *
 * MOVED HERE FROM `CheckInRepository.kt` with Date search 01 (SHOWUP-184), the second flow to need
 * them. The line between "no response" and "a response that is not a success" decides what a user
 * is told -- "check your connection" or "couldn't save" -- and a second copy of that line in the
 * Search repository could draw it somewhere else.
 */
package com.showup.api

import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Why a call did not land: no response (§32 `network`), or one that is not a success (`server`). */
enum class CallFailure { Network, Server }

/**
 * A read, or a write a flow depends on: its value, or why it failed. The caller reports which of
 * the two it was, so a 500 is never logged as the user being offline.
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Failed(val why: CallFailure) : Outcome<Nothing>
}

/** [block] run as a call: its value, or the exception -- except cancellation, which is rethrown. */
inline fun <T> callCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * [block]'s value as an [Outcome]. Null from it is a response that was not a success; an
 * [IOException] is no response at all. Cancellation is rethrown: a screen whose scope ended has not
 * had a call fail, and nothing may report it as one.
 */
inline fun <T : Any> outcome(block: () -> T?): Outcome<T> = try {
    block()?.let { Outcome.Ok(it) } ?: Outcome.Failed(CallFailure.Server)
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Outcome.Failed(CallFailure.Network)
} catch (e: Exception) {
    Outcome.Failed(CallFailure.Server)
}
