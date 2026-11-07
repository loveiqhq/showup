/*
 * ChatCopy.kt
 * ShowUp · every string the coordinate chat draws (SHOWUP-194)
 *
 * From the ticket's "Copy — final strings", verbatim. The chips' labels and drafts are the
 * vocabulary's ([ChatReason]), not repeated here. `%s` / `%d` are filled by [ChatRules] or the screen.
 */
package com.showup.chat

object ChatCopy {
    /** The header's countdown: `in {h}h {m}m`, `in 0h 45m` under an hour. */
    const val COUNTDOWN = "in %dh %dm"
}
