/**
 * Parse a duration string like `15m`, `30d`, `900s`, `24h` into milliseconds.
 * A bare number is treated as seconds. Throws on malformed input.
 */
export function parseDurationMs(value: string): number {
  const match = /^(\d+)\s*(ms|s|m|h|d)$/.exec(value.trim());
  if (!match) {
    const asNumber = Number(value);
    if (Number.isFinite(asNumber)) return asNumber * 1000;
    throw new Error(`Invalid duration: "${value}"`);
  }
  const amount = parseInt(match[1], 10);
  const unitMs: Record<string, number> = {
    ms: 1,
    s: 1000,
    m: 60_000,
    h: 3_600_000,
    d: 86_400_000,
  };
  return amount * unitMs[match[2]];
}
