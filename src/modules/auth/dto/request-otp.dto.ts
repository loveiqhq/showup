import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength } from 'class-validator';

export class RequestOtpDto {
  @ApiProperty({
    example: '+4915123456789',
    description: 'Phone in E.164 format',
  })
  @IsString()
  @MaxLength(32)
  phone: string;

  @ApiPropertyOptional({
    description: 'Opaque device identifier for this session',
  })
  @IsOptional()
  @IsString()
  @MaxLength(255)
  deviceId?: string;
}
