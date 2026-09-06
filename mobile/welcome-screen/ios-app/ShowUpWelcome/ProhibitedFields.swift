import Foundation

/// Field names that must never leave the device in anything a human or an outside service can read.
///
/// A deliberate mirror of `src/common/privacy/prohibited-fields.ts` on the backend, and of
/// `ProhibitedFields.kt` on Android. All three lists are compared by
/// `audit/check-privacy-parity.py`, because a name added to one and not the others is a silent hole
/// — the kind nobody notices until a phone number turns up in a crash report.
///
/// Names are matched by NORMALISED form: lower-cased with punctuation removed, so `email`, `Email`
/// and `e_mail` are all caught, while safe look-alikes such as `age_band`, `city_geohash` and
/// `token_type` survive.
enum ProhibitedFields {

    static let all: Set<String> = [
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
    ]

    /// Lower-case and strip everything that is not an ASCII letter or digit, so naming style
    /// cannot slip a prohibited field through.
    ///
    /// ASCII deliberately, and not `isLetter`/`isNumber`. Swift's `isLetter` is true for `ü` and
    /// `é`; the backend strips with `[^a-z0-9]` and Android with an `'a'..'z'` range, so both drop
    /// those characters. Using `isLetter` here would make the three normalisers disagree on any
    /// non-ASCII field name — a divergence that would show up as one platform redacting a field the
    /// others let through, which is the exact failure the shared list exists to prevent.
    static func normalise(_ key: String) -> String {
        key.lowercased().filter { character in
            guard let ascii = character.asciiValue else { return false }
            return (ascii >= 97 && ascii <= 122) || (ascii >= 48 && ascii <= 57)
        }
    }

    /// Whether a field with this name must be withheld.
    static func isProhibited(_ key: String) -> Bool {
        all.contains(normalise(key))
    }
}
