import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

import { Report } from '../entities/report.entity';
import {
  LatenessBucket,
  ReportContext,
  ReportReason,
  ReportStatus,
} from '../util/report';

/** A report, as returned to the reporter and to staff. */
export class ReportDto {
  @ApiProperty() id: string;
  @ApiProperty() reportedUserId: string;
  @ApiProperty({ enum: ReportContext }) context: ReportContext;
  @ApiProperty({ enum: ReportReason }) reason: ReportReason;
  @ApiPropertyOptional({ enum: LatenessBucket }) lateness?: LatenessBucket;
  @ApiProperty({ enum: ReportStatus }) status: ReportStatus;
  @ApiProperty() createdAt: Date;

  static from(r: Report): ReportDto {
    return {
      id: r.id,
      reportedUserId: r.reportedUserId,
      context: r.context,
      reason: r.reason,
      lateness: r.lateness ?? undefined,
      status: r.status,
      createdAt: r.createdAt,
    };
  }
}

/** A report row for the staff review list (adds who filed it). */
export class StaffReportDto extends ReportDto {
  @ApiProperty() reporterId: string;
  @ApiPropertyOptional() note?: string;

  static fromReport(r: Report): StaffReportDto {
    return {
      ...ReportDto.from(r),
      reporterId: r.reporterId,
      note: r.note ?? undefined,
    };
  }
}
