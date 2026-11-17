import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 05, change the meet time and place (SHOWUP-196).
 *
 * `date_change_suggestions`: one row per suggestion of a new time or a new place. The place is a
 * snapshot (id, name, type, address, point) so the evidence survives whatever Google later returns;
 * `was_*` is what the suggestion would replace, `deadline` is meet - 0:30 at the moment it was made.
 * At most one PENDING suggestion per kind per date (partial unique index), and a phone's resend of
 * the same tap is the same row (`client_id`).
 *
 * `venues` gains `place_id` (unique where set) and `place_type`, and loses its name uniqueness: two
 * Google places can share a name, so a venue is now identified by its place id.
 */
export class DateChangeSuggestions1717000036000 implements MigrationInterface {
  name = 'DateChangeSuggestions1717000036000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "change_kind" AS ENUM ('time', 'place');`,
    );
    await queryRunner.query(
      `CREATE TYPE "change_status" AS ENUM ('pending', 'agreed', 'replaced', 'expired');`,
    );
    await queryRunner.query(`
      CREATE TYPE "change_entry" AS ENUM
        ('coordinate_tile', 'chat_chip', 'counter_card', 'counter_thread', 'cancel_sheet');
    `);
    await queryRunner.query(`
      CREATE TABLE "date_change_suggestions" (
        "id"                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "date_id"                    uuid NOT NULL REFERENCES "dates"("id") ON DELETE CASCADE,
        "sender_id"                  uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
        "kind"                       "change_kind" NOT NULL,
        "value_time"                 timestamptz,
        "place_id"                   varchar(255),
        "place_name"                 varchar(200),
        "place_type"                 varchar(16),
        "place_address"              varchar(300),
        "place_location"             geography(Point, 4326),
        "was_time"                   timestamptz NOT NULL,
        "was_venue_id"               uuid,
        "was_place_name"             varchar(200),
        "status"                     "change_status" NOT NULL DEFAULT 'pending',
        "deadline"                   timestamptz NOT NULL,
        "entry"                      "change_entry" NOT NULL,
        "from_search"                boolean,
        "client_id"                  uuid NOT NULL,
        "expired_binding_time"       timestamptz,
        "expired_binding_place_name" varchar(200),
        "created_at"                 timestamptz NOT NULL DEFAULT now(),
        "answered_at"                timestamptz,
        "replaced_at"                timestamptz,
        "expired_at"                 timestamptz
      );
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_date_change_suggestions_sender_client"
        ON "date_change_suggestions" ("sender_id", "client_id");
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_date_change_suggestions_pending_kind"
        ON "date_change_suggestions" ("date_id", "kind") WHERE "status" = 'pending';
    `);
    await queryRunner.query(`
      CREATE INDEX "idx_date_change_suggestions_status_deadline"
        ON "date_change_suggestions" ("status", "deadline");
    `);

    await queryRunner.query(
      `ALTER TABLE "venues" ADD COLUMN "place_id" varchar(255);`,
    );
    await queryRunner.query(
      `ALTER TABLE "venues" ADD COLUMN "place_type" varchar(16);`,
    );
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_venues_place_id" ON "venues" ("place_id") WHERE "place_id" IS NOT NULL;
    `);
    // Created inline as `name ... UNIQUE` in 1717000001000, so Postgres named it venues_name_key.
    await queryRunner.query(
      `ALTER TABLE "venues" DROP CONSTRAINT "venues_name_key";`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    // Fails if duplicate venue names exist by now (Google venues can share a name): de-duplicate first.
    await queryRunner.query(
      `ALTER TABLE "venues" ADD CONSTRAINT "venues_name_key" UNIQUE ("name");`,
    );
    await queryRunner.query(`DROP INDEX IF EXISTS "UQ_venues_place_id";`);
    await queryRunner.query(`ALTER TABLE "venues" DROP COLUMN "place_type";`);
    await queryRunner.query(`ALTER TABLE "venues" DROP COLUMN "place_id";`);

    await queryRunner.query(
      `DROP INDEX IF EXISTS "idx_date_change_suggestions_status_deadline";`,
    );
    await queryRunner.query(
      `DROP INDEX IF EXISTS "UQ_date_change_suggestions_pending_kind";`,
    );
    await queryRunner.query(
      `DROP INDEX IF EXISTS "UQ_date_change_suggestions_sender_client";`,
    );
    await queryRunner.query(`DROP TABLE IF EXISTS "date_change_suggestions";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "change_entry";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "change_status";`);
    await queryRunner.query(`DROP TYPE IF EXISTS "change_kind";`);
  }
}
