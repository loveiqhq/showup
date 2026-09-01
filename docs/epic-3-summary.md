# Epic 3 — Profile & Media Management — Summary

*A high-level summary of what Epic 3 delivered, the decisions taken that refine the original
project proposal, and the tools involved. (Per-ticket detail lives in the Jira tickets
SHOWUP-32…35 and SHOWUP-109 and is not repeated here.)*

---

## 1. What Epic 3 delivered (in short)

Epic 3 built the **backend foundation for user profiles and media**: where a profile lives, how
it is created and edited, how photos are uploaded and stored, and the scaffolding for the
"verified" badge. The core of this is **built and merged** (PR #3). A follow-up fix (minimum
photo count) is **in review** (PR #4). The full identity-verification feature and a few
refinements below are **designed and ticketed, to be built next**.

---

## 2. Key decisions & changes vs. the original proposal

These are the choices made during Epic 3 that **refine or extend** the original plan:

- **Age is hidden by default.** A profile no longer shows age; the user opts in to display it.
  Date of birth is kept server-side only (used for the 18+ check and matching), never exposed.
- **Photos: minimum 4, maximum 6**, and at least one must contain a real human face (other
  photos — scenery, pets, etc. — are allowed).
- **Verification redefined.** The "verified" badge means an **active-liveness selfie matched
  against the user's own profile photos** (the approach Tinder and Bumble use), and it is
  **mandatory during onboarding**. Epic 3 ships only the *status scaffolding* plus an admin
  approve/reject placeholder; the real selfie flow is a dedicated follow-up ticket (SHOWUP-109).
- **"Verified" ≠ "Complete".** Identity verification (the badge) is kept separate from profile
  completeness (filling in the optional questions).
- **Reciprocal visibility.** A user only sees another person's optional attributes if they have
  filled in their own — this both protects privacy and encourages profile completion.
- **Slimmer initial onboarding.** Fewer questions at sign-up to reduce drop-off; the app asks for
  more profile detail later.
- **Three-tier data model.** Stable profile data, rarely-changing "disclosure" attributes, and
  fast-changing **check-in data are kept in separate stores** (the check-in store is built in
  Epic 4).
- **Account-recovery approach.** Phone number is the mandatory sign-up anchor; email / Apple /
  Google are added later as alternative logins. If a user loses access, recovery uses an
  identifier (old phone + date of birth) plus a **liveness selfie matched against the account's
  photos** — no pre-existing "verified" badge required.
- **Minimal admin role + guard** added to support verification review (seeds the later Admin epic).
- **External services kept behind seams.** Photo storage goes through a `StorageService`
  abstraction (local disk in development, cloud storage later) so the provider can change without
  rewrites.

---

## 3. Tools & technology

**Used in the Epic 3 code (already built):**
- **NestJS** (API framework), **TypeORM** + **PostgreSQL / PostGIS** (database), **Joi** (config
  validation), **Jest** (tests), **Swagger** (API docs), and a local-disk **StorageService**
  adapter for photos (cloud storage to follow).

**Selected for the upcoming verification & photo features (decided, not yet wired up):**
- **AWS Rekognition** — three tools, used per feature when built:
  - **DetectFaces** — confirm at least one profile photo contains a human face.
  - **CompareFaces** — match the selfie against the profile photos (verification + recovery).
  - **Face Liveness** — confirm a real, live person (verification + recovery).
- These run **pay-per-use** (no upfront cost) and will be hosted in **AWS Europe (Ireland,
  eu-west-1)** — the only EU region offering all three — which keeps all data inside the EU/EEA
  and GDPR-compliant.

---

## 4. Compliance note

Dating data — including the **biometric** verification selfie and **sexual orientation** — is
"special-category" data under GDPR. This requires **explicit consent**, **deletion of the
biometric data when an account is deleted**, a **Data Protection Officer (DPO)**, and a **privacy
risk assessment (DPIA)**. These are set up as part of the privacy work and before launch — they
are independent of which face-recognition tool is chosen.

---

## 5. Current status

| Item | Status |
|---|---|
| Core profile / photo / verification-scaffolding code | ✅ Merged (PR #3) |
| Minimum-4-photos rule | 🟡 In review (PR #4) |
| Real selfie verification (liveness + match) | 🔲 Designed & ticketed (SHOWUP-109) |
| `show_age`, "≥1 human" photo check, attributes + reciprocal visibility | 🔲 Designed, to be built |
