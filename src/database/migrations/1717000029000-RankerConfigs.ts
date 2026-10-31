import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Matching 01 (SHOWUP-190): where the Search ranker's numbers live once they leave the code.
 *
 * "Every number lives in config, versioned as `ranker_version` -- changing a weight must never need
 * a release." The launch numbers are in the code (`LAUNCH_RANKER_CONFIG`) and apply while this table
 * holds no active row, so a fresh database ranks exactly as the ticket says. A new set of numbers is
 * a new row, made active; the ranker reads it within a minute, with no deploy.
 *
 * Enforced here rather than by convention:
 * - "Any change to any value -> new ranker_version": a stored row's version and config can never be
 *   edited, only replaced. Every `search_feed_ranked` names the version that ordered it, so an edited
 *   row would make old training records lie about their numbers.
 * - At most one active row.
 */
export class RankerConfigs1717000029000 implements MigrationInterface {
  name = 'RankerConfigs1717000029000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "ranker_configs" (
        "version" varchar(64) PRIMARY KEY,
        "config" jsonb NOT NULL,
        "active" boolean NOT NULL DEFAULT false,
        "created_at" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "ck_ranker_configs_version" CHECK ("config"->>'ranker_version' = "version")
      );
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "uq_ranker_configs_one_active" ON "ranker_configs" ("active") WHERE "active";
    `);
    await queryRunner.query(`
      CREATE FUNCTION "ranker_configs_immutable"() RETURNS trigger AS $$
      BEGIN
        IF NEW."version" IS DISTINCT FROM OLD."version" OR NEW."config" IS DISTINCT FROM OLD."config" THEN
          RAISE EXCEPTION 'a ranker config cannot be edited: insert a new version instead';
        END IF;
        RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;
    `);
    await queryRunner.query(`
      CREATE TRIGGER "trg_ranker_configs_immutable" BEFORE UPDATE ON "ranker_configs"
        FOR EACH ROW EXECUTE FUNCTION "ranker_configs_immutable"();
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "ranker_configs";`);
    await queryRunner.query(
      `DROP FUNCTION IF EXISTS "ranker_configs_immutable"();`,
    );
  }
}
