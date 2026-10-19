import { readFileSync } from 'fs';
import { join } from 'path';

import {
  FIELD_REGISTRY_VERSION,
  SENSITIVITY_BY_FIELD,
  SENSITIVITY_UNKNOWN_DEFAULT,
  sensitivityClassFor,
} from './sensitivity';

describe('sensitivityClassFor', () => {
  it('returns the declared class for a known field', () => {
    expect(sensitivityClassFor('height')).toBe(0);
    expect(sensitivityClassFor('dating_language')).toBe(1);
    expect(sensitivityClassFor('gender')).toBe(2);
    expect(sensitivityClassFor('religion')).toBe(2);
  });

  it('fails CLOSED to Class 2 for an unrecognised field', () => {
    expect(SENSITIVITY_UNKNOWN_DEFAULT).toBe(2);
    expect(sensitivityClassFor('something_new')).toBe(2);
    expect(sensitivityClassFor('')).toBe(2);
  });

  it('exposes the field registry version stamped onto attribute events', () => {
    expect(typeof FIELD_REGISTRY_VERSION).toBe('string');
    expect(FIELD_REGISTRY_VERSION.length).toBeGreaterThan(0);
  });
});

describe('SENSITIVITY_BY_FIELD stays in sync with enums.json (drift guard)', () => {
  const registry = JSON.parse(
    readFileSync(join(__dirname, 'enums.json'), 'utf-8'),
  ) as {
    registry_version: string;
    field_id: Record<string, { sensitivity_class: number }>;
  };

  it('covers exactly the fields declared in the registry, with matching classes', () => {
    const fromRegistry = Object.fromEntries(
      Object.entries(registry.field_id).map(([k, v]) => [
        k,
        v.sensitivity_class,
      ]),
    );
    expect(SENSITIVITY_BY_FIELD).toEqual(fromRegistry);
  });

  it('stamps the same registry version the file declares', () => {
    expect(FIELD_REGISTRY_VERSION).toBe(registry.registry_version);
  });
});

describe('the backend registry copy matches the design handoff registry (drift guard)', () => {
  // The guard above only compares this module with its OWN copy, so the copy itself sat at 1.1.0
  // for two months while the handoff file moved to 1.4.15 -- religion lost `muslim` and politics
  // had `right` and `conservative` swapped, with every test green. This compares the copy with
  // the file the design team actually edits.
  const handoff = JSON.parse(
    readFileSync(
      join(
        __dirname,
        '..',
        '..',
        '..',
        '..',
        'design_handoff_showup',
        'tracking',
        'enums.json',
      ),
      'utf-8',
    ),
  ) as {
    registry_version: string;
    sets: { id: string; tables: { rows: Record<string, string>[] }[] }[];
  };
  const rows = (id: string) =>
    handoff.sets.find((s) => s.id === id)!.tables.flatMap((t) => t.rows);
  const local = JSON.parse(
    readFileSync(join(__dirname, 'enums.json'), 'utf-8'),
  ) as {
    registry_version: string;
    field_id: Record<string, { sensitivity_class: number; values: string[] }>;
    step_id: string[];
  };

  it('declares the same version', () => {
    expect(local.registry_version).toBe(handoff.registry_version);
  });

  it('every field_id has the same class and the same values, in order', () => {
    const fromHandoff = Object.fromEntries(
      rows('e1').map((r) => [
        r.field_id,
        {
          sensitivity_class: Number(r.class),
          // An em dash in the handoff means "no value set" (habits, age).
          values: r.value_bucketed
            .split('·')
            .map((v) => v.trim())
            .filter((v) => v !== '—'),
        },
      ]),
    );
    const fromLocal = Object.fromEntries(
      Object.entries(local.field_id).map(([k, v]) => [
        k,
        { sensitivity_class: v.sensitivity_class, values: v.values },
      ]),
    );
    expect(fromLocal).toEqual(fromHandoff);
  });

  it('lists the same step_ids, in order', () => {
    expect(local.step_id).toEqual(rows('e2').map((r) => r.step_id));
  });
});
