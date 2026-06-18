import { ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBoolean,
  IsDateString,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';

/** Fields a user may set on their own profile (all optional; used by POST and PATCH). */
export class UpsertProfileDto {
  @ApiPropertyOptional({ example: 'Leo' })
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  displayName?: string;

  @ApiPropertyOptional({
    example: '1998-04-23',
    description: 'YYYY-MM-DD; must be 18+',
  })
  @IsOptional()
  @IsDateString()
  dateOfBirth?: string;

  @ApiPropertyOptional({ example: 'Coffee, climbing, and bad puns.' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  bio?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(40)
  gender?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(40)
  lookingFor?: string;

  @ApiPropertyOptional({
    description: 'Whether the profile is visible in discovery',
  })
  @IsOptional()
  @IsBoolean()
  isVisible?: boolean;
}
