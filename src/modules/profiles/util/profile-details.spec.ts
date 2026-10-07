import { readFileSync } from 'fs';
import { join } from 'path';
import {
  canonicalDatingLanguages,
  DATING_LANGUAGES,
  EDUCATIONS,
  GENDERS,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
} from './profile-details';

/**
 * THE REGISTRY IS THE SOURCE, and this reads it rather than restating it. Every list in
 * profile-details.ts must equal its §1 row in `enums.json`, in the same order -- a value added to
 * the registry and not here would be a screen option the server rejects with 400, and one here
 * and not in the registry would be stored and never analysed.
 */
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

interface Row {
  field_id: string;
  value_bucketed: string;
}

function registryValues(fieldId: string): string[] {
  const registry = JSON.parse(readFileSync(REGISTRY, 'utf-8')) as {
    sets: { id: string; tables: { rows: Row[] }[] }[];
  };
  const fields = registry.sets.find((s) => s.id === 'e1');
  const row = fields?.tables
    .flatMap((t) => t.rows)
    .find((r) => r.field_id === fieldId);
  if (!row) throw new Error(`§1 has no row for ${fieldId}`);
  return row.value_bucketed.split('·').map((v) => v.trim());
}

describe('detail value sets mirror enums.json §1 (drift guard)', () => {
  it.each([
    ['gender', GENDERS],
    ['orientation', ORIENTATIONS],
    ['dating_language', DATING_LANGUAGES],
    ['education', EDUCATIONS],
    ['religion', RELIGIONS],
    ['politics', POLITICS],
  ])('%s matches the registry, in order', (fieldId, values) => {
    expect([...values]).toEqual(registryValues(fieldId));
  });

  it('religion includes muslim, added in registry 1.4.14', () => {
    expect(RELIGIONS).toContain('muslim');
  });

  it('politics is linear: the spectrum first, then the off-axis answers', () => {
    expect(POLITICS.slice(0, 5)).toEqual([
      'left',
      'mid_left',
      'middle',
      'mid_right',
      'right',
    ]);
  });
});

describe('canonicalDatingLanguages', () => {
  it('stores list order whatever order the boxes were ticked in', () => {
    expect(canonicalDatingLanguages(['arabic', 'german', 'french'])).toEqual([
      'german',
      'french',
      'arabic',
    ]);
  });

  it('collapses a repeated value to one', () => {
    expect(canonicalDatingLanguages(['english', 'english'])).toEqual([
      'english',
    ]);
  });

  it('never stores a value outside the list', () => {
    expect(canonicalDatingLanguages(['klingon', 'italian'])).toEqual([
      'italian',
    ]);
  });
});
