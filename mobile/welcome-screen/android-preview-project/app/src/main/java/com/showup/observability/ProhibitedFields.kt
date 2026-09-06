package com.showup.observability

/**
 * Field names that must never leave the device in anything a human or an outside service can read.
 *
 * A deliberate mirror of `src/common/privacy/prohibited-fields.ts` on the backend, and of
 * `ProhibitedFields.swift` on iOS. All three lists are compared by
 * `audit/check-privacy-parity.py`, because a name added to one and not the others is a silent hole
 * -- the kind nobody notices until a phone number turns up in a crash report.
 *
 * Names are matched by NORMALISED form: lower-cased with punctuation removed, so `email`, `Email`
 * and `e_mail` are all caught, while safe look-alikes such as `age_band`, `city_geohash` and
 * `token_type` survive.
 */
internal object ProhibitedFields {

    private val PROHIBITED = setOf(
        // Unmasked PII
        "email",
        "phone",
        "phonenumber",
        "mobile",
        "lat",
        "latitude",
        "lng",
        "lon",
        "longitude",
        "gps",
        "coordinates",
        "coords",
        "address",
        "streetaddress",
        // Private chat content
        "message",
        "messagetext",
        "messagebody",
        "text",
        "content",
        // Demographics (bucketed into age_band instead)
        "age",
        "dob",
        "birthdate",
        "dateofbirth",
        // Secrets. Note `code`: the one-time sign-in and email-verification payloads name the
        // secret `code`, so the bare name is withheld. An API error code is a different thing and
        // must be named `error_code` so it is not caught by this rule.
        "code",
        "otp",
        "otpcode",
        "smscode",
        "verificationcode",
        "pin",
        "password",
        "passcode",
        "pwd",
        "token",
        "accesstoken",
        "refreshtoken",
        "pushtoken",
        "authtoken",
        "apitoken",
        "sessionkey",
        "secret",
        "apikey",
        "card",
        "cardnumber",
        "creditcard",
        "cvv",
        "cvc",
        "pan",
    )

    /** Lower-case and strip punctuation, so naming style cannot slip a prohibited field through. */
    fun normalise(key: String): String =
        key.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Whether a field with this name must be withheld. */
    fun isProhibited(key: String): Boolean = normalise(key) in PROHIBITED

    /** The whole set, for the parity test. */
    fun all(): Set<String> = PROHIBITED
}
