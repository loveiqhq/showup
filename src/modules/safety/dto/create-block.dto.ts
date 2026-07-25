import { ApiProperty } from '@nestjs/swagger';
import { IsUUID } from 'class-validator';

/** What a user sends to block someone. */
export class CreateBlockDto {
  @ApiProperty({ description: 'The id of the user to block' })
  @IsUUID()
  targetUserId: string;
}
