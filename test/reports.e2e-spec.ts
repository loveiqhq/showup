import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';

import { AppModule } from './../src/app.module';
import { ReportsService } from './../src/modules/safety/reports.service';
import {
  LatenessBucket,
  ReportContext,
  ReportReason,
  ReportStatus,
} from './../src/modules/safety/util/report';
import { User, UserRole } from './../src/modules/users/entities/user.entity';

const PHONES = {
  reporter: '+491708880001',
  reported: '+491708880002',
  admin: '+491708880003',
};

describe('Reports (e2e)', () => {
  let app: INestApplication;
  let reports: ReportsService;
  let users: Repository<User>;

  const id: Record<string, string> = {};
  let reportId: string;

  const seedUser = async (phone: string, role = UserRole.User) =>
    (await users.save(users.create({ phone, role }))).id;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    await app.init();

    reports = app.get(ReportsService);
    users = app.get<Repository<User>>(getRepositoryToken(User), {
      strict: false,
    });

    await users.delete({ phone: In(Object.values(PHONES)) });
    id.reporter = await seedUser(PHONES.reporter);
    id.reported = await seedUser(PHONES.reported);
    id.admin = await seedUser(PHONES.admin, UserRole.Admin);
  });

  afterAll(async () => {
    await users.delete({ phone: In(Object.values(PHONES)) });
    await app.close();
  });

  it('submits a report with a valid reason and starts it as submitted', async () => {
    const report = await reports.create(id.reporter, {
      reportedUserId: id.reported,
      context: ReportContext.ActiveDate,
      reason: ReportReason.FeltInDanger,
    });
    reportId = report.id;
    expect(report.reportedUserId).toBe(id.reported);
    expect(report.status).toBe(ReportStatus.Submitted);
  });

  it('refuses to let a user report themselves', async () => {
    await expect(
      reports.create(id.reporter, {
        reportedUserId: id.reporter,
        context: ReportContext.ActiveDate,
        reason: ReportReason.FeltUncomfortable,
      }),
    ).rejects.toThrow();
  });

  it('rejects a reason that is not valid for the context', async () => {
    await expect(
      reports.create(id.reporter, {
        reportedUserId: id.reported,
        context: ReportContext.ActiveDate,
        reason: ReportReason.ShowedUpLate, // review-only reason
      }),
    ).rejects.toThrow();
  });

  it('requires a lateness amount for "showed up late", and stores it when given', async () => {
    await expect(
      reports.create(id.reporter, {
        reportedUserId: id.reported,
        context: ReportContext.DateReview,
        reason: ReportReason.ShowedUpLate,
      }),
    ).rejects.toThrow();

    const late = await reports.create(id.reporter, {
      reportedUserId: id.reported,
      context: ReportContext.DateReview,
      reason: ReportReason.ShowedUpLate,
      lateness: LatenessBucket.Late10,
    });
    expect(late.lateness).toBe(LatenessBucket.Late10);
  });

  it('appears in the staff review list and can be filtered by status', async () => {
    const all = await reports.listForStaff();
    expect(all.map((r) => r.id)).toContain(reportId);

    const submitted = await reports.listForStaff(ReportStatus.Submitted);
    expect(submitted.map((r) => r.id)).toContain(reportId);
  });

  it('lets staff move a report to resolved, recording who handled it and when', async () => {
    const resolved = await reports.updateStatus(reportId, id.admin, {
      status: ReportStatus.Resolved,
      resolutionNote: 'Spoke to both parties.',
    });
    expect(resolved.status).toBe(ReportStatus.Resolved);
    expect(resolved.handledByUserId).toBe(id.admin);
    expect(resolved.resolvedAt).not.toBeNull();
  });
});
