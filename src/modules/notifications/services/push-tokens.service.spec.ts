import { PushPlatform, PushToken } from '../entities/push-token.entity';
import { PushTokensService } from './push-tokens.service';

class FakeTokenRepo {
  rows: PushToken[] = [];
  private seq = 1;
  create(p: Partial<PushToken>): PushToken {
    return { ...p } as PushToken;
  }
  save(e: PushToken): Promise<PushToken> {
    if (!e.id) {
      e.id = `t${this.seq++}`;
      this.rows.push(e);
    }
    return Promise.resolve(e);
  }
  findOne({ where }: { where: { token: string } }): Promise<PushToken | null> {
    return Promise.resolve(
      this.rows.find((r) => r.token === where.token) ?? null,
    );
  }
  find({ where }: { where: { userId: string } }): Promise<PushToken[]> {
    return Promise.resolve(this.rows.filter((r) => r.userId === where.userId));
  }
  delete(criteria: any): Promise<{ affected: number }> {
    const before = this.rows.length;
    const t = criteria.token;
    if (t && typeof t === 'object' && Array.isArray(t.value)) {
      // TypeORM In(tokens) → FindOperator exposing `.value`.
      const set = new Set(t.value as string[]);
      this.rows = this.rows.filter((r) => !set.has(r.token));
    } else if (typeof t === 'string') {
      this.rows = this.rows.filter((r) => r.token !== t);
    }
    return Promise.resolve({ affected: before - this.rows.length });
  }
}

const USER = 'user-1';
const OTHER = 'user-2';

describe('PushTokensService', () => {
  let repo: FakeTokenRepo;
  const make = () => new PushTokensService(repo as any);

  beforeEach(() => {
    repo = new FakeTokenRepo();
  });

  it('registers a new token for a user', async () => {
    await make().register(USER, { token: 'abc', platform: PushPlatform.Ios });
    expect(repo.rows).toHaveLength(1);
    expect(repo.rows[0]).toMatchObject({ userId: USER, token: 'abc' });
  });

  it('does not duplicate when the same token is submitted again (upsert)', async () => {
    const svc = make();
    await svc.register(USER, { token: 'abc', platform: PushPlatform.Ios });
    await svc.register(USER, { token: 'abc', platform: PushPlatform.Ios });
    expect(repo.rows).toHaveLength(1);
  });

  it('re-points a token to the latest user that registered it', async () => {
    const svc = make();
    await svc.register(USER, { token: 'abc', platform: PushPlatform.Android });
    await svc.register(OTHER, { token: 'abc', platform: PushPlatform.Ios });
    expect(repo.rows).toHaveLength(1);
    expect(repo.rows[0].userId).toBe(OTHER);
    expect(repo.rows[0].platform).toBe(PushPlatform.Ios);
  });

  it('deregisters a token so it stops receiving alerts', async () => {
    const svc = make();
    await svc.register(USER, { token: 'abc', platform: PushPlatform.Ios });
    await svc.deregister(USER, 'abc');
    expect(repo.rows).toHaveLength(0);
  });

  it('deregister is idempotent for an unknown token', async () => {
    await expect(make().deregister(USER, 'nope')).resolves.toBeUndefined();
  });

  it('lists only the given user’s tokens', async () => {
    const svc = make();
    await svc.register(USER, { token: 'a', platform: PushPlatform.Ios });
    await svc.register(OTHER, { token: 'b', platform: PushPlatform.Ios });
    const mine = await svc.listForUser(USER);
    expect(mine.map((t) => t.token)).toEqual(['a']);
  });

  it('prunes a set of dead tokens', async () => {
    const svc = make();
    await svc.register(USER, { token: 'a', platform: PushPlatform.Ios });
    await svc.register(USER, { token: 'b', platform: PushPlatform.Ios });
    await svc.prune(['a']);
    expect(repo.rows.map((t) => t.token)).toEqual(['b']);
  });
});
