import { BadRequestException } from '@nestjs/common';
import { parsePhoneNumberFromString } from 'libphonenumber-js';

/**
 * Validate and normalize a phone number to E.164 (e.g. `+4915123456789`).
 * Requires an international format with country code. Throws 400 on invalid input.
 */
export function normalizePhone(input: string): string {
  const parsed = parsePhoneNumberFromString(input.trim());
  if (!parsed || !parsed.isValid()) {
    throw new BadRequestException('Invalid phone number');
  }
  return parsed.number;
}
