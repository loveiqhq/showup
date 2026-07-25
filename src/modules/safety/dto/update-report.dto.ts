import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsEnum, IsOptional, IsString, MaxLength } from 'class-validator';

import { ReportStatus } from '../util/report';

/** What a staff member sends to move a report along (SHOWUP-78 / SHOWUP-80). */
export class UpdateReportDto {
  @ApiProperty({ enum: ReportStatus })
  @IsEnum(ReportStatus)
  status: ReportStatus;

  @ApiPropertyOptional({
    description: 'Note on how it was handled (kept on the report).',
  })
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  resolutionNote?: string;
}
