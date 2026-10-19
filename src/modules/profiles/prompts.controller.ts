import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  Put,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiTags,
} from '@nestjs/swagger';

import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { User } from '../users/entities/user.entity';
import { PromptDto, UpsertPromptDto } from './dto/prompt.dto';
import { PromptsService } from './prompts.service';

/**
 * The written prompts on a profile (SHOWUP-158).
 *
 * KEYED ON THE TOPIC, NOT ON A ROW ID, and that is the whole shape of this API. The screen edits
 * one prompt at a time and guarantees one prompt per topic -- a used topic renders disabled in the
 * picker -- so the topic IS the identity the client already holds. That makes `PUT` idempotent,
 * which is exactly what "Save overwrites" means on the write sheet, and it means the client never
 * has to hold a server id to edit something it just wrote.
 *
 * The alternative, POST returning an id and PATCH by that id, would make the first save and every
 * later one two different calls for one button.
 */
@ApiTags('profiles')
@ApiBearerAuth()
@Controller('me/prompts')
export class PromptsController {
  constructor(private readonly prompts: PromptsService) {}

  @Get()
  @ApiOperation({ operationId: 'listPrompts' })
  @ApiOkResponse({ type: [PromptDto] })
  async list(@CurrentUser() user: User): Promise<PromptDto[]> {
    const prompts = await this.prompts.list(user.id);
    return prompts.map((prompt) => PromptDto.from(prompt));
  }

  @Put(':topicId')
  @ApiOperation({ operationId: 'upsertPrompt' })
  @ApiParam({
    name: 'topicId',
    example: 'first_date',
    description:
      'Stable topic id from the app’s list. Not validated against a catalogue — the ' +
      'fifteen topics are design content and live in the app.',
  })
  @ApiOkResponse({ type: PromptDto })
  async upsert(
    @CurrentUser() user: User,
    @Param('topicId') topicId: string,
    @Body() dto: UpsertPromptDto,
  ): Promise<PromptDto> {
    return PromptDto.from(
      await this.prompts.upsert(user.id, topicId, dto.answer),
    );
  }

  @Delete(':topicId')
  @HttpCode(204)
  @ApiOperation({ operationId: 'deletePrompt' })
  @ApiNoContentResponse()
  async remove(
    @CurrentUser() user: User,
    @Param('topicId') topicId: string,
  ): Promise<void> {
    await this.prompts.remove(user.id, topicId);
  }
}
