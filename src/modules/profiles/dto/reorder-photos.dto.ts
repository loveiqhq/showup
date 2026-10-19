import { ApiProperty } from '@nestjs/swagger';
import { ArrayMaxSize, ArrayMinSize, IsArray, IsUUID } from 'class-validator';

import { MAX_PHOTOS } from '../photos.service';

/**
 * The body of `PATCH /me/photos/order` (SHOWUP-156).
 *
 * THE WHOLE ORDER, NOT A `{from, to}` PAIR, and that is the decision this DTO exists to record.
 *
 * A from/to pair is a DIFF against an order the server has to already agree with. Two drags in
 * quick succession on a slow connection arrive as two diffs applied to a list that moved between
 * them, and the result is an order the user never made. Sending the resulting order is idempotent
 * — replaying it changes nothing — which is the same reasoning that made `PUT /me/prompts/{topicId}`
 * the right shape for Save.
 *
 * It also means the client sends what it is already holding: the drag handler has computed the
 * final list before it animates, so there is nothing extra to derive.
 */
export class ReorderPhotosDto {
  @ApiProperty({
    type: [String],
    description:
      'Every one of the account’s photo ids, in the order they should be shown. ' +
      'The first is the main photo. Must be the complete set — a partial list is rejected.',
  })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(MAX_PHOTOS)
  @IsUUID('4', { each: true })
  ids: string[];
}
