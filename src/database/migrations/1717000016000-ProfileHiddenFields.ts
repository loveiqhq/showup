import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Adds per-field profile visibility: `profiles.hidden_fields`, the set of registry `field_id`s a
 * user has chosen not to display. First use is the age control on the date-of-birth step
 * (SHOWUP-154).
 *
 * WHY A `text[]` AND NOT SOMETHING ELSE
 *
 * It is a small unordered set of scalars owned entirely by one row, which is what a Postgres array
 * is for. A `jsonb` column would parse on every read and buy nothing here; a join table would add
 * a join to every profile read for at most nine values; an enum array would need a migration each
 * time a control is added, and the `field_id` vocabulary grows with the profile flow. The allowed
 * values live in `util/hidden-fields.ts` and are enforced at the DTO, so a new control costs a line
 * of TypeScript rather than a schema change.
 *
 * NOT NULL DEFAULT '{}' rather than nullable: "no hidden fields" and "not yet answered" are the
 * same state for this column, and a nullable array would make every reader handle three cases to
 * express two. Existing rows backfill to the empty set, which is the current behaviour — every
 * field displayed — so this migration cannot change what any existing profile shows.
 *
 * A GIN index is deliberately not added. The only query today is reading the set back with the
 * profile row; "which users hide their age" is an analytics question answered off the warehouse,
 * not this table. Add one when a containment query actually appears in a hot path.
 */
export class ProfileHiddenFields1717000016000 implements MigrationInterface {
  name = 'ProfileHiddenFields1717000016000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "hidden_fields" text[] NOT NULL DEFAULT '{}';`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "profiles" DROP COLUMN IF EXISTS "hidden_fields";`,
    );
  }
}
