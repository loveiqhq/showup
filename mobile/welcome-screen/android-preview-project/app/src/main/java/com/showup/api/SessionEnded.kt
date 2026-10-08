package com.showup.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one piece of news that has to outlive the screen that learned it: the server has ended the
 * session, and the user has to log in again.
 *
 * WHY A PROCESS-WIDE FLAG AND NOT A CALLBACK INTO THE SCREEN
 *
 * The news arrives on OkHttp's dispatcher thread, in the middle of whatever request happened to
 * need a refresh -- a save on the height step, the resume check at launch, a photo upload. None of
 * those is the right place to decide what happens next, and the Activity that would act on it may
 * be mid-rotation when it lands. A flag is safe to set from any thread, and whichever Activity is
 * alive picks it up as soon as it is started (`MainActivity`).
 *
 * A FLAG, NOT A COUNT. Ten requests failing together refuse the same session ten times over in
 * principle (in practice single-flight makes it one), and the user should be told once.
 */
object SessionEnded {
    private val pending = MutableStateFlow(false)

    /** True from the moment the server refuses the session until an Activity [consume]s it. */
    val isPending: StateFlow<Boolean> = pending.asStateFlow()

    /** Called by the auth layer, on any thread, after the tokens are cleared. */
    fun signal() {
        pending.value = true
    }

    /**
     * Takes the news, returning whether there was any. Atomic, so two collectors racing for it
     * cannot both restart the app.
     */
    fun consume(): Boolean = pending.compareAndSet(expect = true, update = false)
}
