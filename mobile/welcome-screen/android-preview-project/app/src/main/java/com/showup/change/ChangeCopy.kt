/*
 * ChangeCopy.kt
 * ShowUp · every string Date 05 draws, the ticket's "Copy -- final strings" (SHOWUP-196)
 *
 * One home, so `audit/verify-change.py` quotes the ticket against one file per platform. `%s` are the
 * ticket's `{name}`, `{time}`, `{place}`, `{deadline}`, `{latest}`, `{slot}`, `{venue}` and `{q}`.
 */
package com.showup.change

object ChangeCopy {
    // A · time picker
    const val TIME_TITLE = "Suggest a new time"
    const val TIME_CURRENT = "Your current meet time: "
    const val TIME_CHOOSE = "Choose a time within %s's availability. Latest possible meet time: %s."
    const val PICK_TIME = "Pick a time"

    // B · place picker
    const val PLACE_TITLE = "Suggest a new place"
    const val PLACE_CURRENT = "Your current meet place: "
    const val PLACE_CHOOSE = "Choose a public place: café, bar or restaurant."
    const val PICK_PLACE = "Pick a place"
    const val SEARCH_PLACEHOLDER = "Search cafés, bars, restaurants"
    const val NEARBY = "Nearby public places"
    const val RESULTS = "Results"

    // Both pickers
    const val AGREE_BY = "%s has to agree by %s, so you both still arrive on time. If not, %s stays binding."
    const val SUGGEST = "Suggest %s"
    const val CANCEL = "Cancel"

    /** The binding note (A, B, C, D): the bold sentence, then the rest. `unless you agree` when the match suggested it. */
    const val BINDING_BOLD = "%s at %s stays binding unless %s agrees by %s."
    const val BINDING_BOLD_YOU = "%s at %s stays binding unless you agree by %s."
    const val BINDING_REST = " Not showing up there counts as a no-show."

    // K · M · N
    const val SEND_FAILED = "Couldn't send your suggestion. Check your connection and try again."
    const val PLACES_FAILED = "Couldn't load places. Check your connection and try again."
    const val TRY_AGAIN = "Try again"
    const val NO_PLACES = "No cafés, bars or restaurants found for “%s”."
    const val TRY_ANOTHER = "Try another name."

    /** Google's attribution for Places results shown without a map (R2): the text form, not localised. */
    const val GOOGLE_MAPS = "Google Maps"

    /** The list area's description while it loads: spoken, never drawn (R17). */
    const val LOADING_PLACES = "Loading places"
}
