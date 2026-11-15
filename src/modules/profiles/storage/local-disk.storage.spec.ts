import { LocalDiskStorage } from './local-disk.storage';

describe('LocalDiskStorage', () => {
  const storage = new LocalDiskStorage();

  it('refuses a key that would land outside ./uploads, before writing anything', async () => {
    await expect(
      storage.save('../outside.png', Buffer.from('x'), 'image/png'),
    ).rejects.toThrow('outside the uploads folder');
    await expect(storage.delete('../../etc/hosts')).rejects.toThrow(
      'outside the uploads folder',
    );
  });

  it('accepts a key the services build (a missing file is no error)', async () => {
    await expect(
      storage.delete('user-id/0b7d4a1e-2c3f-4e5a-8b6c-7d8e9f0a1b2c.png'),
    ).resolves.toBeUndefined();
  });

  it('keeps the public URL as before', () => {
    expect(storage.publicUrl('u/p.png')).toBe('/uploads/u/p.png');
  });
});
