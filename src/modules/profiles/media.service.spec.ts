import { ConfigService } from '@nestjs/config';
import { Repository } from 'typeorm';

import { MediaService } from './media.service';
import { ProfileMedia } from './entities/profile-media.entity';
import { StorageService } from './storage/storage.interface';
import {
  COLD_START_PREVIEW,
  isMediaPromptId,
  isPreviewablePromptId,
  MEDIA_PROMPTS,
  OWN_IDEA_PROMPT_ID,
} from './util/media-prompts';

/**
 * The preview rules and the section-20 registry, with no database anywhere near them.
 *
 * `preview()` depends on exactly one thing -- configuration -- so it is testable as a pure
 * decision, which is the point of keeping the ranking OUT of the client and out of the request
 * path. The ranking job itself is a separate deliverable; what is checked here is that the screen
 * is handed something sane whether or not the job has ever run.
 */
describe('MediaService.preview', () => {
  const build = (env: Record<string, string> = {}) =>
    new MediaService(
      null as unknown as Repository<ProfileMedia>,
      null as unknown as StorageService,
      { get: (key: string) => env[key] } as unknown as ConfigService,
    );

  it('falls back to the section-20 cold-start prompts when nothing is published', () => {
    // Every new install, until the ranking job has a sample.
    expect(build().preview()).toEqual({
      video: 'relaxed_and_happy',
      voice: 'relaxing_sound',
      source: 'fallback',
    });
  });

  it('serves what the ranking published', () => {
    expect(
      build({
        MEDIA_PREVIEW_VIDEO: 'made_me_smile',
        MEDIA_PREVIEW_VOICE: 'song_makes_me_move',
      }).preview(),
    ).toEqual({
      video: 'made_me_smile',
      voice: 'song_makes_me_move',
      source: 'ranked',
    });
  });

  it('reports `fallback` when only one medium has been published', () => {
    // Section 22 registers ONE source for two media. A view where one card was a fallback is not
    // one the ranking can be credited for, so the honest answer is to withhold the attribution
    // rather than overstate it. The ranked prompt is still served.
    const preview = build({ MEDIA_PREVIEW_VIDEO: 'made_me_smile' }).preview();
    expect(preview.video).toBe('made_me_smile');
    expect(preview.voice).toBe(COLD_START_PREVIEW.voice);
    expect(preview.source).toBe('fallback');
  });

  it('ignores a published id the registry does not know', () => {
    // A job that publishes a prompt we retired would otherwise put an empty card on every install.
    const preview = build({ MEDIA_PREVIEW_VIDEO: 'retired_prompt' }).preview();
    expect(preview.video).toBe(COLD_START_PREVIEW.video);
    expect(preview.source).toBe('fallback');
  });

  it('never previews own_idea', () => {
    // Storable but not suggestible: a card reading "Something else" suggests nothing.
    const preview = build({
      MEDIA_PREVIEW_VOICE: OWN_IDEA_PROMPT_ID,
    }).preview();
    expect(preview.voice).toBe(COLD_START_PREVIEW.voice);
  });

  it('ignores surrounding whitespace in a published value', () => {
    expect(
      build({ MEDIA_PREVIEW_VIDEO: '  made_me_smile  ' }).preview().video,
    ).toBe('made_me_smile');
  });
});

describe('the section-20 media prompt registry', () => {
  it('holds the eleven prompts and the escape hatch, in registry order', () => {
    // Quoted rather than derived: the order is `position` in the registry and is what the sheet
    // renders. A test that read the order back out of the list it is checking would pass on any
    // order at all.
    expect(MEDIA_PROMPTS.map((p) => p.id)).toEqual([
      'everyday_good_mood',
      'ideal_sunny_morning',
      'relaxing_sound',
      'comfort_snack',
      'friends_three_words',
      'best_weather',
      'song_makes_me_move',
      'relaxed_and_happy',
      'simple_pleasure',
      'made_me_smile',
      'easy_30_outside',
      'own_idea',
    ]);
  });

  it('puts own_idea last', () => {
    expect(MEDIA_PROMPTS[MEDIA_PROMPTS.length - 1].id).toBe(OWN_IDEA_PROMPT_ID);
  });

  it('numbers positions densely from zero', () => {
    MEDIA_PROMPTS.forEach((p, i) => expect(p.position).toBe(i));
  });

  it('accepts every registered id and rejects a display string', () => {
    for (const p of MEDIA_PROMPTS) expect(isMediaPromptId(p.id)).toBe(true);
    expect(isMediaPromptId('What my ideal sunny morning looks like')).toBe(
      false,
    );
    expect(isMediaPromptId('')).toBe(false);
  });

  it('separates storable from previewable at own_idea alone', () => {
    const storable = MEDIA_PROMPTS.filter((p) => isMediaPromptId(p.id));
    const previewable = MEDIA_PROMPTS.filter((p) =>
      isPreviewablePromptId(p.id),
    );
    expect(storable).toHaveLength(12);
    expect(previewable).toHaveLength(11);
    expect(previewable.some((p) => p.id === OWN_IDEA_PROMPT_ID)).toBe(false);
  });

  it('names a real, previewable prompt as each medium cold start', () => {
    expect(isPreviewablePromptId(COLD_START_PREVIEW.video)).toBe(true);
    expect(isPreviewablePromptId(COLD_START_PREVIEW.voice)).toBe(true);
    // Different per medium on purpose -- one you would show, one you would play -- so the two
    // empty cards never read as duplicates of each other.
    expect(COLD_START_PREVIEW.video).not.toBe(COLD_START_PREVIEW.voice);
  });
});
