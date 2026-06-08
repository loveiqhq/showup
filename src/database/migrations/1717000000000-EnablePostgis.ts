import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Enables the PostGIS extension so the database can store geography points and run spatial
 * queries (distance, nearby, within-radius). Required by ShowUp's location features (Epic 5).
 */
export class EnablePostgis1717000000000 implements MigrationInterface {
  name = 'EnablePostgis1717000000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`CREATE EXTENSION IF NOT EXISTS postgis;`);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP EXTENSION IF EXISTS postgis;`);
  }
}
