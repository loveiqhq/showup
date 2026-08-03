import { NotificationPreference } from '../entities/notification-preference.entity';
import { NotificationPreferencesService } from './notification-preferences.service';

class FakePrefsRepo {
  rows: NotificationPreference[] = [];
  create(p: Partial<NotificationPreference>): NotificationPreference {
    return { ...p } as NotificationPreference;
  }
  save(e: NotificationPreference): Promise<NotificationPreference> {
    if (!this.rows.find((r) => r.userId === e.userId)) this.rows.push(e);
    return Promise.resolve(e);
  }
  findOne({
    where,
  }: {
    where: { userId: string };
  }): Promise<NotificationPreference | null> {
    return Promise.resolve(
      this.rows.find((r) => r.userId === where.userId) ?? null,
    );
  }
}

const USER = 'user-1';

describe('NotificationPreferencesService', () => {
  let repo: FakePrefsRepo;
  const make = () => new NotificationPreferencesService(repo as any);

  beforeEach(() => {
    repo = new FakePrefsRepo();
  });

  it('returns defaults (marketing OFF) for a user with no saved row, without creating one', async () => {
    const state = await make().getState(USER);
    expect(state).toEqual({
      essential: true,
      engagement: true,
      marketing: false,
    });
    expect(repo.rows).toHaveLength(0);
  });

  it('ensure() creates a default row on first access', async () => {
    const row = await make().ensure(USER);
    expect(row.userId).toBe(USER);
    expect(row.marketing).toBe(false);
    expect(repo.rows).toHaveLength(1);
  });

  it('update() enables marketing and persists it', async () => {
    const svc = make();
    await svc.update(USER, { marketing: true });
    const state = await svc.getState(USER);
    expect(state.marketing).toBe(true);
    expect(state.essential).toBe(true);
  });

  it('update() only changes provided fields', async () => {
    const svc = make();
    await svc.update(USER, { engagement: false });
    const state = await svc.getState(USER);
    expect(state.engagement).toBe(false);
    expect(state.marketing).toBe(false);
    expect(state.essential).toBe(true);
  });
});
