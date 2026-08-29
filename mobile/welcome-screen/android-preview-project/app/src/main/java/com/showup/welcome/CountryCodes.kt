/*
 * CountryCodes.kt
 * ShowUp · the country list behind the dial-code pill (SHOWUP-143)
 *
 * NOTHING HERE COSTS MONEY.
 *
 * Country calling codes are public assignments published by the ITU (recommendation E.164). They
 * are not licensed, not metered, and not behind anyone's API — the table below is just data, and it
 * works offline. The paid service in this flow is Twilio, and it is paid for *delivering the SMS*,
 * not for knowing that Germany is +49.
 *
 * The one thing worth buying later is deeper validation — "is this a real, reachable mobile number
 * on a live carrier". Even that has a free answer first: Google's libphonenumber (Apache 2.0, free,
 * offline) knows every country's real number formats and is what production should use. Twilio's
 * Lookup API is charged per query and only earns its keep for carrier and portability checks.
 *
 * Until libphonenumber is added, [validate] applies the plain length and prefix rules below. They
 * are deliberately simple and explainable rather than clever: rejecting a number a real user holds
 * is a much worse failure than accepting one that later bounces.
 */
package com.showup.welcome

/** How a flag is drawn. No emoji anywhere, flags included — CLAUDE.md states it as a rule. */
sealed interface FlagArt {
    /** Equal or weighted stripes. [horizontal] false means vertical stripes. */
    data class Bands(val horizontal: Boolean, val stripes: List<Pair<Long, Int>>) : FlagArt

    /** The Nordic cross — offset left, not centred. [inner] draws a second, thinner cross. */
    data class Cross(val bg: Long, val arm: Long, val inner: Long? = null, val centred: Boolean = false) : FlagArt

    /**
     * An ordered list of shapes on a 0..1 unit square, painted back to front.
     *
     * Bands and Cross cover the flags that are just stripes; this covers the rest — a triangle from
     * the hoist, a canton, a crescent, a leaf. Everything is a fraction of the flag rather than a
     * pixel, so one description renders correctly at any size and on either platform.
     */
    data class Layers(val shapes: List<FlagShape>) : FlagArt

    /**
     * The ISO code on a neutral chip, for the few flags that cannot be drawn honestly.
     *
     * Reserved for flags whose identity depends on a coat of arms — Slovakia and Slovenia are both
     * white-blue-red and are told apart ONLY by their arms, so drawing the stripes alone would
     * render two different countries identically. Australia needs a Union Jack plus the Southern
     * Cross at a size where neither survives. A wrong flag is worse than an honest code: it reads
     * as a bug, and for some countries it is a genuine offence.
     */
    data object Code : FlagArt
}

/** One shape in a [FlagArt.Layers] flag. All coordinates are fractions of the flag, 0..1. */
sealed interface FlagShape {
    data class Fill(val c: Long) : FlagShape

    /** Equal stripes, painted in order. [horizontal] false means vertical. */
    data class Stripes(val horizontal: Boolean, val colors: List<Long>) : FlagShape

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float, val c: Long) : FlagShape

    /** A filled polygon — a hoist triangle, a maple leaf, a diagonal of a Union Jack. */
    data class Poly(val pts: List<Pair<Float, Float>>, val c: Long) : FlagShape

    data class Disc(val cx: Float, val cy: Float, val r: Float, val c: Long) : FlagShape

    /** [w] is the stroke width as a fraction of the flag height. */
    data class Ring(val cx: Float, val cy: Float, val r: Float, val w: Float, val c: Long) : FlagShape

    /** A five-pointed star, point upward. [r] is the outer radius. */
    data class Star(val cx: Float, val cy: Float, val r: Float, val c: Long) : FlagShape

    /** [n] x [n] alternating squares — Croatia's shield, which is what tells it from the Dutch. */
    data class Checks(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val n: Int, val a: Long, val b: Long,
    ) : FlagShape
}

/**
 * @param nsnMin/[nsnMax] length of a MOBILE national significant number — the digits after the dial
 *        code, with any national trunk "0" already stripped.
 *
 * Mobile, not "any number in that country". This screen exists to send an SMS, so a number that
 * cannot receive one is not valid input however real it is. The distinction is not academic: German
 * landlines start at six digits, and while this table said 6 a number like 49 6 12345 was accepted,
 * verified against nothing, and would have sat waiting for a code that could never arrive.
 */
data class Country(
    val iso: String,
    val name: String,
    val dial: String,
    val nsnMin: Int,
    val nsnMax: Int,
    val flag: FlagArt,
    /** Placeholder shown in the empty field, in that country's own habits. */
    val sample: String,
)

private fun bandsH(vararg c: Long) = FlagArt.Bands(true, c.map { it to 1 })
private fun bandsV(vararg c: Long) = FlagArt.Bands(false, c.map { it to 1 })

/**
 * Sorted by name. Weighted toward the launch market and its neighbours; every entry is real data
 * rather than a placeholder, so adding a country is one line.
 */
val COUNTRIES: List<Country> = listOf(
    Country("AT", "Austria", "+43", 10, 13, bandsH(0xFFED2939, 0xFFFFFFFF, 0xFFED2939), "664 1234567"),
    Country("AU", "Australia", "+61", 9, 9, FlagArt.Code, "412 345 678"),
    Country("BA", "Bosnia and Herzegovina", "+387", 8, 8, FlagArt.Code, "61 123 456"),
    Country("BE", "Belgium", "+32", 9, 9, bandsV(0xFF000000, 0xFFFAE042, 0xFFED2939), "470 12 34 56"),
    Country("BG", "Bulgaria", "+359", 8, 9, bandsH(0xFFFFFFFF, 0xFF00966E, 0xFFD62612), "48 123 456"),
    Country("CA", "Canada", "+1", 10, 10, FlagArt.Code, "506 234 5678"),
    Country("CH", "Switzerland", "+41", 9, 9, FlagArt.Cross(0xFFDA291C, 0xFFFFFFFF, centred = true), "78 123 45 67"),
    Country("CZ", "Czechia", "+420", 9, 9, FlagArt.Layers(listOf(
        FlagShape.Stripes(true, listOf(0xFFFFFFFF, 0xFFD7141A)),
        FlagShape.Poly(listOf(0f to 0f, 0.5f to 0.5f, 0f to 1f), 0xFF11457E),
    )), "601 123 456"),
    Country("DE", "Germany", "+49", 10, 11, bandsH(0xFF000000, 0xFFDD0000, 0xFFFFCE00), "176 123 45 678"),
    Country("DK", "Denmark", "+45", 8, 8, FlagArt.Cross(0xFFC8102E, 0xFFFFFFFF), "32 12 34 56"),
    Country("EE", "Estonia", "+372", 7, 8, bandsH(0xFF0072CE, 0xFF000000, 0xFFFFFFFF), "5123 4567"),
    Country("ES", "Spain", "+34", 9, 9,
        FlagArt.Bands(true, listOf(0xFFAA151BL to 1, 0xFFF1BF00L to 2, 0xFFAA151BL to 1)), "612 34 56 78"),
    Country("FI", "Finland", "+358", 9, 10, FlagArt.Cross(0xFFFFFFFF, 0xFF003580), "41 2345678"),
    Country("FR", "France", "+33", 9, 9, bandsV(0xFF002395, 0xFFFFFFFF, 0xFFED2939), "6 12 34 56 78"),
    Country("GB", "United Kingdom", "+44", 10, 10, FlagArt.Layers(listOf(
        FlagShape.Fill(0xFF012169),
        FlagShape.Poly(listOf(0f to 0f, 0.16f to 0f, 1f to 1f, 0.84f to 1f), 0xFFFFFFFF),
        FlagShape.Poly(listOf(1f to 0f, 0.84f to 0f, 0f to 1f, 0.16f to 1f), 0xFFFFFFFF),
        FlagShape.Poly(listOf(0f to 0f, 0.09f to 0f, 1f to 1f, 0.91f to 1f), 0xFFC8102E),
        FlagShape.Poly(listOf(1f to 0f, 0.91f to 0f, 0f to 1f, 0.09f to 1f), 0xFFC8102E),
        FlagShape.Box(0f, 0.33f, 1f, 0.34f, 0xFFFFFFFF),
        FlagShape.Box(0.39f, 0f, 0.22f, 1f, 0xFFFFFFFF),
        FlagShape.Box(0f, 0.40f, 1f, 0.20f, 0xFFC8102E),
        FlagShape.Box(0.435f, 0f, 0.13f, 1f, 0xFFC8102E),
    )), "7400 123456"),
    Country("GR", "Greece", "+30", 10, 10, FlagArt.Layers(listOf(
        FlagShape.Stripes(true, listOf(
            0xFF0D5EAF, 0xFFFFFFFF, 0xFF0D5EAF, 0xFFFFFFFF, 0xFF0D5EAF,
            0xFFFFFFFF, 0xFF0D5EAF, 0xFFFFFFFF, 0xFF0D5EAF,
        )),
        // The canton is square: five stripes tall, and as wide as it is tall.
        FlagShape.Box(0f, 0f, 5f / 9f * 14f / 22f, 5f / 9f, 0xFF0D5EAF),
        FlagShape.Box(0f, 5f / 9f * 0.4f, 5f / 9f * 14f / 22f, 5f / 9f * 0.2f, 0xFFFFFFFF),
        FlagShape.Box(5f / 9f * 14f / 22f * 0.4f, 0f, 5f / 9f * 14f / 22f * 0.2f, 5f / 9f, 0xFFFFFFFF),
    )), "691 234 5678"),
    Country("HR", "Croatia", "+385", 8, 9, FlagArt.Layers(listOf(
        FlagShape.Stripes(true, listOf(0xFFFF0000, 0xFFFFFFFF, 0xFF171796)),
        FlagShape.Box(0.39f, 0.22f, 0.22f, 0.56f, 0xFFFFFFFF),
        FlagShape.Checks(0.39f, 0.22f, 0.22f, 0.56f, 4, 0xFFFF0000, 0xFFFFFFFF),
    )), "91 234 5678"),
    Country("HU", "Hungary", "+36", 9, 9, bandsH(0xFFCD2A3E, 0xFFFFFFFF, 0xFF436F4D), "20 123 4567"),
    Country("IE", "Ireland", "+353", 9, 9, bandsV(0xFF169B62, 0xFFFFFFFF, 0xFFFF883E), "85 012 3456"),
    Country("IT", "Italy", "+39", 9, 10, bandsV(0xFF008C45, 0xFFF4F5F0, 0xFFCD212A), "312 345 6789"),
    Country("LT", "Lithuania", "+370", 8, 8, bandsH(0xFFFDB913, 0xFF006A44, 0xFFC1272D), "612 34567"),
    Country("LU", "Luxembourg", "+352", 9, 9, bandsH(0xFFED2939, 0xFFFFFFFF, 0xFF00A1DE), "628 123 456"),
    Country("LV", "Latvia", "+371", 8, 8,
        FlagArt.Bands(true, listOf(0xFF9E3039L to 2, 0xFFFFFFFFL to 1, 0xFF9E3039L to 2)), "21 234 567"),
    Country("NL", "Netherlands", "+31", 9, 9, bandsH(0xFFAE1C28, 0xFFFFFFFF, 0xFF21468B), "6 12345678"),
    Country("NO", "Norway", "+47", 8, 8, FlagArt.Cross(0xFFBA0C2F, 0xFFFFFFFF, inner = 0xFF00205B), "406 12 345"),
    Country("PL", "Poland", "+48", 9, 9, bandsH(0xFFFFFFFF, 0xFFDC143C), "512 345 678"),
    Country("PT", "Portugal", "+351", 9, 9, FlagArt.Layers(listOf(
        FlagShape.Stripes(false, listOf(0xFF006600, 0xFF006600, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000)),
        // The armillary sphere and shield reduce to a ring at this size. Without something on the
        // join it is a plain green-red bicolour, which is not Portugal.
        // The armillary sphere reduces to its ring. A shield drawn inside it at this size reads
        // as a logo rather than a coat of arms, so it is left off.
        FlagShape.Ring(0.40f, 0.5f, 0.28f, 0.10f, 0xFFFFE900),
    )), "912 345 678"),
    Country("RO", "Romania", "+40", 9, 9, bandsV(0xFF002B7F, 0xFFFCD116, 0xFFCE1126), "712 345 678"),
    Country("RS", "Serbia", "+381", 8, 9, FlagArt.Layers(listOf(
        FlagShape.Stripes(true, listOf(0xFFC6363C, 0xFF0C4076, 0xFFFFFFFF)),
    )), "60 1234567"),
    Country("SE", "Sweden", "+46", 9, 9, FlagArt.Cross(0xFF006AA7, 0xFFFECC00), "70 123 45 67"),
    Country("SI", "Slovenia", "+386", 8, 8, FlagArt.Code, "31 234 567"),
    Country("SK", "Slovakia", "+421", 9, 9, FlagArt.Code, "912 123 456"),
    Country("TR", "Türkiye", "+90", 10, 10, FlagArt.Layers(listOf(
        FlagShape.Fill(0xFFE30A17),
        // The crescent is one white disc with a red one overlapping it — the same way it is
        // constructed on the real flag, rather than an arc drawn by eye.
        FlagShape.Disc(0.40f, 0.5f, 0.26f, 0xFFFFFFFF),
        FlagShape.Disc(0.455f, 0.5f, 0.21f, 0xFFE30A17),
        FlagShape.Star(0.63f, 0.5f, 0.13f, 0xFFFFFFFF),
    )), "501 234 56 78"),
    Country("UA", "Ukraine", "+380", 9, 9, bandsH(0xFF0057B7, 0xFFFFDD00), "50 123 4567"),
    Country("US", "United States", "+1", 10, 10, FlagArt.Layers(listOf(
        FlagShape.Stripes(true, listOf(
            0xFFB31942, 0xFFFFFFFF, 0xFFB31942, 0xFFFFFFFF, 0xFFB31942, 0xFFFFFFFF, 0xFFB31942,
            0xFFFFFFFF, 0xFFB31942, 0xFFFFFFFF, 0xFFB31942, 0xFFFFFFFF, 0xFFB31942,
        )),
        FlagShape.Box(0f, 0f, 0.40f, 7f / 13f, 0xFF0A3161),
    )), "201 555 0123"),
)

/** Germany is the launch market, so it is the fallback when the device locale says nothing useful. */
val DEFAULT_COUNTRY: Country = COUNTRIES.first { it.iso == "DE" }

/**
 * The country for a device region, or Germany.
 *
 * SHOWUP-143 asks the pill to default from device locale. The region code the platform reports is
 * exactly the ISO key used above, so this is a lookup rather than a guess.
 */
fun countryForRegion(region: String?): Country =
    COUNTRIES.firstOrNull { it.iso.equals(region, ignoreCase = true) } ?: DEFAULT_COUNTRY

/** Why a number was rejected. Each maps to one message, and each is something the user can act on. */
enum class PhoneError { Empty, TooShort, TooLong, LeadingZero, NotANumber }

/**
 * How many digits past the country's maximum the field will accept before it stops taking input.
 *
 * Not zero, and that is the point. The field used to cap at exactly the maximum, which silently ate
 * every extra keystroke — so a too-long number could not be typed, [PhoneError.TooLong] could never
 * fire, and the user watched their own digits disappear with no explanation. Letting a few through
 * makes the error reachable and lets the message do the explaining.
 */
const val OVERTYPE_ALLOWANCE = 4

/**
 * Validation, run on submit rather than per keystroke — SHOWUP-143 requires that, and it is also
 * the kinder behaviour: nobody wants to be told their number is wrong while they are still typing.
 */
fun validate(raw: String, country: Country): PhoneError? {
    val digits = raw.filter { it.isDigit() }
    return when {
        // Letters first. Stripping them and then reporting "empty" would answer a question the
        // user did not ask -- they typed something, it was just the wrong something.
        //
        // The field filters to digits as they are entered, so today nothing can reach this. It
        // stays as the guard for a value arriving from somewhere that does not filter: a paste,
        // an autofill suggestion, or a caller that has not been written yet.
        raw.any { it.isLetter() } -> PhoneError.NotANumber
        digits.isEmpty() -> PhoneError.Empty
        // Every country in this list uses 0 as a national trunk prefix, and it is dropped when the
        // dial code is supplied separately. Catching it explicitly is worth it: writing 0176... is
        // the single most common way a German user gets this wrong.
        digits.startsWith("0") -> PhoneError.LeadingZero
        digits.length < country.nsnMin -> PhoneError.TooShort
        digits.length > country.nsnMax -> PhoneError.TooLong
        else -> null
    }
}

fun PhoneError.message(country: Country): String = when (this) {
    PhoneError.Empty -> "Enter your phone number to continue."
    PhoneError.NotANumber -> "Numbers only, please. For example ${country.sample}."
    // Names the fix rather than the rule.
    PhoneError.LeadingZero -> "Leave out the first 0 — ${country.dial} already covers it."
    PhoneError.TooShort -> "That looks too short for ${country.name}. For example ${country.sample}."
    PhoneError.TooLong -> "That looks too long for ${country.name}. For example ${country.sample}."
}

/** Digits only, grouped the way that country's sample is grouped, so typing looks familiar. */
fun formatNational(digits: String, country: Country): String {
    val groups = country.sample.split(" ").map { it.length }
    val sb = StringBuilder()
    var i = 0
    for (g in groups) {
        if (i >= digits.length) break
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(digits, i, minOf(i + g, digits.length))
        i += g
    }
    // Anything past the sample's shape runs on unbroken rather than being invented into groups.
    if (i < digits.length) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(digits, i, digits.length)
    }
    return sb.toString()
}
