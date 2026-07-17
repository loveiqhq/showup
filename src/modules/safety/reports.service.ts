import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { User } from '../users/entities/user.entity';
import { CreateReportDto } from './dto/create-report.dto';
import { UpdateReportDto } from './dto/update-report.dto';
import { Report } from './entities/report.entity';
import {
  isValidReasonForContext,
  latenessAppliesTo,
  ReportStatus,
} from './util/report';

/** A report is considered closed (its handling is finished) in these statuses. */
const CLOSED_STATUSES = [ReportStatus.Resolved, ReportStatus.Dismissed];

/**
 * User reports (SHOWUP-78). Users submit a report against another user with a reason valid for their
 * context; staff work the reports through the review list (via the admin controller). Each report is
 * a stored, traceable row that carries a status from submitted through to resolved.
 */
@Injectable()
export class ReportsService {
  constructor(
    @InjectRepository(Report) private readonly reports: Repository<Report>,
    @InjectRepository(User) private readonly users: Repository<User>,
  ) {}

  /** Submit a report. Validates the target, the reason/context pairing, and the lateness rule. */
  async create(reporterId: string, dto: CreateReportDto): Promise<Report> {
    if (reporterId === dto.reportedUserId) {
      throw new BadRequestException('You cannot report yourself');
    }
    const target = await this.users.findOne({
      where: { id: dto.reportedUserId },
    });
    if (!target) throw new NotFoundException('User not found');

    if (!isValidReasonForContext(dto.context, dto.reason)) {
      throw new BadRequestException(
        'That reason is not available in this context',
      );
    }
    if (latenessAppliesTo(dto.reason) && !dto.lateness) {
      throw new BadRequestException(
        'A lateness amount is required for "showed up late"',
      );
    }

    return this.reports.save(
      this.reports.create({
        reporterId,
        reportedUserId: dto.reportedUserId,
        context: dto.context,
        reason: dto.reason,
        // Only keep lateness where it makes sense.
        lateness: latenessAppliesTo(dto.reason) ? (dto.lateness ?? null) : null,
        dateId: dto.dateId ?? null,
        note: dto.note ?? null,
        status: ReportStatus.Submitted,
      }),
    );
  }

  /** The staff review list, optionally filtered by status. Newest first, capped. */
  async listForStaff(status?: ReportStatus): Promise<Report[]> {
    return this.reports.find({
      where: status ? { status } : {},
      order: { createdAt: 'DESC' },
      take: 200,
    });
  }

  async getOne(id: string): Promise<Report> {
    const report = await this.reports.findOne({ where: { id } });
    if (!report) throw new NotFoundException('Report not found');
    return report;
  }

  /** Move a report along (staff). Records who handled it and, once closed, when. */
  async updateStatus(
    id: string,
    handledByUserId: string,
    dto: UpdateReportDto,
  ): Promise<Report> {
    const report = await this.getOne(id);
    report.status = dto.status;
    report.handledByUserId = handledByUserId;
    if (dto.resolutionNote !== undefined) {
      report.resolutionNote = dto.resolutionNote;
    }
    report.resolvedAt = CLOSED_STATUSES.includes(dto.status)
      ? new Date()
      : null;
    return this.reports.save(report);
  }
}
