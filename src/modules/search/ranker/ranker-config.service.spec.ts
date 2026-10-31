import { DataSource } from 'typeorm';

import { LAUNCH_RANKER_CONFIG, RankerConfig } from './ranker-config';
import {
  RANKER_CONFIG_TTL_MS,
  RankerConfigService,
} from './ranker-config.service';

/** A `ranker_configs` table the test controls, and how often it was read. */
function table(rows: () => Array<{ version: string; config: unknown }>) {
  const query = jest.fn(() => Promise.resolve(rows()));
  return {
    service: new RankerConfigService({ query } as unknown as DataSource),
    query,
  };
}

const tuned: RankerConfig = {
  ...LAUNCH_RANKER_CONFIG,
  ranker_version: 'tuned-2026-11',
  weights: { fit: 0.5, time: 0.25, reliability: 0.15, distance: 0.1 },
};

describe('RankerConfigService', () => {
  it('ranks with the launch numbers while no row is active', async () => {
    const { service } = table(() => []);
    expect(await service.current(0)).toBe(LAUNCH_RANKER_CONFIG);
  });

  it('uses the active row, and reads it again only after a minute', async () => {
    let rows = [{ version: tuned.ranker_version, config: tuned as unknown }];
    const { service, query } = table(() => rows);
    expect(await service.current(0)).toEqual(tuned);
    rows = [];
    expect(await service.current(RANKER_CONFIG_TTL_MS - 1)).toEqual(tuned);
    expect(query).toHaveBeenCalledTimes(1);
    // A minute later the change applies, with no deploy.
    expect(await service.current(RANKER_CONFIG_TTL_MS)).toBe(
      LAUNCH_RANKER_CONFIG,
    );
  });

  it('refuses an invalid row and keeps the last good config', async () => {
    let rows = [{ version: tuned.ranker_version, config: tuned as unknown }];
    const { service } = table(() => rows);
    expect(await service.current(0)).toEqual(tuned);
    rows = [{ version: 'broken', config: { ranker_version: 'broken' } }];
    expect(await service.current(RANKER_CONFIG_TTL_MS)).toEqual(tuned);
  });

  it('refuses a row that reuses the launch version with different numbers', async () => {
    const { service } = table(() => [
      {
        version: LAUNCH_RANKER_CONFIG.ranker_version,
        config: { ...LAUNCH_RANKER_CONFIG, fit_gate: 0.5 },
      },
    ]);
    expect(await service.current(0)).toBe(LAUNCH_RANKER_CONFIG);
  });

  it('keeps ranking when the table cannot be read', async () => {
    const service = new RankerConfigService({
      query: jest.fn(() =>
        Promise.reject(new Error('relation "ranker_configs" does not exist')),
      ),
    } as unknown as DataSource);
    expect(await service.current(0)).toBe(LAUNCH_RANKER_CONFIG);
  });
});
