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
   * 160 is the product cap and 200 is the column.
   *
   * The extra 40 is deliberate: a `varchar(160)` would make a request one character over the limit
   * a database error rather than a validation error, and the two produce very different responses.
   * The DTO rejects at 160 long before this is reached.
   */
  @Column({ type: 'varchar', length: 200 })
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
