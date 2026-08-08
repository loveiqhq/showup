import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Removes the profile "bio" column. The design has no bio input anywhere, so the field is dropped
 * (decided in the Aug 2026 glossary review). `down` re-adds the nullable column so the migration is
 * reversible; any previous bio text is not restored.
 */
export class RemoveProfileBio1717000013000 implements MigrationInterface {
  name = 'RemoveProfileBio1717000013000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "profiles" DROP COLUMN IF EXISTS "bio";`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "profiles" ADD COLUMN "bio" varchar(500);`,
    );
  }
}
