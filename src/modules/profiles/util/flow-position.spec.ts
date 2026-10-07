import { readFileSync } from 'fs';
import { join } from 'path';
import {
  advanceFlowPosition,
  FLOW_POSITIONS,
  flowPositionRank,
} from './flow-position';

const REGISTRY = join(
  __dirname,
  '..',
  '..',
  '..',
  '..',
  'design_handoff_showup',
  'tracking',
  'enums.json',
);

describe('flow positions', () => {
  it('every position is a §2 step_id -- the registry flow-progress vocabulary', () => {
    const registry = JSON.parse(readFileSync(REGISTRY, 'utf-8')) as {
      sets: { id: string; tables: { rows: { step_id: string }[] }[] }[];
    };
    const steps = new Set(
      registry.sets
        .find((s) => s.id === 'e2')!
        .tables.flatMap((t) => t.rows)
        .map((r) => r.step_id),
    );
    for (const position of FLOW_POSITIONS) {
      expect(steps.has(position)).toBe(true);
    }
  });

  it('walks in the order the screens are reached', () => {
    const walk = [
      'media_video',
      'notifications',
      'reachability',
      'location',
      'height',
      'gender',
      'orientation',
      'dating_language',
      'education',
      'religion',
      'politics',
    ];
    const ranks = walk.map(flowPositionRank);
    expect(ranks).toEqual([...ranks].sort((a, b) => a - b));
    expect(new Set(ranks).size).toBe(walk.length);
  });

  it('the media screen is one step under either of its two ids', () => {
    expect(flowPositionRank('media_voice')).toBe(
      flowPositionRank('media_video'),
    );
  });

  it('is not a position for anything outside the vocabulary', () => {
    expect(flowPositionRank('interests')).toBe(-1);
    expect(flowPositionRank(null)).toBe(-1);
  });
});

describe('advanceFlowPosition', () => {
  it('a first report is stored', () => {
    expect(advanceFlowPosition(null, 'location')).toBe('location');
  });

  it('moves forward', () => {
    expect(advanceFlowPosition('height', 'gender')).toBe('gender');
  });

  it('never rewinds when the user goes back to an earlier step', () => {
    expect(advanceFlowPosition('gender', 'height')).toBe('gender');
  });

  it('the other media id at the same rank is not movement', () => {
    expect(advanceFlowPosition('media_voice', 'media_video')).toBe(
      'media_voice',
    );
  });

  it('moves past a stored value that is not a position at all', () => {
    expect(advanceFlowPosition('garbage', 'height')).toBe('height');
  });

  it('the last step stays the last step', () => {
    expect(advanceFlowPosition('politics', 'media_video')).toBe('politics');
  });
});
