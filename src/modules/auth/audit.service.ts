import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { AuditLog } from './entities/audit-log.entity';

@Injectable()
export class AuditService {
  constructor(
    @InjectRepository(AuditLog)
    private readonly repo: Repository<AuditLog>,
  ) {}

  /** Record a security/account event. `metadata` must never contain tokens or codes. */
  async record(
    action: string,
    userId: string | null = null,
    metadata: Record<string, unknown> | null = null,
  ): Promise<void> {
    await this.repo.save(this.repo.create({ action, userId, metadata }));
  }
}
