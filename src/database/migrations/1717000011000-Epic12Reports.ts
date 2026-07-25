import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Epic 12 — User reports (SHOWUP-78). A `reports` table where a person flags another, with a reason
 * valid for its context (active date vs. review) and a status tracking progress to resolution.
 * Enums are separate from the block/moderation ones added in 1717000010000.
 */
export class Epic12Reports1717000011000 implements MigrationInterface {
  name = 'Epic12Reports1717000011000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "report_context_enum" AS ENUM ('active_date', 'date_review');`,
    );
    await queryRunner.query(
      `CREATE TYPE "report_reason_enum" AS ENUM ('felt_in_danger', 'felt_uncomfortable', 'profile_mismatch', 'showed_up_late');`,
    );
    await queryRunner.query(
      `CREATE TYPE "report_lateness_enum" AS ENUM ('late_5', 'late_10', 'late_15', 'late_15_plus');`,
    );
    await queryRunner.query(
      `CREATE TYPE "report_status_enum" AS ENUM ('submitted', 'reviewing', 'resolved', 'dismissed');`,
    );

    await queryRunner.query(`
      CREATE TABLE "reports" (
        "id"                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "reporter_id"         uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "reported_user_id"    uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "context"             "report_context_enum" NOT NULL,
        "reason"              "report_reason_enum" NOT NULL,
        "lateness"            "report_lateness_enum",
        "date_id"             uuid,
        "note"                varchar(1000),
        "status"              "report_status_enum" NOT NULL DEFAULT 'submitted',
        "handled_by_user_id"  uuid REFERENCES "users"("id") ON DELETE SET NULL,
        "resolution_note"     varchar(1000),
        "resolved_at"         timestamptz,
        "created_at"          timestamptz NOT NULL DEFAULT now(),
        "updated_at"          timestamptz NOT NULL DEFAULT now()
      );
    `);
    await queryRunner.query(
      `CREATE INDEX "idx_reports_reported" ON "reports" ("reported_user_id");`,
    );
    await queryRunner.query(
      `CREATE INDEX "idx_reports_status" ON "reports" ("status");`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "reports";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "report_status_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "report_lateness_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "report_reason_enum";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "report_context_enum";`);
  }
}
