/**
 * The media prompt registry -- `enums.json` section 20, mirrored server-side.
 *
 * WHY THE SERVER HOLDS THESE AT ALL. The clients render the display strings and could own the
 * whole list; the server needs the IDS because it validates what a recording claims to answer and
 * because the preview ranking is published against them. Storing the display string instead was
 * explicitly ruled out (SHOWUP-161): the prompts are copy and will be edited, and an edit must not
 * orphan the clips recorded under them.
 *
 * `own_idea` is the escape hatch rather than a prompt. It is accepted on an upload and is the only
 * id that carries `isOwnPrompt`, but it never ranks -- a preview that suggested "something else"
 * would be suggesting nothing.
 *
 * Order is the registry's `position` and is fixed. The list a user sees never reorders; only the
 * previewed prompt moves, and it moves because the ranking published a new one.
 */

export type MediaKind = 'video' | 'voice';

export interface MediaPrompt {
  readonly id: string;
  readonly position: number;
  readonly display: string;
}

/** Section 20, in registry order. The eleven, then the escape hatch. */
export const MEDIA_PROMPTS: readonly MediaPrompt[] = [
  {
    id: 'everyday_good_mood',
    position: 0,
    display: 'The little everyday thing that instantly puts me in a good mood',
  },
  {
    id: 'ideal_sunny_morning',
    position: 1,
    display: 'What my ideal sunny morning looks like',
  },
  {
    id: 'relaxing_sound',
    position: 2,
    display: 'A sound that always makes me feel relaxed',
  },
  {
    id: 'comfort_snack',
    position: 3,
    display: 'My go-to comfort snack when having a good day',
  },
  {
    id: 'friends_three_words',
    position: 4,
    display: 'How my friends would describe my energy in three words',
  },
  {
    id: 'best_weather',
    position: 5,
    display: 'The kind of weather that brings out the best in me',
  },
  {
    id: 'song_makes_me_move',
    position: 6,
    display: 'A song that always makes me want to move',
  },
  {
    id: 'relaxed_and_happy',
    position: 7,
    display: "What I usually look like when I'm relaxed and happy",
  },
  {
    id: 'simple_pleasure',
    position: 8,
    display: 'The best simple pleasure in my daily routine',
  },
  {
    id: 'made_me_smile',
    position: 9,
    display: 'Something cute or funny that made me smile this week',
  },
  {
    id: 'easy_30_outside',
    position: 10,
    display: 'My favorite way to spend an easy 30 minutes outside',
  },
  { id: 'own_idea', position: 11, display: 'Something else — my own idea' },
] as const;

/** The escape hatch's id. Accepted, stored, never ranked. */
export const OWN_IDEA_PROMPT_ID = 'own_idea';

/**
 * The cold-start preview per medium -- `cold_start_preview` in section 20.
 *
 * Used only when the ranking has published nothing for that medium, which is every install until
 * the job has a sample. One you would show and one you would play, which is why they differ.
 */
export const COLD_START_PREVIEW: Readonly<Record<MediaKind, string>> = {
  video: 'relaxed_and_happy',
  voice: 'relaxing_sound',
};

const BY_ID = new Map(MEDIA_PROMPTS.map((p) => [p.id, p]));

/** Whether an id is in the registry. The only accepted spelling of "is this a real prompt". */
export function isMediaPromptId(id: string): boolean {
  return BY_ID.has(id);
}

/**
 * Whether an id may be PREVIEWED on an empty card.
 *
 * Narrower than `isMediaPromptId` on purpose: `own_idea` is storable but not suggestible, and a
 * ranking job that published it would put "Something else" on the card as the thing to answer.
 */
export function isPreviewablePromptId(id: string): boolean {
  return BY_ID.has(id) && id !== OWN_IDEA_PROMPT_ID;
}

export function mediaPrompt(id: string): MediaPrompt | undefined {
  return BY_ID.get(id);
}
