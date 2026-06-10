import { createPublicKey, type JsonWebKey as NodeJsonWebKey } from 'crypto';

import {
  Injectable,
  Logger,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';

import { SocialIdentity } from './social-identity.interface';

interface AppleJwk {
  kid: string;
  kty: string;
  n: string;
  e: string;
}

interface AppleClaims {
  sub?: string;
  email?: string;
  email_verified?: boolean | string;
}

/**
 * Verifies a Sign in with Apple identity token (RS256 JWT) against Apple's public JWKS, checking
 * the signature, issuer, and that the audience matches one of our configured app/service IDs.
 * Keys are fetched from Apple and cached by `kid`. Uses only Node crypto + jsonwebtoken (no ESM deps).
 */
@Injectable()
export class AppleVerifier {
  private readonly logger = new Logger('AppleVerifier');
  private readonly jwksUri = 'https://appleid.apple.com/auth/keys';
  private readonly audiences: string[];
  private readonly issuer: string;
  private readonly pemByKid = new Map<string, string>();

  constructor(config: ConfigService) {
    this.audiences = config.get<string[]>('social.appleClientIds') ?? [];
    this.issuer =
      config.get<string>('social.appleIssuer') ?? 'https://appleid.apple.com';
  }

  private async pemForKid(kid: string): Promise<string> {
    if (!this.pemByKid.has(kid)) {
      const res = await fetch(this.jwksUri);
      if (!res.ok) {
        throw new ServiceUnavailableException('Could not fetch Apple keys');
      }
      const body = (await res.json()) as { keys: AppleJwk[] };
      for (const jwk of body.keys) {
        const pem = createPublicKey({
          key: jwk as unknown as NodeJsonWebKey,
          format: 'jwk',
        }).export({ type: 'spki', format: 'pem' }) as string;
        this.pemByKid.set(jwk.kid, pem);
      }
    }
    const pem = this.pemByKid.get(kid);
    if (!pem) throw new UnauthorizedException('Unknown Apple signing key');
    return pem;
  }

  async verify(identityToken: string): Promise<SocialIdentity> {
    if (this.audiences.length === 0) {
      this.logger.warn(
        'Apple login attempted but APPLE_CLIENT_IDS is not configured',
      );
      throw new ServiceUnavailableException('Apple login is not configured');
    }

    const decoded = jwt.decode(identityToken, { complete: true });
    const kid = decoded?.header.kid;
    if (!kid) throw new UnauthorizedException('Invalid Apple token');

    const pem = await this.pemForKid(kid);

    let claims: AppleClaims;
    try {
      claims = jwt.verify(identityToken, pem, {
        algorithms: ['RS256'],
        issuer: this.issuer,
        // Non-empty (guarded above); cast to satisfy jsonwebtoken's tuple typing.
        audience: this.audiences as [string, ...string[]],
      }) as AppleClaims;
    } catch {
      throw new UnauthorizedException('Invalid Apple token');
    }

    if (!claims.sub) throw new UnauthorizedException('Invalid Apple token');
    return {
      provider: 'apple',
      providerId: claims.sub,
      email: typeof claims.email === 'string' ? claims.email : null,
      // Apple sends email_verified as a boolean or the string "true".
      emailVerified:
        claims.email_verified === true || claims.email_verified === 'true',
      name: null,
    };
  }
}
