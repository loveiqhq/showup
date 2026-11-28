import { ApiProperty } from '@nestjs/swagger';
import { IsIn } from 'class-validator';

import {
  CLIENT_END_ENTRIES,
  CLIENT_END_REASONS,
  EndEntry,
  EndReason,
} from '../util/date-end';

/** Body of `POST /dates/:id/end` (Date 06, SHOWUP-197). */
export class EndDateDto {
  @ApiProperty({
    enum: CLIENT_END_REASONS,
    enumName: 'DateEndReason',
    description:
      'How the person ended the date (registry §43). `partner_no_show` needs the agreed time to have come and a committed check-in; `uncomfortable` needs a committed check-in; `met` needs neither.',
  })
  @IsIn(CLIENT_END_REASONS)
  reason: EndReason;

  @ApiProperty({
    enum: CLIENT_END_ENTRIES,
    enumName: 'EndEntry',
    description: 'Where the end started (registry §45)',
  })
  @IsIn(CLIENT_END_ENTRIES)
  entry: EndEntry;
}
