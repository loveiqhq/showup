import { readFileSync } from 'fs';
import { join } from 'path';
import {
  ACTIVITIES,
  ALCOHOL,
  canonicalDatingLanguages,
  canonicalInterests,
  canonicalLifeAnswer,
  DATING_LANGUAGES,
  DIETS,
  EDUCATIONS,
  GENDERS,
  INTEREST_SECTIONS,
  INTEREST_TAGS,
  isContradictoryLifeAnswer,
  LIFE_QUESTIONS,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
  RHYTHMS,
  SMOKING,
  SOCIAL_BATTERIES,
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

function rowsOf<T>(setId: string): T[] {
  const registry = JSON.parse(readFileSync(REGISTRY, 'utf-8')) as {
    sets: { id: string; tables: { rows: T[] }[] }[];
  };
  const set = registry.sets.find((s) => s.id === setId);
  if (!set) throw new Error(`the registry has no set ${setId}`);
  return set.tables.flatMap((t) => t.rows);
}

/** A registry cell's `·`-separated list. An em dash is "none"; a parenthesis is commentary. */
function cellList(cell: string): string[] {
  return cell
    .split('(')[0]
    .split('·')
    .map((v) => v.trim())
    .filter((v) => v !== '' && v !== '—');
}

function registryValues(fieldId: string): string[] {
  const row = rowsOf<Row>('e1').find((r) => r.field_id === fieldId);
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
    ['activity', ACTIVITIES],
    ['diet', DIETS],
    ['alcohol', ALCOHOL],
    ['smoking', SMOKING],
    ['rhythm', RHYTHMS],
    ['social', SOCIAL_BATTERIES],
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

describe('interest tags mirror enums.json §3 (drift guard)', () => {
  const rows = rowsOf<{ section: string; tag_id: string }>('e3');

  it('has the registry sections, in order', () => {
    expect(Object.keys(INTEREST_SECTIONS)).toEqual(rows.map((r) => r.section));
  });

  it.each(rows.map((r) => [r.section, cellList(r.tag_id)] as const))(
    '%s holds the registry tags, in order',
    (section, tags) => {
      expect([
        ...INTEREST_SECTIONS[section as keyof typeof INTEREST_SECTIONS],
      ]).toEqual(tags);
    },
  );

  it('has 51 tags, none repeated across sections', () => {
    expect(INTEREST_TAGS).toHaveLength(51);
    expect(new Set(INTEREST_TAGS).size).toBe(INTEREST_TAGS.length);
  });
});

describe('life questions mirror enums.json §4 (drift guard)', () => {
  const rows = rowsOf<{
    question_id: string;
    values: string;
    exclusive: string;
  }>('e4');

  it('has the registry questions, in order', () => {
    expect(Object.keys(LIFE_QUESTIONS)).toEqual(rows.map((r) => r.question_id));
  });

  it.each(rows.map((r) => [r.question_id, r] as const))(
    '%s has the registry values and exclusive set, in order',
    (questionId, row) => {
      const question =
        LIFE_QUESTIONS[questionId as keyof typeof LIFE_QUESTIONS];
      expect([...question.values]).toEqual(cellList(row.values));
      expect([...question.exclusive]).toEqual(cellList(row.exclusive));
    },
  );
});

describe('canonicalInterests', () => {
  it('stores list order whatever order the chips were tapped in', () => {
    expect(canonicalInterests(['cruises', 'gym', 'reading'])).toEqual([
      'gym',
      'reading',
      'cruises',
    ]);
  });

  it('collapses a repeat and drops a value outside the list', () => {
    expect(canonicalInterests(['wine', 'wine', 'skydiving'])).toEqual(['wine']);
  });
});

describe('canonicalLifeAnswer', () => {
  it('stores list order, not tap order', () => {
    expect(
      canonicalLifeAnswer(LIFE_QUESTIONS.living, [
        'living_with_children',
        'living_alone',
      ]),
    ).toEqual(['living_alone', 'living_with_children']);
  });

  it('keeps an empty answer empty: walked past is an answer', () => {
    expect(canonicalLifeAnswer(LIFE_QUESTIONS.partnership, [])).toEqual([]);
  });
});

describe('isContradictoryLifeAnswer', () => {
  it('refuses No children together with any other family answer', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family, [
        'no_children',
        'single_parent',
      ]),
    ).toBe(true);
  });

  it('accepts No children alone, and several non-exclusive answers', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family, ['no_children']),
    ).toBe(false);
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family, [
        'single_parent',
        'co_parenting',
      ]),
    ).toBe(false);
  });

  it('allows one family vision at most', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family_ahead, [
        'want_kids',
        'dont_want_kids',
      ]),
    ).toBe(true);
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family_ahead, ['want_kids']),
    ).toBe(false);
  });

  it('refuses find out together with another living vision', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.living_ahead, [
        'find_out_together',
        'move_abroad',
      ]),
    ).toBe(true);
  });

  it('does not count a repeated value twice', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.family, [
        'no_children',
        'no_children',
      ]),
    ).toBe(false);
  });

  it('never calls an answer with no exclusive values contradictory', () => {
    expect(
      isContradictoryLifeAnswer(LIFE_QUESTIONS.partnership, [
        'single',
        'open_relationship',
        'divorced',
      ]),
    ).toBe(false);
  });
});
