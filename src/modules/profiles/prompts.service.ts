import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { PROMPT_MAX_CHARS, PROMPTS_MAX } from './dto/prompt.dto';
import { ProfilePrompt } from './entities/profile-prompt.entity';

/**
 * The three rules a prompt has to obey, enforced where they cannot be skipped (SHOWUP-158).
 *
 * The clients enforce all three as well, and that is not duplication for its own sake: the client
 * enforces them so the user is never told off after the fact, and the server enforces them because
 * a client is a thing somebody can replace. The ticket asks for the length cap "server-side as well
 * as in the client" in as many words.
 */
@Injectable()
export class PromptsService {
  constructor(
    @InjectRepository(ProfilePrompt)
    private readonly prompts: Repository<ProfilePrompt>,
  ) {}

  /** This account's prompts, in reading order. */
  list(userId: string): Promise<ProfilePrompt[]> {
    return this.prompts.find({
      where: { userId },
      order: { position: 'ASC', createdAt: 'ASC' },
    });
  }

  /**
   * Writes one prompt, creating it or overwriting the answer if the topic already has one.
   *
   * IDEMPOTENT ON topicId, which is what "Save overwrites" means on the write sheet and what stops
   * a double-tap writing two rows for one question. The unique index is what makes that true even
   * when two requests race.
   *
   * @throws BadRequestException when the answer is blank after trimming, or when the account is
   *   already at [PROMPTS_MAX] and this is a NEW topic. Editing an existing one at the cap is
   *   always allowed -- refusing it would mean a user with three prompts could never fix a typo.
   */
  async upsert(
    userId: string,
    topicId: string,
    rawAnswer: string,
  ): Promise<ProfilePrompt> {
    const answer = rawAnswer.trim();
    if (answer.length === 0) {
      // Whitespace-only counts as empty, which is what both clients do and what the group's rule
      // 2c says. `MinLength(1)` on the DTO cannot see this: " " has length 1.
      throw new BadRequestException('An answer cannot be blank.');
    }
    if (answer.length > PROMPT_MAX_CHARS) {
      // Unreachable through the DTO, and kept because this method is also the one a future import
      // or admin tool would call.
      throw new BadRequestException(
        `An answer is at most ${PROMPT_MAX_CHARS} characters.`,
      );
    }

    const existing = await this.prompts.findOne({ where: { userId, topicId } });
    if (existing) {
      existing.answer = answer;
      return this.prompts.save(existing);
    }

    const count = await this.prompts.count({ where: { userId } });
    if (count >= PROMPTS_MAX) {
      throw new BadRequestException(
        `A profile carries at most ${PROMPTS_MAX} prompts.`,
      );
    }

    // One past the last, so a new prompt lands at the BOTTOM: "the reading order stays
    // chronological". Derived from the count rather than from max(position) + 1 because the two
    // only differ after a delete, and after a delete the gap is exactly where the next one should
    // go.
    return this.prompts.save(
      this.prompts.create({ userId, topicId, answer, position: count }),
    );
  }

  /**
   * Removes one prompt and closes the gap it left.
   *
   * Renumbering rather than leaving a hole: `position` is a reading order, and a list that reads
   * 0, 2 is a list whose next insert collides. Three rows make this a trivial rewrite.
   *
   * Deleting something that is not there is not an error -- the caller wanted it gone and it is.
   */
  async remove(userId: string, topicId: string): Promise<void> {
    await this.prompts.delete({ userId, topicId });
    const remaining = await this.list(userId);
    await Promise.all(
      remaining.map((prompt, index) =>
        prompt.position === index
          ? Promise.resolve(prompt)
          : this.prompts.save({ ...prompt, position: index }),
      ),
    );
  }

  /** How many this account has. Used by the profile-progress read. */
  count(userId: string): Promise<number> {
    return this.prompts.count({ where: { userId } });
  }
}
