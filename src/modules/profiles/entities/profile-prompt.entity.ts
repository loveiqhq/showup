import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

/**
 * One written prompt on a profile: a topic and the user's answer to it (SHOWUP-158).
 *
 * Up to three per account, each at most 160 characters. Both limits are enforced in
 * `PromptsService` as well as in the client, because the ticket asks for the cap "server-side as
 * well as in the client" and a client-only limit is a suggestion.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY topic_id IS A STRING AND IS NOT VALIDATED AGAINST A LIST
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The fifteen topics are DESIGN CONTENT. They live in `PromptTopics.kt` / `PromptTopics.swift`,
 * copied from the handoff's reference file, and they change when the copy changes. A server-side
 * allow-list would be a second copy of that content, and adding or rewording a topic would need a
 * backend deploy to go with the app release — which is how a copy edit turns into a coordination
 * problem.
 *
 * What the server does enforce is everything that protects the row: the length, the count, and the
 * uniqueness below. An unrecognised id can only ever come from our own client, and the worst it
 * produces is a prompt the app cannot render a question for — which it handles by falling back to
 * the id (`topicText`).
 *
 * THE ANSWER IS USER-WRITTEN TEXT THAT WILL BE SHOWN TO OTHER PEOPLE, and nothing here moderates
 * it. SHOWUP-158's "out of scope" says so explicitly and its open questions flag it: no profanity
 * or moderation pass exists for this field. It has to exist before prompts are shown to a match.
 */
@Entity('profile_prompts')
// One prompt per topic per user. The screen already guarantees it -- a used topic renders disabled
// in the picker -- but the guarantee belongs here too: it is what makes PUT idempotent, which is
// what "Save overwrites" on the write sheet actually means.
@Unique('profile_prompts_user_topic_unique', ['userId', 'topicId'])
export class ProfilePrompt {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  /** A stable id from the app's topic list, never the question's display text. */
  @Column({ name: 'topic_id', type: 'varchar', length: 64 })
  topicId: string;

  /**
   * 160 graphemes is the product cap and 1,000 is the column.
   *
   * The column matches the DTO's wall (`PROMPT_MAX_UNITS`), not the cap: varchar counts code
   * points, and 160 graphemes can be many more of those -- a flag is two. Sized to the wall, a
   * request that passes validation can never become a database error instead
   * (`1717000031000-PromptAnswerGraphemes`).
   */
  @Column({ type: 'varchar', length: 1000 })
  answer: string;

  /**
   * Reading order on the profile, from 0.
   *
   * Assigned by the service as "one past the last" so a new prompt lands at the BOTTOM of the
   * list, which is the order the write sheet promises: "a new card appears at the bottom, not the
   * top -- the reading order stays chronological".
   */
  @Column({ type: 'int', default: 0 })
  position: number;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
