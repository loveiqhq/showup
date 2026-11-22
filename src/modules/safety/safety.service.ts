import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';

import { DatePairBlock } from '../dates/entities/date-pair-block.entity';
import { Block } from './entities/block.entity';
import { BlockSource, dedupeBlockIds } from './util/safety';

/**
 * Blocking (SHOWUP-77). A block is directional and stored once per pair; unblocking is soft (sets
 * `unblockedAt`) so the history stays traceable. Discovery and matching hide the *union* of both
 * directions — see `blockedUserIdsFor`.
 */
@Injectable()
export class SafetyService {
  constructor(
    @InjectRepository(Block) private readonly blocks: Repository<Block>,
    @InjectRepository(DatePairBlock)
    private readonly pairBlocks: Repository<DatePairBlock>,
  ) {}

  /**
   * Block someone (idempotent; reactivates a previously-lifted block). Cannot block yourself.
   * `source` records how the block arose — a direct tap by default, or one of the date/search flows
   * (SHOWUP-77) when they call this. A single row hides the two from each other in both directions.
   */
  async block(
    blockerId: string,
    blockedId: string,
    source: BlockSource = BlockSource.Manual,
  ): Promise<Block> {
    if (blockerId === blockedId) {
      throw new BadRequestException('You cannot block yourself');
    }
    const existing = await this.blocks.findOne({
      where: { blockerId, blockedId },
    });
    if (existing) {
      if (existing.unblockedAt !== null) {
        existing.unblockedAt = null;
        existing.source = source;
        return this.blocks.save(existing);
      }
      return existing;
    }
    return this.blocks.save(
      this.blocks.create({ blockerId, blockedId, source, unblockedAt: null }),
    );
  }

  /** Lift a block. Idempotent — a no-op if there is no active block. */
  async unblock(blockerId: string, blockedId: string): Promise<void> {
    const existing = await this.blocks.findOne({
      where: { blockerId, blockedId, unblockedAt: IsNull() },
    });
    if (!existing) return;
    existing.unblockedAt = new Date();
    await this.blocks.save(existing);
  }

  /** The active blocks the viewer has created. */
  async listBlocks(userId: string): Promise<Block[]> {
    return this.blocks.find({
      where: { blockerId: userId, unblockedAt: IsNull() },
      order: { createdAt: 'DESC' },
    });
  }

  /**
   * Everyone who must be hidden from the viewer: people the viewer blocked plus people who blocked
   * the viewer, plus the other person of any permanent date pair block (Date 07, either side of the
   * pair), de-duplicated. Drops straight into the `blockedUserIds` filter of the proximity
   * search and matching.
   */
  async blockedUserIdsFor(userId: string): Promise<string[]> {
    const [byViewer, ofViewer, pairs] = await Promise.all([
      this.blocks.find({
        where: { blockerId: userId, unblockedAt: IsNull() },
        select: { blockedId: true },
      }),
      this.blocks.find({
        where: { blockedId: userId, unblockedAt: IsNull() },
        select: { blockerId: true },
      }),
      this.pairBlocks.find({
        where: [{ userLow: userId }, { userHigh: userId }],
        select: { userLow: true, userHigh: true },
      }),
    ]);
    return dedupeBlockIds(
      byViewer.map((b) => b.blockedId),
      ofViewer.map((b) => b.blockerId),
      pairs.map((p) => (p.userLow === userId ? p.userHigh : p.userLow)),
    );
  }

  /** Whether either person has blocked the other, or a red-link cancel parted them for good (used to reject a like). */
  async isBlockedEitherWay(a: string, b: string): Promise<boolean> {
    const found = await this.blocks.findOne({
      where: [
        { blockerId: a, blockedId: b, unblockedAt: IsNull() },
        { blockerId: b, blockedId: a, unblockedAt: IsNull() },
      ],
    });
    if (found !== null) return true;
    const [userLow, userHigh] = a < b ? [a, b] : [b, a];
    return (await this.pairBlocks.count({ where: { userLow, userHigh } })) > 0;
  }

  /** Active blocks pointing at this user (for staff review). */
  async blockedBy(userId: string): Promise<string[]> {
    const rows = await this.blocks.find({
      where: { blockedId: userId, unblockedAt: IsNull() },
      select: { blockerId: true },
    });
    return rows.map((b) => b.blockerId);
  }
}
