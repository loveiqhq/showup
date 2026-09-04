import { ApiProperty } from '@nestjs/swagger';

/**
 * The error body every failing request returns.
 *
 * This is documentation of existing behaviour, not a new format. `AllExceptionsFilter` extends
 * `BaseExceptionFilter` and deliberately changes nothing about status codes or bodies, so what
 * clients actually receive is Nest's standard shape. The one place that throws a custom body --
 * `FreshAuthGuard`, with `error: 'step_up_required'` -- uses the same three fields.
 *
 * WHY THIS MATTERS FOR THE GENERATED CLIENTS
 *
 * Without a declared error schema, a generated Swift or Kotlin client has no type for a failure:
 * it surfaces an opaque HTTP error and every screen has to parse the body by hand, which is
 * exactly the kind of hand-written client code the OpenAPI pipeline exists to remove.
 */
export class ApiErrorDto {
  @ApiProperty({
    type: 'integer',
    example: 400,
    description: 'HTTP status code, repeated in the body.',
  })
  statusCode!: number;

  /**
   * A single string for most failures. The global ValidationPipe returns an ARRAY of strings --
   * one per failed constraint -- so the union is real and both clients must handle it. Declared
   * honestly rather than narrowed to string, because narrowing here would produce a generated
   * client that fails to decode exactly the errors users hit most.
   */
  @ApiProperty({
    description:
      'Human-readable message. An array when request validation failed, one entry per constraint.',
    oneOf: [{ type: 'string' }, { type: 'array', items: { type: 'string' } }],
    example: 'Please re-verify your identity to continue.',
  })
  message!: string | string[];

  @ApiProperty({
    description:
      'Short error identifier. Usually the standard reason phrase; domain errors use a code, e.g. step_up_required.',
    example: 'Bad Request',
  })
  error!: string;
}
