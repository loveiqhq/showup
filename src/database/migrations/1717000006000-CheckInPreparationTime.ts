import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Adds the user's "time to get ready" (preparation) to a check-in. Used, together with travel
 * time, to work out whether a date is actually reachable inside the availability window.
 * Additive and safe: existing rows default to 30 minutes.
 */
export class CheckInPreparationTime1717000006000 implements MigrationInterface {
  name = 'CheckInPreparationTime1717000006000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "check_ins" ADD COLUMN "preparation_minutes" smallint NOT NULL DEFAULT 30;`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "check_ins" DROP COLUMN IF EXISTS "preparation_minutes";`,
    );
  }
}
