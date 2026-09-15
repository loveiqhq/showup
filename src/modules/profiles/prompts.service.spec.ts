import { BadRequestException } from '@nestjs/common';
import { Repository } from 'typeorm';

import { PROMPT_MAX_CHARS, PROMPTS_MAX } from './dto/prompt.dto';
import { ProfilePrompt } from './entities/profile-prompt.entity';
import { PromptsService } from './prompts.service';

/**
 * The rules a prompt has to obey, without a database (SHOWUP-158).
 *
 * `test/prompts.e2e-spec.ts` proves the same rules hold against real Postgres, which is what the
 * unique index and the column types are for. These pin the BRANCHES: what happens at the cap, what
 * counts as blank, where a new row's position comes from, and what a delete does to the ones left.
 *
 * The repository is a hand-rolled fake rather than a mocking library. It is five methods, and a
 * fake that stores rows in an array reads as what it is -- whereas a stack of `jest.fn()` returning
 * canned values would let a test pass while the service did something else entirely.
 */
describe('PromptsService', () => {
  const USER = 'user-1';
  let rows: ProfilePrompt[];
  let service: PromptsService;

  const repo = {
    find: ({ where }: { where: { userId: string } }) =>
      Promise.resolve(
        rows
          .filter((r) => r.userId === where.userId)
          .sort((a, b) => a.position - b.position),
      ),
    findOne: ({ where }: { where: { userId: string; topicId: string } }) =>
      Promise.resolve(
        rows.find(
          (r) => r.userId === where.userId && r.topicId === where.topicId,
        ) ?? null,
      ),
    count: ({ where }: { where: { userId: string } }) =>
      Promise.resolve(rows.filter((r) => r.userId === where.userId).length),
    create: (row: Partial<ProfilePrompt>) => ({ ...row }) as ProfilePrompt,
    save: (row: ProfilePrompt) => {
      const existing = rows.findIndex(
        (r) => r.userId === row.userId && r.topicId === row.topicId,
      );
      if (existing >= 0) {
        rows[existing] = { ...rows[existing], ...row };
        return Promise.resolve(rows[existing]);
      }
      const created = { id: `id-${rows.length}`, ...row };
      rows.push(created);
      return Promise.resolve(created);
    },
    delete: ({ userId, topicId }: { userId: string; topicId: string }) => {
      rows = rows.filter(
        (r) => !(r.userId === userId && r.topicId === topicId),
      );
      return Promise.resolve({ affected: 1 });
    },
  };

  beforeEach(() => {
    rows = [];
    service = new PromptsService(repo as unknown as Repository<ProfilePrompt>);
  });

  const write = (topicId: string, answer = 'Something worth reading.') =>
    service.upsert(USER, topicId, answer);

  it('stores the answer trimmed', async () => {
    const saved = await write('first_date', '  Talk about anything real.  ');
    expect(saved.answer).toBe('Talk about anything real.');
  });

  it('refuses a blank answer, including whitespace-only', async () => {
    // `MinLength(1)` on the DTO cannot see this: ' ' has length 1. Whitespace-only counts as empty
    // on both clients and it has to mean the same thing here.
    await expect(write('first_date', '   ')).rejects.toBeInstanceOf(
      BadRequestException,
    );
    await expect(write('first_date', '')).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('refuses an answer over the cap and accepts one exactly at it', async () => {
    await expect(
      write('first_date', 'x'.repeat(PROMPT_MAX_CHARS + 1)),
    ).rejects.toBeInstanceOf(BadRequestException);
    const saved = await write('first_date', 'x'.repeat(PROMPT_MAX_CHARS));
    expect(saved.answer).toHaveLength(PROMPT_MAX_CHARS);
  });

  it('is idempotent on the topic: a second save overwrites rather than adding', async () => {
    // What "Save overwrites" means on the write sheet, and what stops a double-tap writing two
    // rows for one question.
    await write('first_date', 'First answer.');
    const second = await write('first_date', 'Second answer.');
    expect(second.answer).toBe('Second answer.');
    expect(await service.count(USER)).toBe(1);
  });

  it('gives a new prompt the next position, so it lands at the bottom', async () => {
    await write('first_date');
    await write('hot_take');
    const listed = await service.list(USER);
    expect(listed.map((p) => p.topicId)).toEqual(['first_date', 'hot_take']);
    expect(listed.map((p) => p.position)).toEqual([0, 1]);
  });

  it('refuses a fourth topic', async () => {
    await write('first_date');
    await write('hot_take');
    await write('cross_town');
    await expect(write('weird_habit')).rejects.toBeInstanceOf(
      BadRequestException,
    );
    expect(await service.count(USER)).toBe(PROMPTS_MAX);
  });

  it('still allows an existing prompt to be edited at the cap', async () => {
    // Refusing this would mean a user with three prompts could never fix a typo.
    await write('first_date');
    await write('hot_take');
    await write('cross_town');
    const edited = await write('hot_take', 'A better something.');
    expect(edited.answer).toBe('A better something.');
  });

  it('closes the gap when a prompt in the middle is removed', async () => {
    await write('first_date');
    await write('hot_take');
    await write('cross_town');
    await service.remove(USER, 'hot_take');

    const listed = await service.list(USER);
    // 0, 2 would be a list whose next insert collides.
    expect(listed.map((p) => p.position)).toEqual([0, 1]);
    expect(listed.map((p) => p.topicId)).toEqual(['first_date', 'cross_town']);
  });

  it('treats removing something that is not there as success', async () => {
    await expect(
      service.remove(USER, 'never_written'),
    ).resolves.toBeUndefined();
  });

  it('never returns another account’s prompts', async () => {
    await write('first_date');
    await service.upsert('someone-else', 'hot_take', 'Not yours.');
    const listed = await service.list(USER);
    expect(listed).toHaveLength(1);
    expect(await service.count(USER)).toBe(1);
  });

  it('counts per account, so one user at the cap does not block another', async () => {
    await write('first_date');
    await write('hot_take');
    await write('cross_town');
    await expect(
      service.upsert('someone-else', 'weird_habit', 'Mine.'),
    ).resolves.toBeDefined();
  });
});
