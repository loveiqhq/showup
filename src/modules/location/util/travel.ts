/**
 * Epic 5 — travel-time estimation and meeting feasibility (SHOWUP-112).
 *
 * Two people can only meet if, from the moment they are free, they each have enough time to get
 * ready AND travel to the venue AND still overlap for a real meeting. This module owns that maths.
 *
 * Travel time sits behind a small "seam" (the `TravelTimeEstimator` interface) so the source can be
 * swapped without changing callers. The chosen source is Google Maps (Routes API — Compute Route
 * Matrix) when an API key is configured; otherwise it falls back to a free, rough distance buffer.
 * Automated tests use the rough estimator or a mocked Google client, so they never spend a paid
 * map lookup.
 */

import { LatLng, haversineMeters } from '../location.geo';

export type { LatLng };

/** A source of travel-time estimates (in minutes) between two points. */
export interface TravelTimeEstimator {
  estimateMinutes(from: LatLng, to: LatLng): Promise<number>;
}

/** Tunable rate for the rough, no-cost estimator. */
export interface TravelBufferConfig {
  minutesPerKm: number;
}

/** Default rate: 2 km ≈ 30 minutes, i.e. 15 minutes per km. Tunable anytime from real data. */
export const DEFAULT_TRAVEL_BUFFER: TravelBufferConfig = { minutesPerKm: 15 };

/**
 * Great-circle distance in kilometres. Thin wrapper over the shared `haversineMeters` primitive
 * (in `location.geo`) so the maths has a single source of truth.
 */
export function haversineKm(a: LatLng, b: LatLng): number {
  return haversineMeters(a, b) / 1000;
}

/**
 * Rough travel-time estimate: distance × a configurable rate, rounded UP to whole minutes so we
 * never under-estimate how long someone needs. No external service, no cost.
 */
export function estimateTravelMinutes(
  distanceKm: number,
  config: TravelBufferConfig = DEFAULT_TRAVEL_BUFFER,
): number {
  if (!(distanceKm >= 0)) {
    // Guards both negatives and NaN.
    throw new Error('distanceKm must be a non-negative number');
  }
  return Math.ceil(distanceKm * config.minutesPerKm);
}

/** Free fallback estimator: great-circle distance × the tunable buffer rate. */
export class RoughTravelEstimator implements TravelTimeEstimator {
  constructor(
    private readonly buffer: TravelBufferConfig = DEFAULT_TRAVEL_BUFFER,
  ) {}

  estimateMinutes(from: LatLng, to: LatLng): Promise<number> {
    return Promise.resolve(
      estimateTravelMinutes(haversineKm(from, to), this.buffer),
    );
  }
}

/** Minimal shape of a fetch Response we rely on — keeps this decoupled from DOM/node typings. */
export interface FetchResponseLike {
  ok: boolean;
  status: number;
  json(): Promise<unknown>;
  text(): Promise<string>;
}

/** Minimal fetch signature, injectable so the Google client is unit-testable without a network. */
export type FetchLike = (
  url: string,
  init: { method: string; headers: Record<string, string>; body: string },
) => Promise<FetchResponseLike>;

interface GoogleRoutesOptions {
  apiKey: string;
  /** DRIVE (default), WALK, BICYCLE, or TRANSIT. */
  travelMode?: string;
  /** Injected for tests; defaults to the global fetch. */
  fetchFn?: FetchLike;
}

/**
 * Accurate travel time via Google Maps Routes API (Compute Route Matrix). Billed per
 * origin→destination pair ("element"); one person → one venue = one element. Only call this for a
 * shortlist (not wide exploration) and cache results to stay inside the free monthly allowance.
 */
export class GoogleRoutesTravelEstimator implements TravelTimeEstimator {
  private static readonly ENDPOINT =
    'https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix';

  constructor(private readonly options: GoogleRoutesOptions) {
    if (!options.apiKey) {
      throw new Error('Google Maps API key is required');
    }
  }

  async estimateMinutes(from: LatLng, to: LatLng): Promise<number> {
    const fetchFn = this.options.fetchFn ?? globalThis.fetch;
    const waypoint = (p: LatLng) => ({
      waypoint: { location: { latLng: { latitude: p.lat, longitude: p.lng } } },
    });

    const res = await fetchFn(GoogleRoutesTravelEstimator.ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': this.options.apiKey,
        'X-Goog-FieldMask': 'originIndex,destinationIndex,duration,condition',
      },
      body: JSON.stringify({
        origins: [waypoint(from)],
        destinations: [waypoint(to)],
        travelMode: this.options.travelMode ?? 'DRIVE',
      }),
    });

    if (!res.ok) {
      throw new Error(`Google Routes API error: HTTP ${res.status}`);
    }

    const elements = (await res.json()) as Array<{
      duration?: string;
      condition?: string;
    }>;
    const element = elements?.[0];
    if (!element || element.condition !== 'ROUTE_EXISTS' || !element.duration) {
      throw new Error('Google Routes API: no route found');
    }

    // Duration is returned as a string of seconds, e.g. "601s".
    const seconds = parseInt(element.duration, 10);
    return Math.ceil(seconds / 60);
  }
}

/**
 * Build the travel-time estimator for the app. Uses Google Maps when an API key is provided (the
 * current choice), otherwise the free rough buffer. Swapping the source later is just a config
 * change — no caller needs to change.
 */
export function createTravelEstimator(
  opts: {
    apiKey?: string;
    buffer?: TravelBufferConfig;
    fetchFn?: FetchLike;
    travelMode?: string;
  } = {},
): TravelTimeEstimator {
  if (opts.apiKey) {
    return new GoogleRoutesTravelEstimator({
      apiKey: opts.apiKey,
      fetchFn: opts.fetchFn,
      travelMode: opts.travelMode,
    });
  }
  return new RoughTravelEstimator(opts.buffer);
}

/**
 * The mandatory minimum time two people must have together for a date to be worth proposing.
 * 30 minutes is required; beyond that, people can choose to stay longer or leave.
 */
export const MIN_MEETING_MINUTES = 30;

/** One side of a potential meeting: when they're free, plus their prep and travel time. */
export interface MeetingParticipant {
  availabilityStart: Date;
  availabilityEnd: Date;
  preparationMinutes: number;
  travelMinutes: number;
}

/**
 * The earliest instant a participant could actually be AT the venue: measured from when they are
 * free (or "now", whichever is later, since you cannot leave before now), plus the time to get
 * ready, plus the time to travel.
 */
export function earliestArrival(p: MeetingParticipant, now: Date): Date {
  const freeFromMs = Math.max(p.availabilityStart.getTime(), now.getTime());
  const bufferMs = (p.preparationMinutes + p.travelMinutes) * 60_000;
  return new Date(freeFromMs + bufferMs);
}

/**
 * The earliest time two people can both be present with at least `minMeetingMinutes` of overlap
 * left in both their windows — or null if no such time exists (there simply isn't enough time once
 * prep + travel are subtracted). This answers "they check in at 1:30 and are free until 2:00 —
 * what happens?": if prep + travel push arrival past what leaves a 30-minute meeting, this returns
 * null and the pair is not offered as a match.
 */
export function earliestFeasibleMeeting(
  a: MeetingParticipant,
  b: MeetingParticipant,
  now: Date,
  minMeetingMinutes: number = MIN_MEETING_MINUTES,
): Date | null {
  const startMs = Math.max(
    earliestArrival(a, now).getTime(),
    earliestArrival(b, now).getTime(),
  );
  const latestEndMs = Math.min(
    a.availabilityEnd.getTime(),
    b.availabilityEnd.getTime(),
  );
  const fits = startMs + minMeetingMinutes * 60_000 <= latestEndMs;
  return fits ? new Date(startMs) : null;
}
