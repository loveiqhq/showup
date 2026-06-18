import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, Length, MaxLength } from 'class-validator';

export class VerifyOtpDto {
  @ApiProperty({
    example: '+4915123456789',
    description: 'Phone in E.164 format',
  })
  @IsString()
  @MaxLength(32)
  phone: string;

  @ApiProperty({ example: '4821' })
  @IsString()
  @Length(4, 8)
  code: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(255)
  deviceId?: string;
}
