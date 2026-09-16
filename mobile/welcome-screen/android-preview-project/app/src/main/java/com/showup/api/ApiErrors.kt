package com.showup.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The error body, parsed by hand rather than through the generated model.
 *
 * WHY NOT USE THE GENERATED ApiErrorDto
 *
 * The backend contract describes `message` truthfully: it is a string for most failures and an
 * ARRAY of strings when request validation fails, one entry per failed constraint. In OpenAPI that
 * is `oneOf: [string, array]`, and it is correct.
 *
 * The Kotlin generator cannot model it. It emits `ApiErrorDtoMessage` as an empty @Serializable
 * class with no properties, which can decode neither a string nor an array -- so the generated
 * `ApiErrorDto` is unusable for exactly the field callers need most.
 *
 * The three ways out were: narrow the contract to `string` (a lie, and it breaks on precisely the
 * validation errors users hit most often), declare it untyped (NestJS then infers `type: object`,
 * which is a worse lie), or change the backend to always return an array (a behaviour change).
 *
 * So the contract stays honest and this file absorbs the awkwardness. It is deliberately the only
 * hand-written thing in the API layer, and the reason is written down so nobody "fixes" it by
 * making the contract wrong.
 */
data class ApiError(
    val statusCode: Int,
    /** Every message the server sent. One entry usually; one per failed constraint on a 400. */
    val messages: List<String>,
    /** Short identifier: usually the reason phrase, sometimes a domain code like step_up_required. */
    val error: String,
) {
    /** The whole thing as one line, for logging. Never shown to a user as-is. */
    val summary: String get() = "$statusCode $error: ${messages.joinToString("; ")}"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parse an error body. Never throws: a failure here would replace a useful server error
         * with a parse error, which is strictly less information than we started with.
         */
        fun parse(status: Int, body: String?): ApiError {
            val fallback = ApiError(status, listOf("Something went wrong."), "unknown")
            if (body.isNullOrBlank()) return fallback

            return runCatching {
                val root = json.parseToJsonElement(body) as? JsonObject ?: return fallback
                val messages = when (val m = root["message"]) {
                    is JsonArray -> m.mapNotNull { (it as? JsonPrimitive)?.content }
                    is JsonPrimitive -> listOf(m.content)
                    else -> fallback.messages
                }
                ApiError(
                    statusCode = root["statusCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: status,
                    messages = messages.ifEmpty { fallback.messages },
                    error = root["error"]?.jsonPrimitive?.content ?: fallback.error,
                )
            }.getOrDefault(fallback)
        }
    }
}
