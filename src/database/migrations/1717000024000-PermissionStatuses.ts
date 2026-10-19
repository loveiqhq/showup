import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * The phone's permission statuses, as the apps last reported them (epic SHOWUP-151, SHOWUP-163).
 *
 * The epic's shared permission reconciler: "On launch and on every foreground ... read the status
 * -> if it differs from the last value synced, update the server, register or invalidate the
 * token". The apps now send what they read; this is where it lands. The push path reads it, so a
 * phone whose notifications are off is not sent pushes it would never show.
 *
 * ONE ROW PER USER AND PERMISSION. The newest report wins, from whichever phone sent it. The
 * platform reuses the push tokens' own enum: the same two values, one vocabulary.
 *
 * Statuses are `enums.json` §24 `permission_status`; permissions are the §8 types the MVP asks for.
 */
export class PermissionStatuses1717000024000 implements MigrationInterface {
  name = 'PermissionStatuses1717000024000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "permission_statuses_permission_enum" AS ENUM ('notifications', 'location', 'camera', 'microphone');`,
    );
    await queryRunner.query(
      `CREATE TYPE "permission_statuses_status_enum" AS ENUM ('not_determined', 'granted', 'denied', 'restricted');`,
    );
    await queryRunner.query(`
      CREATE TABLE "permission_statuses" (
        "user_id"    uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "permission" "permission_statuses_permission_enum" NOT NULL,
        "status"     "permission_statuses_status_enum" NOT NULL,
        "platform"   "push_tokens_platform_enum" NOT NULL,
        "updated_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "pk_permission_statuses" PRIMARY KEY ("user_id", "permission")
      );
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "permission_statuses";`);
    await queryRunner.query(
      `DROP TYPE IF EXISTS "permission_statuses_status_enum";`,
    );
    await queryRunner.query(
      `DROP TYPE IF EXISTS "permission_statuses_permission_enum";`,
    );
  }
}
