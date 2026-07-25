import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsEnum,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator';

import { LatenessBucket, ReportContext, ReportReason } from '../util/report';

/** What a user submits to report someone (SHOWUP-78). */
export class CreateReportDto {
  @ApiProperty({ description: 'The id of the user being reported' })
  @IsUUID()
  reportedUserId: string;

  @ApiProperty({
    enum: ReportContext,
    description: 'Where the report is made from',
  })
  @IsEnum(ReportContext)
  context: ReportContext;

  @ApiProperty({ enum: ReportReason })
  @IsEnum(ReportReason)
  reason: ReportReason;

  @ApiPropertyOptional({
    enum: LatenessBucket,
    description: 'Required only when the reason is "showed up late".',
  })
  @IsOptional()
  @IsEnum(LatenessBucket)
  lateness?: LatenessBucket;

  @ApiPropertyOptional({ description: 'The date this report relates to' })
  @IsOptional()
  @IsUUID()
  dateId?: string;

  @ApiPropertyOptional({
    description: 'Optional free-text note (max 1000 chars).',
  })
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  note?: string;
}
