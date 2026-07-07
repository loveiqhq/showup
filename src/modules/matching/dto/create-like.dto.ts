import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

/** What a user sends to like someone. */
export class CreateLikeDto {
  @ApiProperty({ description: 'The id of the user being liked' })
  @IsUUID()
  targetUserId: string;

  @ApiPropertyOptional({
    description:
      'Optional short message to attach to the like (premium only, max 500 characters).',
  })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  message?: string;
}
