/** Compute age in whole years from a `YYYY-MM-DD` date of birth. Null if missing/invalid. */
export function ageFromDateOfBirth(dateOfBirth: string | null): number | null {
  if (!dateOfBirth) return null;
  const birth = new Date(`${dateOfBirth}T00:00:00Z`);
  if (Number.isNaN(birth.getTime())) return null;

  const now = new Date();
  let age = now.getUTCFullYear() - birth.getUTCFullYear();
  const monthDiff = now.getUTCMonth() - birth.getUTCMonth();
  if (
    monthDiff < 0 ||
    (monthDiff === 0 && now.getUTCDate() < birth.getUTCDate())
  ) {
    age -= 1;
  }
  return age;
}

/** Dating apps are 18+. Returns true only if the date of birth is a valid age of at least 18. */
export function isAtLeast18(dateOfBirth: string | null): boolean {
  const age = ageFromDateOfBirth(dateOfBirth);
  return age !== null && age >= 18;
}
