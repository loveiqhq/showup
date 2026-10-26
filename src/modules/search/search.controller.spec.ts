import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { PlanTier, User } from '../users/entities/user.entity';
import { SearchPassType } from './entities/search-pass.entity';
import { SearchController, SearchDevController } from './search.controller';
import { SearchService } from './search.service';

/**
 * The controller's own rules: the Premium surfaces refuse a Freemium account -- they show who liked
 * the viewer and what they wrote -- and every call reaches the service with the caller's id.
 */
describe('SearchController', () => {
  const service = {
    queue: jest.fn().mockResolvedValue({ resultsCount: 0 }),
    grid: jest.fn().mockResolvedValue({ resultsCount: 0, cards: [] }),
    availability: jest.fn().mockResolvedValue([]),
    pass: jest.fn().mockResolvedValue(undefined),
    hideCoach: jest.fn().mockResolvedValue(undefined),
    likesOpenCount: jest.fn().mockResolvedValue(3),
    likesReceived: jest.fn().mockResolvedValue({ openCount: 0, cards: [] }),
    setPlanTier: jest.fn().mockResolvedValue(undefined),
  };
  const controller = new SearchController(service as unknown as SearchService);
  const premium = { id: 'u-premium', planTier: PlanTier.Premium } as User;
  const freemium = { id: 'u-freemium', planTier: PlanTier.Freemium } as User;

  beforeEach(() => jest.clearAllMocks());

  it('reads the queue for anyone, in the zone sent', async () => {
    await controller.queue(freemium, 'Europe/Berlin');
    expect(service.queue).toHaveBeenCalledWith('u-freemium', 'Europe/Berlin');
  });

  it('serves the grid to Premium and refuses it to Freemium', async () => {
    await controller.grid(premium, 'Europe/Berlin');
    expect(service.grid).toHaveBeenCalledWith('u-premium', 'Europe/Berlin');
    expect(() => controller.grid(freemium)).toThrow(ForbiddenException);
    expect(service.grid).toHaveBeenCalledTimes(1);
  });

  it('wraps the re-check in statuses, Premium only', async () => {
    service.availability.mockResolvedValueOnce([
      { userId: 'a', status: 'matched' },
    ]);
    await expect(
      controller.availability(premium, { userIds: ['a'] }, 'UTC'),
    ).resolves.toEqual({ statuses: [{ userId: 'a', status: 'matched' }] });
    expect(service.availability).toHaveBeenCalledWith(
      'u-premium',
      ['a'],
      'UTC',
    );
    await expect(
      controller.availability(freemium, { userIds: ['a'] }),
    ).rejects.toThrow(ForbiddenException);
  });

  it('serves the likes list to Premium and refuses it to Freemium', async () => {
    await controller.likesReceived(premium, 'UTC');
    expect(service.likesReceived).toHaveBeenCalledWith('u-premium', 'UTC');
    expect(() => controller.likesReceived(freemium)).toThrow(
      ForbiddenException,
    );
  });

  it('counts open likes for every plan -- the badge is on every tab bar', async () => {
    await expect(controller.likesOpenCount(freemium)).resolves.toEqual({
      count: 3,
    });
  });

  it('passes and hides the coach for the caller', async () => {
    const dto = {
      targetUserId: 't',
      passType: SearchPassType.NotInterested,
    };
    await controller.pass(freemium, dto);
    expect(service.pass).toHaveBeenCalledWith('u-freemium', dto);
    await controller.hideCoach(freemium);
    expect(service.hideCoach).toHaveBeenCalledWith('u-freemium');
  });

  describe('the dev plan switch', () => {
    const dev = (env: string) =>
      new SearchDevController(
        service as unknown as SearchService,
        { get: () => env } as unknown as ConfigService,
      );

    it('switches the plan outside production', async () => {
      await dev('development').setPlanTier(freemium, {
        planTier: PlanTier.Premium,
      });
      expect(service.setPlanTier).toHaveBeenCalledWith(
        'u-freemium',
        PlanTier.Premium,
      );
    });

    it('is not an endpoint in production', async () => {
      await expect(
        dev('production').setPlanTier(freemium, { planTier: PlanTier.Premium }),
      ).rejects.toThrow(NotFoundException);
    });
  });
});
