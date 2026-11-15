import { mkdir, rm, writeFile } from 'fs/promises';
import { dirname, join, resolve, sep } from 'path';

import { Injectable, Logger } from '@nestjs/common';

import { StorageService } from './storage.interface';

/**
 * Development storage adapter: writes uploaded files under ./uploads (git-ignored). Good enough
 * to build and test the photo flow locally. Replaced by a cloud adapter (S3/GCS/R2/Supabase)
 * before launch — see StorageService.
 */
@Injectable()
export class LocalDiskStorage implements StorageService {
  private readonly logger = new Logger('LocalDiskStorage');
  private readonly baseDir = join(process.cwd(), 'uploads');

  async save(key: string, data: Buffer, contentType: string): Promise<void> {
    const fullPath = this.pathFor(key);
    await mkdir(dirname(fullPath), { recursive: true });
    await writeFile(fullPath, data);
    this.logger.debug(`Stored ${key} (${contentType}, ${data.length} bytes)`);
  }

  async delete(key: string): Promise<void> {
    await rm(this.pathFor(key), { force: true });
  }

  /**
   * The file for `key`, refused if it would land outside ./uploads. Keys are built by the services
   * (`<userId>/<uuid>.<ext from an allow-list>`), so nothing a user sends reaches here; the check
   * keeps it that way if a caller ever changes.
   */
  private pathFor(key: string): string {
    // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- contained by the check below
    const fullPath = resolve(this.baseDir, key);
    if (!fullPath.startsWith(this.baseDir + sep)) {
      throw new Error('Storage key resolves outside the uploads folder');
    }
    return fullPath;
  }

  publicUrl(key: string): string {
    return `/uploads/${key}`;
  }
}
