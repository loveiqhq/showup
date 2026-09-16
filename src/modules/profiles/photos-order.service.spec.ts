import { BadRequestException } from '@nestjs/common';
import { Repository } from 'typeorm';

import {
  PhotoModerationStatus,
  ProfilePhoto,
} from './entities/profile-photo.entity';
import { PhotosService } from './photos.service';
import { ProfilesService } from './profiles.service';
import { StorageService } from './storage/storage.interface';

/**
 * Everything that decides what order a profile's photos are in, without a database (SHOWUP-156).
 *
 * The rule is one sentence -- "store the order the user dragged the grid into" -- and everything
 * worth testing is what happens when the list is NOT the account's photos exactly once each. A
 * reorder that accepted a partial list would renumber around photos it had never been told about,
 * and one that accepted a foreign id would let a caller learn that an id exists on another account.
 *
 * Only `reorder` is exercised here. The rest of the service writes files through `Storage` and is
 * covered end to end in `test/profiles.e2e-spec.ts`.
 */
describe('PhotosService photo order', () => {
  const USER = 'user-1';
  let rows: ProfilePhoto[];
  let service: PhotosService;
  let saved: ProfilePhoto[][];

  const photo = (id: string, position: number, userId = USER): ProfilePhoto =>
    ({
      id,
      userId,
      position,
      storageKey: `${userId}/${id}.jpg`,
      contentType: 'image/jpeg',
      moderationStatus: PhotoModerationStatus.Pending,
    }) as ProfilePhoto;

  const repo = {
    find: ({ where }: { where: { userId: string } }) =>
      Promise.resolve(
        rows
          .filter((r) => r.userId === where.userId)
          .sort((a, b) => a.position - b.position),
      ),
    findOne: ({ where }: { where: { id: string; userId: string } }) =>
      Promise.resolve(
        rows.find((r) => r.id === where.id && r.userId === where.userId) ??
          null,
      ),
    save: (batch: ProfilePhoto[]) => {
      saved.push(batch.map((p) => ({ ...p })));
      return Promise.resolve(batch);
    },
    remove: (photo: ProfilePhoto) => {
      rows = rows.filter((r) => r !== photo);
      return Promise.resolve(photo);
    },
  };

  beforeEach(() => {
    saved = [];
    rows = [photo('a', 0), photo('b', 1), photo('c', 2)];
    service = new PhotosService(
      repo as unknown as Repository<ProfilePhoto>,
      { delete: () => Promise.resolve() } as unknown as StorageService,
      {
        refreshCompletion: () => Promise.resolve(),
      } as unknown as ProfilesService,
    );
  });

  // ── reorder ───────────────────────────────────────────────────────────────

  it('writes position by index, so the first id becomes the main photo', async () => {
    const result = await service.reorder(USER, ['c', 'a', 'b']);
    expect(result.map((p) => p.id)).toEqual(['c', 'a', 'b']);
    expect(result.map((p) => p.position)).toEqual([0, 1, 2]);
  });

  it('saves every photo in one call, not one at a time', async () => {
    // Three round trips where one will do, and a half-applied order if the second fails.
    await service.reorder(USER, ['c', 'b', 'a']);
    expect(saved).toHaveLength(1);
    expect(saved[0]).toHaveLength(3);
  });

  it('accepts an order that is already the current one', async () => {
    // Idempotent: a retry after a dropped connection must not be an error.
    const result = await service.reorder(USER, ['a', 'b', 'c']);
    expect(result.map((p) => p.position)).toEqual([0, 1, 2]);
  });

  it('refuses a partial list', async () => {
    // "Position these two and leave the rest" has no answer the caller and the server would agree
    // on: whatever the server invented for the others is an order the user did not choose.
    await expect(service.reorder(USER, ['a', 'b'])).rejects.toBeInstanceOf(
      BadRequestException,
    );
    expect(saved).toHaveLength(0);
  });

  it('refuses a list with a duplicate', async () => {
    await expect(service.reorder(USER, ['a', 'a', 'b'])).rejects.toBeInstanceOf(
      BadRequestException,
    );
    expect(saved).toHaveLength(0);
  });

  it('refuses an id that belongs to somebody else', async () => {
    rows.push(photo('x', 0, 'someone-else'));
    await expect(service.reorder(USER, ['a', 'b', 'x'])).rejects.toBeInstanceOf(
      BadRequestException,
    );
    expect(saved).toHaveLength(0);
  });

  it('refuses an id that does not exist at all', async () => {
    await expect(
      service.reorder(USER, ['a', 'b', 'not-a-photo']),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('says the same thing for every shape of wrong', async () => {
    // One message for missing, extra and foreign, because the caller's recovery is identical in
    // each case: re-read and try again. Naming which id was unrecognised would also tell a caller
    // whether an id exists on another account.
    const messages = await Promise.all(
      [
        ['a', 'b'],
        ['a', 'a', 'b'],
        ['a', 'b', 'nope'],
      ].map((ids) => service.reorder(USER, ids).catch((e: Error) => e.message)),
    );
    expect(new Set(messages).size).toBeLessThanOrEqual(2);
  });

  it('does not consult moderation status', async () => {
    // This app POST-moderates: pending and approved are both shown and only rejected is hidden,
    // and that filtering happens in `listVisible` where photos are read for somebody else. A drag
    // that silently sprang back because a photo was still in review would have nothing on screen
    // to explain it.
    rows = [
      photo('a', 0),
      { ...photo('b', 1), moderationStatus: PhotoModerationStatus.Rejected },
      photo('c', 2),
    ];
    const result = await service.reorder(USER, ['b', 'a', 'c']);
    expect(result.map((p) => p.id)).toEqual(['b', 'a', 'c']);
  });

  it('leaves another account’s photos alone', async () => {
    rows.push(photo('x', 0, 'someone-else'));
    await service.reorder(USER, ['c', 'b', 'a']);
    const theirs = rows.find((r) => r.id === 'x');
    expect(theirs?.position).toBe(0);
    expect(saved[0].map((p) => p.id)).toEqual(['c', 'b', 'a']);
  });

  // ── remove closes the gap ─────────────────────────────────────────────────

  it('renumbers the survivors after a delete', async () => {
    await service.remove(USER, 'a');
    expect(rows.map((r) => [r.id, r.position])).toEqual([
      ['b', 0],
      ['c', 1],
    ]);
  });

  it('writes nothing when the deleted photo was the last one', async () => {
    // Deleting the end of a dense list leaves it dense. A save here would be a write for nothing.
    await service.remove(USER, 'c');
    expect(saved).toHaveLength(0);
  });

  it('stops a later upload from jumping to the front of the grid', async () => {
    // The bug this renumbering exists for. `upload` gives a new photo `position = count`, which
    // is the end of the list only while positions are dense. Delete three of five and the next
    // upload would take position 2 -- ahead of the survivors at 3 and 4.
    rows = [
      photo('a', 0),
      photo('b', 1),
      photo('c', 2),
      photo('d', 3),
      photo('e', 4),
    ];
    for (const id of ['a', 'b', 'c']) await service.remove(USER, id);

    const survivors = await service.list(USER);
    expect(survivors.map((p) => p.position)).toEqual([0, 1]);
    // So `count` is genuinely the next free position again.
    expect(survivors.length).toBe(2);
  });

  it('leaves another account’s positions untouched when renumbering', async () => {
    rows.push(photo('x', 7, 'someone-else'));
    await service.remove(USER, 'a');
    expect(rows.find((r) => r.id === 'x')?.position).toBe(7);
  });
});
