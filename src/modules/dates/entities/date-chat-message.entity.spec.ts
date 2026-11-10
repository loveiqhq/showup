import { getMetadataArgsStorage } from 'typeorm';

import { DateChatMessage } from './date-chat-message.entity';

describe('DateChatMessage (Date 02 J)', () => {
  it('declares the partial unique index migration 1717000034000 creates, so a generated migration keeps it', () => {
    const index = getMetadataArgsStorage().indices.find(
      (i) =>
        i.target === DateChatMessage &&
        i.name === 'UQ_date_chat_messages_client',
    );
    expect(index).toMatchObject({
      unique: true,
      where: '"client_id" IS NOT NULL',
      columns: ['dateId', 'senderId', 'clientId'],
    });
  });
});
