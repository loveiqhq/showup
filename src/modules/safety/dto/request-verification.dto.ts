import { ApiProperty } from '@nestjs/swagger';
import { Equals, IsBoolean } from 'class-validator';

/**
 * Request to run selfie verification. Consent must be explicitly given because a face is
 * special-category biometric data under GDPR Art. 9 (SHOWUP-110).
 */
export class RequestVerificationDto {
  @ApiProperty({
    description:
      'Explicit consent to process biometric (face) data. Must be true.',
  })
  @IsBoolean()
  @Equals(true)
  consent: boolean;
}
