/** DI token for the object-storage implementation. */
export const STORAGE = Symbol('STORAGE');

/**
 * Object storage for media (photos). Implemented now by a local-disk dev adapter; a cloud adapter
 * (S3 / GCS / R2 / Supabase) plugs in later with no changes to the photo flow — same pattern as
 * the SMS sender. Only the backend writes/deletes; image bytes are never kept in the database.
 */
export interface StorageService {
  save(key: string, data: Buffer, contentType: string): Promise<void>;
  delete(key: string): Promise<void>;
  /** A URL the client can use to fetch the object (CDN/bucket URL in production). */
  publicUrl(key: string): string;
}
