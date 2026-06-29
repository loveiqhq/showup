import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 4 — Check-In & Availability schema:
 *  - check_ins (volatile availability windows, kept separate from the profile)
 *  - status enum + indexes for the "active now" query and the expiry sweep
 */
export class Epic4CheckIns1717000005000 implements MigrationInterface {
  name = 'Epic4CheckIns1717000005000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "check_ins_status_enum" AS ENUM ('available', 'cancelled', 'expired');`,
    );
    await queryRunner.query(`
      CREATE TABLE "check_ins" (
        "id"                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "user_id"            uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "status"             "check_ins_status_enum" NOT NULL DEFAULT 'available',
        "availability_start" timestamptz NOT NULL,
        "availability_end"   timestamptz NOT NULL,
        "created_at"         timestamptz NOT NULL DEFAULT now(),
        "updated_at"         timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_check_ins_user_id" ON "check_ins" ("user_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_check_ins_status_end" ON "check_ins" ("status", "availability_end");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "check_ins";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "check_ins_status_enum";`);
  }
}
