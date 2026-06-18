import { mkdir, rm, writeFile } from 'fs/promises';
import { dirname, join } from 'path';

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
    const fullPath = join(this.baseDir, key);
    await mkdir(dirname(fullPath), { recursive: true });
    await writeFile(fullPath, data);
    this.logger.debug(`Stored ${key} (${contentType}, ${data.length} bytes)`);
  }

  async delete(key: string): Promise<void> {
    await rm(join(this.baseDir, key), { force: true });
  }

  publicUrl(key: string): string {
    return `/uploads/${key}`;
  }
}
