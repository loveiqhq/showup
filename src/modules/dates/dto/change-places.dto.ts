import { ApiProperty } from '@nestjs/swagger';

import { PLACE_TYPES, type PlaceType } from '../util/change-rules';

/** One place a date can move to (Date 05). */
export class PlaceOptionDto {
  @ApiProperty({
    description: "The provider's stable id for the place.",
    example: 'ChIJ_bar_lenz',
  })
  placeId: string;

  @ApiProperty({ example: 'Bar Lenz' })
  name: string;

  @ApiProperty({ enum: PLACE_TYPES })
  type: PlaceType;

  @ApiProperty({ example: 'Lausitzer Platz 9, Kreuzberg' })
  address: string;

  @ApiProperty({
    description:
      'Whole metres from the date’s current meet place; the clients format it.',
    example: 350,
  })
  distanceMeters: number;
}

/** Places near the date's current meet place, nearest first. */
export class ChangePlacesDto {
  @ApiProperty({
    enum: ['google'],
    nullable: true,
    description:
      "'google' only when Google answered, and its terms ask for the attribution line; null for fixtures.",
  })
  attribution: 'google' | null;

  @ApiProperty({ type: [PlaceOptionDto] })
  places: PlaceOptionDto[];
}
