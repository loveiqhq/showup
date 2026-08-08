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
      Object.entries(registry.field_id).map(([k, v]) => [k, v.sensitivity_class]),
    );
    expect(SENSITIVITY_BY_FIELD).toEqual(fromRegistry);
  });

  it('stamps the same registry version the file declares', () => {
    expect(FIELD_REGISTRY_VERSION).toBe(registry.registry_version);
  });
});
