import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Date 02, a message that did not send (SHOWUP-194 J, 9 Nov 2026): "tapping the line resends it ...
 * the match receives it exactly once."
 *
 * A send can land while its answer is lost, so the phone shows `Couldn't send` for a message the
 * server already has. `client_id` is the id the phone gave the message when it was first sent and
 * keeps for every resend; the partial unique index makes a second row with the same id impossible,
 * so a resend answers with the first copy instead of adding one. Per date and sender: two phones can
 * never collide, and a message sent before this column (no id) is never matched.
 */
export class ChatClientId1717000034000 implements MigrationInterface {
  name = 'ChatClientId1717000034000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "date_chat_messages" ADD COLUMN "client_id" uuid;`,
    );
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_date_chat_messages_client"
        ON "date_chat_messages" ("date_id", "sender_id", "client_id")
        WHERE "client_id" IS NOT NULL;
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `DROP INDEX IF EXISTS "UQ_date_chat_messages_client";`,
    );
    await queryRunner.query(
      `ALTER TABLE "date_chat_messages" DROP COLUMN IF EXISTS "client_id";`,
    );
  }
}
