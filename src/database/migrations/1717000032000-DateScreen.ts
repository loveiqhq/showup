import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 01, the Date screen (SHOWUP-193).
 *
 * - `venues.address`: the time + place card shows "{place}" over "{address}", and Copy, Share and
 *   Calendar hand the two on together. A venue had only a name. Nullable, because a place picked
 *   later (Google Places, Date 05) may arrive without one, and the screen then shows the name alone.
 *   The demo seed's venues get their real street addresses.
 * - `dates.coordinate_unlocked_at`: when the once-a-minute job sent `coordinate_unlocked` (meet
 *   − 1:30). The stamp is what makes the job fire once per date, however many workers run it.
 * - `dates.a_chat_read_at` / `b_chat_read_at`: when each person last read the date's chat. A message
 *   from the other person after it is unread, which moves the Coordinate card above the time + place
 *   card (state C). Null means never read.
 */
export class DateScreen1717000032000 implements MigrationInterface {
  name = 'DateScreen1717000032000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "venues" ADD COLUMN IF NOT EXISTS "address" character varying(300);`,
    );
    await queryRunner.query(`
      UPDATE "venues" SET "address" = v.address
        FROM (VALUES
          ('Brandenburg Gate', 'Pariser Platz, Mitte'),
          ('Reichstag', 'Platz der Republik 1, Tiergarten'),
          ('Berlin TV Tower', 'Panoramastrasse 1A, Mitte'),
          ('Cologne Cathedral', 'Domkloster 4, Altstadt-Nord')
        ) AS v(name, address)
       WHERE "venues"."name" = v.name AND "venues"."address" IS NULL;
    `);
    await queryRunner.query(`
      ALTER TABLE "dates"
        ADD COLUMN "coordinate_unlocked_at" timestamptz,
        ADD COLUMN "a_chat_read_at" timestamptz,
        ADD COLUMN "b_chat_read_at" timestamptz;
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "dates"
        DROP COLUMN IF EXISTS "b_chat_read_at",
        DROP COLUMN IF EXISTS "a_chat_read_at",
        DROP COLUMN IF EXISTS "coordinate_unlocked_at";
    `);
    await queryRunner.query(
      `ALTER TABLE "venues" DROP COLUMN IF EXISTS "address";`,
    );
  }
}
