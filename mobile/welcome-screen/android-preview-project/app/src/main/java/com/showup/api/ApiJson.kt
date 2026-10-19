package com.showup.api

import com.showup.api.generated.infrastructure.Serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Converter
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * The JSON the app sends. It differs from the generated default in exactly one setting, and that
 * setting is the difference between a partial update and a destructive one.
 *
 * WHY THIS EXISTS
 *
 * The generated `Serializer` builds its `Json` with `encodeDefaults = true`. Every optional field
 * on a generated request model defaults to `null`, so a body built to change one field arrives
 * carrying every other field explicitly set to null:
 *
 *     UpsertProfileDto(hiddenFields = listOf("age"))
 *     -> {"dateOfBirth":null,"displayName":null,"gender":null,
 *         "hiddenFields":["age"],"isVisible":null,"lookingFor":null}
 *
 * The backend's `PATCH /me/profile` applies any key that is present. An explicit null is present.
 * So that request would clear the user's name, date of birth, gender and looking-for, and set a
 * NOT NULL boolean column to null. A request meant to hide an age would empty the profile.
 *
 * `encodeDefaults = false` omits an unset optional entirely, which is what "optional" means in the
 * contract and what PATCH semantics require. Required fields have no default and are unaffected.
 *
 * WHY NOT EDIT THE GENERATED FILE
 *
 * It lives in `build/` and is rewritten on every build; a fix there survives exactly until the next
 * one. `ApiClient` takes `converterFactories` as a constructor parameter precisely so callers can
 * do this, so this is the generator's own seam rather than a workaround.
 *
 * Decoding is unaffected: `encodeDefaults` governs serialisation only.
 */
object ApiJson {
    val json: Json = Json {
        // Same adapters as the generated default -- the contextual date/UUID adapters are required
        // for OffsetDateTime fields to decode at all.
        serializersModule = Serializer.kotlinxSerializationAdapters
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Pass to `ApiClient(converterFactories = ...)`. Order matches the generated default:
     * scalars first, then JSON.
     */
    val converterFactories: List<Converter.Factory> = listOf(
        ScalarsConverterFactory.create(),
        json.asConverterFactory("application/json".toMediaType()),
    )
}
