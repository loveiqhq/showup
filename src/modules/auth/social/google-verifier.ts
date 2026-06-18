import {
  Injectable,
  Logger,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OAuth2Client } from 'google-auth-library';

import { SocialIdentity } from './social-identity.interface';

/**
 * Verifies a Google ID token against Google's public certs, checking the audience matches one of
 * our configured client IDs. Returns the normalized identity.
 */
@Injectable()
export class GoogleVerifier {
  private readonly logger = new Logger('GoogleVerifier');
  private readonly client = new OAuth2Client();
  private readonly audiences: string[];

  constructor(config: ConfigService) {
    this.audiences = config.get<string[]>('social.googleClientIds') ?? [];
  }

  async verify(idToken: string): Promise<SocialIdentity> {
    if (this.audiences.length === 0) {
      this.logger.warn(
        'Google login attempted but GOOGLE_CLIENT_IDS is not configured',
      );
      throw new ServiceUnavailableException('Google login is not configured');
    }
    const ticket = await this.client.verifyIdToken({
      idToken,
      audience: this.audiences,
    });
    const payload = ticket.getPayload();
    if (!payload?.sub) {
      throw new UnauthorizedException('Invalid Google token');
    }
    return {
      provider: 'google',
      providerId: payload.sub,
      email: payload.email ?? null,
      emailVerified: payload.email_verified === true,
      name: payload.name ?? null,
    };
  }
}
