import {
  HIDEABLE_FIELDS,
  isHideableField,
  normaliseHiddenFields,
  unknownHiddenFields,
} from './hidden-fields';

describe('hidden-fields', () => {
  describe('the vocabulary', () => {
    it('accepts age, the control SHOWUP-154 ships', () => {
      expect(isHideableField('age')).toBe(true);
    });

    it('rejects a field whose control does not exist yet', () => {
      // Named in the registry, but its screen is unbuilt. Accepting it would let a client store a
      // preference nothing can honour.
      expect(isHideableField('height')).toBe(false);
    });

    it('rejects isVisible, which is not a field_id at all', () => {
      // The whole point of this module: discovery visibility is a different concept and must not
      // be reachable through this set.
      expect(isHideableField('isVisible')).toBe(false);
      expect(isHideableField('is_visible')).toBe(false);
    });

    it('has no duplicates', () => {
      expect(new Set(HIDEABLE_FIELDS).size).toBe(HIDEABLE_FIELDS.length);
    });
  });

  describe('normalising a submitted set', () => {
    it('de-duplicates', () => {
      expect(normaliseHiddenFields(['age', 'age'])).toEqual(['age']);
    });

    it('sorts, so the same choices always produce the same row', () => {
      // Guards against a no-op UPDATE bumping updated_at because two clients ordered the set
      // differently.
      expect(normaliseHiddenFields(['b', 'a'])).toEqual(['a', 'b']);
    });

    it('turns an empty submission into an empty set, not null', () => {
      expect(normaliseHiddenFields([])).toEqual([]);
    });
  });

  describe('reporting unknown values', () => {
    it('names every unrecognised value once, sorted', () => {
      expect(unknownHiddenFields(['age', 'nope', 'zzz', 'nope'])).toEqual([
        'nope',
        'zzz',
      ]);
    });

    it('is empty for a fully valid set', () => {
      expect(unknownHiddenFields(['age'])).toEqual([]);
    });
  });
});
