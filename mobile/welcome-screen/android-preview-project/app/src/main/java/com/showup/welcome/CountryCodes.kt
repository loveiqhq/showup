/*
 * CountryCodes.kt
 * ShowUp · every country, and the rules for each (SHOWUP-143)
 *
 * NOTHING HERE COSTS MONEY.
 *
 * Country calling codes are public ITU assignments (recommendation E.164) — not licensed, not
 * metered, not behind anyone's API. The rules come from Google's **libphonenumber**, which is
 * Apache 2.0: "perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable", with no
 * clause anywhere about user numbers or revenue. It is a library that ships inside the app, so
 * there is no server to meter and nothing to bill. The paid service in this flow is Twilio, and it
 * is paid for *delivering the SMS*.
 *
 * WHY IT REPLACED A HAND-WRITTEN TABLE
 *
 * This file used to carry 35 countries with hand-written length rules. That was wrong twice over,
 * and both were shipped bugs: the lengths described every kind of number rather than mobiles (so a
 * six-digit German landline passed as a number we could text), and 250 countries could never have
 * been maintained this way — writing those rules by hand means guessing, and a wrong guess REJECTS
 * A REAL PERSON'S REAL NUMBER, which is the worst failure this screen has.
 *
 * libphonenumber knows, per country: the valid lengths, whether a number is a mobile, the national
 * trunk prefix, and how to group the digits as they are typed. The country list itself is derived
 * from it too, so there is no list here to fall out of date.
 */
package com.showup.welcome

import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.PhoneNumberUtil.ValidationResult
import java.util.Locale

private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

/**
 * One country in the picker.
 *
 * Everything is derived: [name] from the device's own locale data, so it appears in the user's
 * language; [dial] and every rule from libphonenumber. The flag is looked up by [iso] against the
 * bundled artwork — see `assets/flags`.
 */
data class Country(
    val iso: String,
    val name: String,
    val dial: String,
) {
    /**
     * An example mobile number for this country, shown in the empty field.
     *
     * Fetched on demand rather than stored: building 250 of these up front costs real time at
     * startup, and only the selected country's example is ever shown.
     */
    val sample: String
        get() = exampleMobile(iso)
}

/**
 * Every region libphonenumber knows, sorted by name in the user's own language.
 *
 * Built once, lazily. Sorting is locale-aware — an alphabetical sort of German names is not the
 * same order as English ones, and a list sorted by the wrong alphabet is hard to scan.
 */
val COUNTRIES: List<Country> by lazy {
    val collator = java.text.Collator.getInstance()
    phoneUtil.supportedRegions
        .map { iso ->
            Country(
                iso = iso,
                name = Locale("", iso).getDisplayCountry(Locale.getDefault()).ifBlank { iso },
                dial = "+" + phoneUtil.getCountryCodeForRegion(iso),
            )
        }
        .sortedWith(compareBy(collator) { it.name })
}

/** Germany is the launch market, so it is the fallback when the device locale says nothing useful. */
val DEFAULT_COUNTRY: Country by lazy { countryForRegion("DE") }

/**
 * The country for a device region, or Germany.
 *
 * SHOWUP-143 asks the pill to default from device locale. The region the platform reports is the
 * same key libphonenumber uses, so this is a lookup rather than a guess.
 */
fun countryForRegion(region: String?): Country =
    COUNTRIES.firstOrNull { it.iso.equals(region, ignoreCase = true) }
        ?: COUNTRIES.first { it.iso == "DE" }

/**
 * How many digits past a country's longest valid number the field will accept.
 *
 * Not zero, and that is the point. The field used to cap at exactly the maximum, which silently ate
 * the extra keystrokes — so a too-long number could not be typed, [PhoneError.TooLong] could never
 * fire, and the user watched their own digits disappear with no explanation.
 */
const val OVERTYPE_ALLOWANCE = 4

/**
 * The most digits a national number can have anywhere, from E.164: fifteen including the country
 * code. Used only to bound the input; libphonenumber does the real rejecting.
 */
const val E164_MAX_DIGITS = 15

/**
 * Why a number was rejected.
 *
 * Seven values, ONE message. The distinction is not for the user -- it is the `reason` property
 * SHOWUP-143 asks for under Tracking: "Number submitted · validation failed, with a `reason`
 * property (too short / not a mobile / unsupported country)". Knowing which rule people trip is
 * how the copy and the country list get better; telling each person which rule they tripped is a
 * different decision, and not the one that was made.
 */
enum class PhoneError { Empty, TooShort, TooLong, InvalidLength, Unrecognised, NotANumber, NotMobile }

/**
 * The one error string, from the ticket and the reference render alike.
 *
 * SHOWUP-143: "the helper line is replaced with the example-number message" -- singular -- and
 * "Copy matches the strings exactly". Both `welcome/tickets/03-phone-verification.md` and
 * `screen-phone-reference.jsx` give it verbatim:
 *
 *     Please enter a valid number e.g. 176 123 45 678
 *
 * This used to be seven different sentences, each naming the country and the rule that was broken.
 * They read well and they were wrong twice over: the wording came from the ticket's ANALYTICS
 * categories rather than from its copy, and being three times longer they were clipped on every
 * phone 360dp wide or narrower -- so the example, the one genuinely useful part, was the half that
 * got cut off. Shorter is not a compromise here; it is the specification, and it fits.
 *
 * The example itself stays per-country rather than the hard-coded German one in the reference.
 * The ticket lists that as an open concern -- "`e.g. 176 123 45 678` is a German example hard-coded
 * into the error string" -- and libphonenumber already knows the right example for all 245.
 */
fun PhoneError.message(country: Country): String =
    "Please enter a valid number e.g. ${country.sample}"

/**
 * Validation, run on submit rather than per keystroke — SHOWUP-143 requires that, and it is the
 * kinder behaviour: nobody wants to be told their number is wrong while they are still typing.
 *
 * The national trunk prefix is no longer an error. Parsing with a region lets libphonenumber strip
 * a leading 0 the way the country's own dialling rules say, so a German typing 0176… is simply
 * correct now rather than being told off for it.
 */
fun validate(raw: String, country: Country): PhoneError? {
    val digits = raw.filter { it.isDigit() }
    if (raw.any { it.isLetter() }) return PhoneError.NotANumber
    if (digits.isEmpty()) return PhoneError.Empty

    val parsed = try {
        phoneUtil.parse(digits, country.iso)
    } catch (e: NumberParseException) {
        return when (e.errorType) {
            NumberParseException.ErrorType.TOO_SHORT_NSN,
            NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD -> PhoneError.TooShort
            NumberParseException.ErrorType.TOO_LONG -> PhoneError.TooLong
            else -> PhoneError.NotANumber
        }
    }

    // TYPE FIRST, then length. A German landline is eight digits and a German mobile is ten or
    // eleven, so a length check reaches it first and reports "too short" — true, but useless to
    // someone who has correctly typed the landline they own. Asking "is this a real number here,
    // and what kind?" before "is it the right length for a mobile?" produces the message that
    // actually helps: we need a mobile.
    if (phoneUtil.isValidNumberForRegion(parsed, country.iso)) {
        return when (phoneUtil.getNumberType(parsed)) {
            // FIXED_LINE_OR_MOBILE is allowed: in several countries the ranges overlap and
            // libphonenumber genuinely cannot tell them apart. Refusing there would reject people
            // holding perfectly good mobiles.
            PhoneNumberType.MOBILE,
            PhoneNumberType.FIXED_LINE_OR_MOBILE -> null
            else -> PhoneError.NotMobile
        }
    }

    // Not a real number for this country. Now say why, measured against a mobile — that is what
    // the user is being asked for, so it is the only comparison that means anything to them.
    return when (phoneUtil.isPossibleNumberForTypeWithReason(parsed, PhoneNumberType.MOBILE)) {
        ValidationResult.TOO_SHORT -> PhoneError.TooShort
        ValidationResult.TOO_LONG -> PhoneError.TooLong
        ValidationResult.INVALID_LENGTH -> PhoneError.InvalidLength
        ValidationResult.INVALID_COUNTRY_CODE -> PhoneError.NotANumber
        // The right length for a mobile, but not a number this country issues — almost always a
        // prefix that does not exist.
        else -> PhoneError.Unrecognised
    }
}

/**
 * Groups the digits the way that country writes them, as they are typed.
 *
 * Fed as an INTERNATIONAL number and then stripped back, rather than asked for a national one.
 * libphonenumber's national format includes the country's trunk prefix — Germany's example is
 * `01512 3456789`, with the leading zero — and the pill already shows +49, so a national format
 * here would put both on screen at once, which is a number that dials nowhere. Priming the
 * formatter with the dial code and removing it afterwards gets the grouping without the prefix.
 */
fun formatNational(digits: String, country: Country): String {
    if (digits.isEmpty()) return ""
    val formatter: AsYouTypeFormatter = phoneUtil.getAsYouTypeFormatter(country.iso)
    var out = ""
    formatter.inputDigit('+')
    country.dial.drop(1).forEach { formatter.inputDigit(it) }
    digits.forEach { out = formatter.inputDigit(it) }
    return out.removePrefix(country.dial).trim()
}

/**
 * An example mobile number for a region, grouped and without the trunk prefix — the same shape the
 * user is being asked to type.
 */
private fun exampleMobile(iso: String): String = try {
    val dial = "+" + phoneUtil.getCountryCodeForRegion(iso)
    phoneUtil.getExampleNumberForType(iso, PhoneNumberType.MOBILE)
        ?.let { phoneUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL) }
        ?.removePrefix(dial)
        ?.trim()
        .orEmpty()
} catch (e: Exception) {
    ""
}
