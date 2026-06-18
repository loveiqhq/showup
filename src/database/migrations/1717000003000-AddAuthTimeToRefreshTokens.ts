import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Adds `auth_time` to refresh tokens — the moment the user last truly authenticated (phone OTP or
 * social). It is preserved across token rotation (refreshing is not re-authentication) and is used
 * to enforce step-up re-verification on sensitive actions.
 */
export class AddAuthTimeToRefreshTokens1717000003000 implements MigrationInterface {
  name = 'AddAuthTimeToRefreshTokens1717000003000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "refresh_tokens" ADD COLUMN "auth_time" timestamptz NOT NULL DEFAULT now();`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "refresh_tokens" DROP COLUMN "auth_time";`,
    );
  }
}
