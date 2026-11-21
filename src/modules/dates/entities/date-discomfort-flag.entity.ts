import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * One "Stay for now" tap on Date 06's I don't feel comfortable step: an internal flag against the
 * date, kept as evidence for flow 10 and never shown to either person. Every tap stores one more row.
 */
@Entity('date_discomfort_flags')
export class DateDiscomfortFlag {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'date_id', type: 'uuid' })
  dateId: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
