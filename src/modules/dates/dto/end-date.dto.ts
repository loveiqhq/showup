import { ApiProperty } from '@nestjs/swagger';
import { IsEnum } from 'class-validator';

import { EndEntry, EndReason } from '../util/date-end';

/** Body of `POST /dates/:id/end` (Date 06, SHOWUP-197). */
export class EndDateDto {
  @ApiProperty({
    enum: EndReason,
    enumName: 'DateEndReason',
    description:
      'How the person ended the date (registry §43). `partner_no_show` needs the agreed time to have come and a committed check-in; `uncomfortable` needs a committed check-in; `met` needs neither.',
  })
  @IsEnum(EndReason)
  reason: EndReason;

  @ApiProperty({
    enum: EndEntry,
    enumName: 'EndEntry',
    description: 'Where the end started (registry §45)',
  })
  @IsEnum(EndEntry)
  entry: EndEntry;
}
