import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Removes the unused account status `verified` from `users_status_enum`.
 *
 * Identity verification lives on the profile (`profiles.verification_status` — the selfie badge).
 * The account-level copy duplicated that concept, could never coexist with `active` (a status column
 * holds one value), and was never set or checked by any code, so it is dropped.
 *
 * Postgres cannot remove a value from an existing enum type, so the type is recreated without it and
 * the column is re-pointed at the new type. Any row still holding 'verified' is moved to 'active'
 * first — none are expected, since nothing ever set it, but the migration must not fail if one exists.
 */
export class RemoveUserStatusVerified1717000015000 implements MigrationInterface {
  name = 'RemoveUserStatusVerified1717000015000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `UPDATE "users" SET "status" = 'active' WHERE "status" = 'verified';`,
    );

    await queryRunner.query(
      `ALTER TYPE "users_status_enum" RENAME TO "users_status_enum_old";`,
    );
    await queryRunner.query(`
      CREATE TYPE "users_status_enum" AS ENUM (
        'registered','active','restricted','suspended','deletion_pending','deleted'
      );
    `);
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" DROP DEFAULT;`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" TYPE "users_status_enum" USING "status"::text::"users_status_enum";`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" SET DEFAULT 'registered';`,
    );
    await queryRunner.query(`DROP TYPE "users_status_enum_old";`);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TYPE "users_status_enum" RENAME TO "users_status_enum_old";`,
    );
    await queryRunner.query(`
      CREATE TYPE "users_status_enum" AS ENUM (
        'registered','verified','active','restricted','suspended','deletion_pending','deleted'
      );
    `);
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" DROP DEFAULT;`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" TYPE "users_status_enum" USING "status"::text::"users_status_enum";`,
    );
    await queryRunner.query(
      `ALTER TABLE "users" ALTER COLUMN "status" SET DEFAULT 'registered';`,
    );
    await queryRunner.query(`DROP TYPE "users_status_enum_old";`);
  }
}
