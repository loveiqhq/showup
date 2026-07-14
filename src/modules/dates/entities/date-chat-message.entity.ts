import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

import { ChatReason } from '../util/pre-date-chat';

export { ChatReason };

/**
 * One pre-date chat message (Epic 7, SHOWUP-115). Each message belongs to a date, records who sent
 * it, and always carries a preset `reason`; `body` is the optional free text the sender added on top.
 * The reason is what feeds analytics (Epic 11); the free-text body stays private to the two people.
 * Append-only — messages are never edited or deleted once sent.
 */
@Entity('date_chat_messages')
export class DateChatMessage {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ name: 'sender_id', type: 'uuid' })
  senderId: string;

  @Column({
    type: 'enum',
    enum: ChatReason,
    enumName: 'date_chat_reason_enum',
  })
  reason: ChatReason;

  // Optional free-text note attached to the chosen reason. Private between the two participants.
  @Column({ name: 'body', type: 'varchar', length: 1000, nullable: true })
  body: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
