import { ApiProperty } from '@nestjs/swagger';

/**
 * What the client needs to work out where a half-finished profile left off.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FACTS, NOT A DECISION
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This deliberately does NOT return "the step to show". The profile flow's routing is a client
 * rule -- the README's 4a, "on launch, an account with an incomplete profile routes straight to
 * its last incomplete step" -- and it is expressed on both platforms as a pure function over these
 * values, unit-tested with no network. Returning a step name would move that rule onto the server,
 * where the app's own step enum could not check it and where adding a step would need a deploy on
 * both sides at once.
 *
 * The same argument the tutorial's routing already follows: `TutorialRouting` decides, the API
 * supplies what it decides from.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS NOT PART OF ProfileDto
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `ProfileDto` is described as the public-safe view of a profile and is returned by the admin
 * controller as well. An email address does not belong in it. One extra field there would put a
 * user's address on an admin response for no reason, so the two stay separate.
 *
 * ONE ROUND TRIP, not three. Resuming has to happen before the first frame, and asking for the
 * profile, the photos and the prompts separately would make that three sequential calls on a cold
 * launch.
 */
export class ProfileProgressDto {
  @ApiProperty({
    type: String,
    nullable: true,
    description: 'Step 1. Null when the name has not been given.',
  })
  displayName: string | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: 'Step 2. Null when no address has been submitted.',
  })
  email: string | null;

  @ApiProperty({
    description:
      'Step 3. False while an address is present but its code has not been confirmed.',
  })
  emailVerified: boolean;

  @ApiProperty({
    description:
      'Step 4. The date itself is never returned — only whether it is set.',
  })
  hasDateOfBirth: boolean;

  @ApiProperty({
    type: 'integer',
    description: 'Confirmed uploads. "The real you" step 1 needs four.',
  })
  photoCount: number;

  @ApiProperty({
    type: 'integer',
    description: '"The real you" step 2 needs one.',
  })
  promptCount: number;
}
