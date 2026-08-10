/**
 * Sensitivity stamping for attribute events (Epic 11, SHOWUP-73 · requirements.json sensitivity_model).
 *
 * Every attribute event must carry the field's sensitivity class, resolved AT EMIT TIME (never joined
 * downstream, so a row keeps the classification it was collected under). An unrecognised field FAILS
 * CLOSED to Class 2 — wrong-but-safe: special-category data can never silently land in an unrestricted
 * table because a new field wasn't in the registry yet.
 *
 * The values below mirror registry/enums.json (the source of truth). sensitivity.spec.ts loads that
 * JSON and fails if the two ever drift, so this map cannot silently fall out of sync.
 */

/** Sensitivity classes: 0 non-sensitive · 1 personal · 2 special-category · 3 biometric/safety. */
export type SensitivityClass = 0 | 1 | 2 | 3;

/** Bumped whenever the field_id registry vocabulary changes; stamped as field_registry_version. */
export const FIELD_REGISTRY_VERSION = '1.1.0';

/** Fail-closed default for a field not present in the registry. */
export const SENSITIVITY_UNKNOWN_DEFAULT: SensitivityClass = 2;

/** field_id → sensitivity class. Mirrors enums.json field_id (drift-guarded by the spec). */
export const SENSITIVITY_BY_FIELD: Record<string, SensitivityClass> = {
  height: 0,
  gender: 2,
  orientation: 2,
  dating_language: 1,
  education: 1,
  religion: 2,
  politics: 2,
  habits: 2,
  age: 0,
};

/**
 * The sensitivity class for a field, resolved at emit time. Unknown fields fail closed to Class 2 and
 * should also be counted in an "unclassified attributes" metric by the caller.
 */
export function sensitivityClassFor(fieldId: string): SensitivityClass {
  return SENSITIVITY_BY_FIELD[fieldId] ?? SENSITIVITY_UNKNOWN_DEFAULT;
}
