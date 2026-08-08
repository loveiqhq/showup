import { Body, Controller, Headers, HttpCode, Post } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';

import { AuthService } from './auth.service';
import { Public } from './decorators/public.decorator';
import { AppleLoginDto } from './dto/apple-login.dto';
import {
  AuthResponseDto,
  OtpChallengeResponseDto,
} from './dto/auth-response.dto';
import { GoogleLoginDto } from './dto/google-login.dto';
import { LogoutDto } from './dto/logout.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RequestOtpDto } from './dto/request-otp.dto';
import { VerifyOtpDto } from './dto/verify-otp.dto';
import { RequestEmailDto } from './dto/request-email.dto';
import { VerifyEmailDto } from './dto/verify-email.dto';
import { CurrentUser } from './decorators/current-user.decorator';
import { EmailOtpService } from './email-otp.service';
import { User } from '../users/entities/user.entity';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly emailOtp: EmailOtpService,
  ) {}

  @Public()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('phone/start')
  @HttpCode(200)
  @ApiOkResponse({ type: OtpChallengeResponseDto })
  start(@Body() dto: RequestOtpDto): Promise<OtpChallengeResponseDto> {
    return this.auth.requestPhoneOtp(dto);
  }

  @Public()
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('phone/verify')
  @HttpCode(200)
  @ApiOkResponse({ type: AuthResponseDto })
  verifyPhone(
    @Body() dto: VerifyOtpDto,
    @Headers('user-agent') ua?: string,
  ): Promise<AuthResponseDto> {
    return this.auth.verifyPhoneOtp(dto, { userAgent: ua ?? null });
  }

  @Public()
  @Post('apple')
  @HttpCode(200)
  @ApiOkResponse({ type: AuthResponseDto })
  apple(
    @Body() dto: AppleLoginDto,
    @Headers('user-agent') ua?: string,
  ): Promise<AuthResponseDto> {
    return this.auth.loginWithApple(dto, { userAgent: ua ?? null });
  }

  @Public()
  @Post('google')
  @HttpCode(200)
  @ApiOkResponse({ type: AuthResponseDto })
  google(
    @Body() dto: GoogleLoginDto,
    @Headers('user-agent') ua?: string,
  ): Promise<AuthResponseDto> {
    return this.auth.loginWithGoogle(dto, { userAgent: ua ?? null });
  }

  @Public()
  @Post('refresh')
  @HttpCode(200)
  @ApiOkResponse({ type: AuthResponseDto })
  refresh(
    @Body() dto: RefreshDto,
    @Headers('user-agent') ua?: string,
  ): Promise<AuthResponseDto> {
    return this.auth.refresh(dto, { userAgent: ua ?? null });
  }

  @Public()
  @Post('logout')
  @HttpCode(204)
  logout(@Body() dto: LogoutDto): Promise<void> {
    return this.auth.logout(dto.refreshToken);
  }

  /**
   * Start email verification (authenticated): send a 6-digit code to the address. Email is a
   * support/contact detail, not a sign-in method.
   */
  @Post('email/start')
  @HttpCode(200)
  @ApiOkResponse({ type: OtpChallengeResponseDto })
  emailStart(@CurrentUser() user: User, @Body() dto: RequestEmailDto) {
    return this.emailOtp.request(user.id, dto.email);
  }

  /** Confirm the email code, marking the signed-in user's email as verified. */
  @Post('email/verify')
  @HttpCode(204)
  async emailVerify(
    @CurrentUser() user: User,
    @Body() dto: VerifyEmailDto,
  ): Promise<void> {
    await this.emailOtp.verify(user.id, dto.code);
  }
}
