import { BadRequestException } from '@nestjs/common';
import { Repository } from 'typeorm';

import {
  DevicePermission,
  DevicePermissionStatus,
  PermissionStatusEntry,
} from '../entities/permission-status.entity';
import { PushPlatform } from '../entities/push-token.entity';
import { PermissionStatusesService } from './permission-statuses.service';

/**
 * The phone's permission statuses on the server, without a database (epic SHOWUP-151).
 *
 * `test/permission-statuses.e2e-spec.ts` runs the same rules against Postgres, where the primary key
 * and the enum types do the work. These pin the branches: the newest report wins per permission, a
 * permission sent twice is refused, and only an explicit refusal counts as notifications being off.
 *
 * The repository is a hand-rolled fake over an array (as in `prompts.service.spec.ts`): an upsert
 * keyed the way the table is keyed, so a change to the key would show here.
 */
describe('PermissionStatusesService', () => {
  const USER = 'user-1';
  let rows: PermissionStatusEntry[];
  let service: PermissionStatusesService;

  const repo = {
    upsert: (values: Partial<PermissionStatusEntry>[], keys: string[]) => {
      expect(keys).toEqual(['userId', 'permission']);
      for (const v of values) {
        const at = rows.findIndex(
          (r) => r.userId === v.userId && r.permission === v.permission,
        );
        const row = { ...v, updatedAt: new Date() } as PermissionStatusEntry;
        if (at >= 0) rows[at] = row;
        else rows.push(row);
      }
      return Promise.resolve();
    },
    find: ({ where }: { where: { userId: string } }) =>
      Promise.resolve(
        rows
          .filter((r) => r.userId === where.userId)
          .sort((a, b) => a.permission.localeCompare(b.permission)),
      ),
    findOne: ({
      where,
    }: {
      where: { userId: string; permission: DevicePermission };
    }) =>
      Promise.resolve(
        rows.find(
          (r) => r.userId === where.userId && r.permission === where.permission,
        ) ?? null,
      ),
  };

  beforeEach(() => {
    rows = [];
    service = new PermissionStatusesService(
      repo as unknown as Repository<PermissionStatusEntry>,
    );
  });

  const save = (
    statuses: {
      permission: DevicePermission;
      status: DevicePermissionStatus;
    }[],
    platform = PushPlatform.Android,
  ) => service.save(USER, { platform, statuses });

  it('stores each reported permission, and the newest report wins', async () => {
    await save([
      {
        permission: DevicePermission.Notifications,
        status: DevicePermissionStatus.Granted,
      },
      {
        permission: DevicePermission.Location,
        status: DevicePermissionStatus.NotDetermined,
      },
    ]);
    await save(
      [
        {
          permission: DevicePermission.Notifications,
          status: DevicePermissionStatus.Denied,
        },
      ],
      PushPlatform.Ios,
    );
    const stored = await service.list(USER);
    expect(stored.map((r) => [r.permission, r.status, r.platform])).toEqual([
      [
        DevicePermission.Location,
        DevicePermissionStatus.NotDetermined,
        PushPlatform.Android,
      ],
      [
        DevicePermission.Notifications,
        DevicePermissionStatus.Denied,
        PushPlatform.Ios,
      ],
    ]);
  });

  it('refuses a report that names the same permission twice, and stores nothing', async () => {
    await expect(
      save([
        {
          permission: DevicePermission.Camera,
          status: DevicePermissionStatus.Granted,
        },
        {
          permission: DevicePermission.Camera,
          status: DevicePermissionStatus.Denied,
        },
      ]),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(rows).toHaveLength(0);
  });

  it('counts denied and restricted as notifications off', async () => {
    await save([
      {
        permission: DevicePermission.Notifications,
        status: DevicePermissionStatus.Denied,
      },
    ]);
    expect(await service.notificationsBlocked(USER)).toBe(true);
    await save([
      {
        permission: DevicePermission.Notifications,
        status: DevicePermissionStatus.Restricted,
      },
    ]);
    expect(await service.notificationsBlocked(USER)).toBe(true);
  });

  it('does not count granted, not determined, or no report at all as off', async () => {
    expect(await service.notificationsBlocked(USER)).toBe(false);
    await save([
      {
        permission: DevicePermission.Notifications,
        status: DevicePermissionStatus.NotDetermined,
      },
    ]);
    expect(await service.notificationsBlocked(USER)).toBe(false);
    await save([
      {
        permission: DevicePermission.Notifications,
        status: DevicePermissionStatus.Granted,
      },
    ]);
    expect(await service.notificationsBlocked(USER)).toBe(false);
  });

  it('reads notifications only: another permission being off does not block pushes', async () => {
    await save([
      {
        permission: DevicePermission.Location,
        status: DevicePermissionStatus.Denied,
      },
      {
        permission: DevicePermission.Camera,
        status: DevicePermissionStatus.Denied,
      },
    ]);
    expect(await service.notificationsBlocked(USER)).toBe(false);
  });
});
