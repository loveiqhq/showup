/*
 * ProfileDetailsRepository.kt
 * ShowUp · the "Share some details" answers, read and written (SHOWUP-167 to SHOWUP-173)
 *
 * Two calls, both on the owner's own profile: `GET /me/profile` to pre-fill a step on a resume or a
 * back navigation, and `PATCH /me/profile` to store an answer, its visibility and the flow position
 * in ONE request. One request is the point -- "persist the value and the visibility flag, then the
 * flow position" cannot half-happen when the server commits all three in one transaction.
 *
 * No hand-written models: the bodies are the generated `UpsertProfileDto` and `OwnProfileDto`.
 */
package com.showup.profile

import com.showup.api.ShowUpApi
import com.showup.api.generated.model.OwnProfileDto
import com.showup.api.generated.model.UpsertProfileDto

/** The read and the write, behind an interface so the view model is tested with no network. */
interface ProfileDetailsStore {
    /** What the account holds, or null when it could not be read. */
    suspend fun load(): SavedDetails?

    /** Stores [body]. What the server now holds on success, null on any failure. */
    suspend fun save(body: UpsertProfileDto): SavedDetails?
}

class ProfileDetailsRepository(private val api: ShowUpApi) : ProfileDetailsStore {

    override suspend fun load(): SavedDetails? = runCatching {
        val response = api.profiles.getProfile()
        response.body()?.takeIf { response.isSuccessful }?.toSavedDetails()
    }.getOrNull()

    override suspend fun save(body: UpsertProfileDto): SavedDetails? = runCatching {
        val response = api.profiles.updateProfile(body)
        response.body()?.takeIf { response.isSuccessful }?.toSavedDetails()
    }.getOrNull()
}

/**
 * The owner's view, as the details group reads it.
 *
 * The answers arrive as strings by design (`profile.dto.ts`, `answerSet`): a newer server may add
 * values, and an installed app must still load its own profile. Unknown values are kept here and
 * simply match no row on screen.
 */
internal fun OwnProfileDto.toSavedDetails() = SavedDetails(
    heightCm = heightCm,
    gender = gender,
    orientation = orientation,
    datingLanguages = datingLanguages.orEmpty(),
    education = education,
    religion = religion,
    politics = politics,
    hiddenFields = hiddenFields.toSet(),
)
