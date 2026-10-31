import { Injectable, Logger } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { isDeepStrictEqual } from 'util';
import { DataSource } from 'typeorm';

import {
  LAUNCH_RANKER_CONFIG,
  parseRankerConfig,
  RankerConfig,
} from './ranker-config';

/** How long a read config is reused: a new active row orders feeds within this, with no deploy. */
export const RANKER_CONFIG_TTL_MS = 60_000;

/**
 * The ranker's numbers for this moment: the active `ranker_configs` row, or the launch numbers in the
 * code while there is none.
 *
 * A row that fails `parseRankerConfig` -- or reuses the launch version with different numbers, which
 * would make two sets of numbers share one name in the training data -- is refused with a warning and
 * the last good config stays in use. A bad row must never take Search down.
 */
@Injectable()
export class RankerConfigService {
  private readonly logger = new Logger('RankerConfig');
  private cached: { config: RankerConfig; readAt: number } | null = null;
  private readonly refused = new Set<string>();

  constructor(@InjectDataSource() private readonly db: DataSource) {}

  async current(now: number = Date.now()): Promise<RankerConfig> {
    if (this.cached && now - this.cached.readAt < RANKER_CONFIG_TTL_MS) {
      return this.cached.config;
    }
    const config = await this.read();
    this.cached = { config, readAt: now };
    return config;
  }

  private async read(): Promise<RankerConfig> {
    const fallback = this.cached?.config ?? LAUNCH_RANKER_CONFIG;
    let rows: Array<{ version: string; config: unknown }>;
    try {
      rows = await this.db.query(
        `SELECT "version", "config" FROM "ranker_configs" WHERE "active" LIMIT 1`,
      );
    } catch (err) {
      this.logger.warn(`ranker config read failed: ${(err as Error).message}`);
      return fallback;
    }
    if (rows.length === 0) return LAUNCH_RANKER_CONFIG;
    const [row] = rows;
    const parsed = parseRankerConfig(row.config);
    let problem = 'error' in parsed ? parsed.error : null;
    if (
      !problem &&
      'config' in parsed &&
      parsed.config.ranker_version === LAUNCH_RANKER_CONFIG.ranker_version &&
      !isDeepStrictEqual(parsed.config, LAUNCH_RANKER_CONFIG)
    ) {
      problem = 'reuses the launch version with different numbers';
    }
    if (problem || !('config' in parsed)) {
      // Once per version, so a bad row does not log on every feed load.
      if (!this.refused.has(row.version)) {
        this.refused.add(row.version);
        this.logger.warn(`ranker config ${row.version} refused: ${problem}`);
      }
      return fallback;
    }
    return parsed.config;
  }
}
