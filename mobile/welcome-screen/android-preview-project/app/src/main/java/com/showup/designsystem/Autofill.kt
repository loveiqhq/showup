package com.showup.designsystem

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * What a field holds, so the system keyboard can offer to fill it.
 *
 * Deliberately OUR enum and not Compose's `AutofillType`, which has some fifty cases and is
 * experimental. Two reasons, and the second is the load-bearing one:
 *
 *  * these are the only two fillable things in the app, and a list of two says so;
 *  * `AutofillType` requires an opt-in at every site that names it. Taking it as a parameter would
 *    put `@OptIn(ExperimentalComposeUiApi::class)` on two screen files and on [InputField], which
 *    is how an experimental API stops being contained.
 *
 * The names mirror iOS's `textContentType` values rather than Compose's, because the pair of them
 * is the thing being kept in step: `.telephoneNumber` and `.oneTimeCode`.
 */
enum class FieldContent {
    /** iOS: `.telephoneNumber`. */
    PhoneNumber,

    /** iOS: `.oneTimeCode`. The SMS verification code. */
    SmsCode,
}

/**
 * Declares what a field holds, so the system keyboard can offer to fill it.
 *
 * WHY THIS EXISTS AS A HELPER RATHER THAN A MODIFIER AT EACH CALL SITE
 *
 * iOS has declared its hints since the screens were written -- `.telephoneNumber` on the number
 * field and `.oneTimeCode` on the code entry, each one line inside the control. Android declared
 * nothing on either, so the number never came from the keychain and an arriving SMS code never
 * appeared above the keyboard. That is a behaviour difference between the platforms that no
 * screenshot shows and no layout test measures, which is the same class of invisible defect as the
 * 23dp tap target this design system was consolidated to prevent.
 *
 * WHY IT IS THIS VERBOSE
 *
 * Compose 1.8 declares the same thing in one line -- `semantics { contentType = ... }`. This
 * project is on the BOM that resolves Compose UI **1.7.4**, where the only autofill API is the
 * experimental one: a node registered in a tree, holding its own window bounds, told by hand when
 * focus arrives and leaves. Writing that twice, in two screens, is how it gets subtly wrong in one
 * of them.
 *
 * **This file is the upgrade seam.** Every experimental import in the app is in it. When the BOM
 * moves to 1.8 or later this collapses into a `contentType` semantics property, [FieldContent] maps
 * to `ContentType` instead, and neither call site changes.
 *
 * NOTHING HERE AFFECTS LAYOUT
 *
 * `onGloballyPositioned` and `onFocusChanged` both observe; neither measures, places, nor draws. The
 * fields render exactly as they did before, which is required -- these screens are in PO
 * Acceptance.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofill(
    content: FieldContent,
    onFill: (String) -> Unit,
): Modifier = composed {
    val autofill = LocalAutofill.current

    val types = remember(content) {
        listOf(
            when (content) {
                FieldContent.PhoneNumber -> AutofillType.PhoneNumber
                FieldContent.SmsCode -> AutofillType.SmsOtpCode
            }
        )
    }

    // The node is created once and keeps its identity, because the tree is keyed by it -- but the
    // lambda it calls has to be the CURRENT one. Capturing `onFill` directly inside `remember`
    // would freeze the first composition's closure and fill through a callback that has since been
    // replaced, which fails silently and only when a person actually uses autofill.
    val latestOnFill = rememberUpdatedState(onFill)
    val node = remember(types) {
        AutofillNode(autofillTypes = types, onFill = { latestOnFill.value(it) })
    }
    LocalAutofillTree.current += node

    this
        // The framework needs to know WHERE the fillable thing is on screen; the node carries its
        // own bounds and nothing else reports them.
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { state ->
            // Null in unit tests and in any host with no autofill service, so this is a real branch
            // rather than defensive noise.
            val service = autofill ?: return@onFocusChanged
            if (state.isFocused) service.requestAutofillForNode(node)
            else service.cancelAutofillForNode(node)
        }
}
