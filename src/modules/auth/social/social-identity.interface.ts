/** Normalized identity returned by a social-login verifier (Apple/Google). */
export interface SocialIdentity {
  provider: 'apple' | 'google';
  /** Stable provider-specific user id (the token `sub`). */
  providerId: string;
  email: string | null;
  emailVerified: boolean;
  name: string | null;
}
