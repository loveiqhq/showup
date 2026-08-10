import { ConfigService } from '@nestjs/config';

import { AnalyticsService } from './analytics.service';
import type { AnalyticsContext } from './events/envelope';

function makeConfig(overrides: Record<string, unknown> = {}): ConfigService {
  const values: Record<string, unknown> = {
    'analytics.salt': 'test-salt',
    'analytics.consentDefault': false,
    ...overrides,
  };
  return { get: (key: string) => values[key] } as unknown as ConfigService;
}

const ctx = (): AnalyticsContext => ({
  source: 'server',
  anonymousId: 'anon-1',
});

describe('AnalyticsService (single gated tracking path)', () => {
  let sink: { capture: jest.Mock };
  let service: AnalyticsService;

  beforeEach(() => {
    sink = { capture: jest.fn().mockResolvedValue(undefined) };
    service = new AnalyticsService(sink, makeConfig());
  });

  it('does not send when consent is unset and the posture is opt-in (default false)', async () => {
    await service.track({ eventName: 'like_sent', context: ctx() });
    expect(sink.capture).not.toHaveBeenCalled();
  });

  it('sends when the user has explicitly consented', async () => {
    await service.track({
      eventName: 'like_sent',
      context: ctx(),
      consent: true,
    });
    expect(sink.capture).toHaveBeenCalledTimes(1);
  });

  it('keys a signed-in user by their hashed id and never sends the raw id', async () => {
    await service.track({
      eventName: 'like_sent',
      context: ctx(),
      consent: true,
      userId: 'user-9',
    });
    const arg = sink.capture.mock.calls[0][0];
    expect(arg.distinctId).not.toBe('user-9');
    expect(arg.distinctId).toMatch(/^[0-9a-f]{64}$/);
    expect(arg.properties.user_id_hashed).toBe(arg.distinctId);
    expect(JSON.stringify(arg)).not.toContain('user-9');
  });

  it('falls back to the anonymous id before sign-in', async () => {
    await service.track({
      eventName: 'app_opened',
      context: ctx(),
      consent: true,
    });
    expect(sink.capture.mock.calls[0][0].distinctId).toBe('anon-1');
  });

  it('strips prohibited fields from event properties before sending', async () => {
    await service.track({
      eventName: 'x',
      context: ctx(),
      consent: true,
      properties: { email: 'a@b.com', position: 1 },
    });
    const props = sink.capture.mock.calls[0][0].properties;
    expect(props.email).toBeUndefined();
    expect(props.position).toBe(1);
  });

  it('never throws even if the sink fails — analytics must not break the app', async () => {
    sink.capture.mockRejectedValue(new Error('network down'));
    await expect(
      service.track({ eventName: 'x', context: ctx(), consent: true }),
    ).resolves.toBeUndefined();
  });
});
