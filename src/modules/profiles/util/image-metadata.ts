/**
 * Strips the metadata a photo can carry -- above all a phone's GPS position -- before it is stored.
 *
 * WHY HERE, NOW. Search 01 (SHOWUP-184) is the first screen that shows a person's photos to someone
 * else, and its rule is that "the client never receives an exact distance or coordinates". A phone
 * photo's EXIF holds exactly that: latitude and longitude to a few metres. Both apps already
 * re-encode every photo before upload, which writes no EXIF, but the server cannot rely on the
 * client for a privacy rule -- any other client could post a raw camera file. SHOWUP-34 asked for
 * this ("Photo metadata does not expose unsafe information"); re-encoding on the server still
 * arrives with the resizing work on SHOWUP-119, and this is the part of it that cannot wait.
 *
 * WHAT IS REMOVED, PER FORMAT (the three the upload accepts):
 *
 * - JPEG: every APP1 segment (EXIF and XMP), APP13 (Photoshop/IPTC) and COM. APP0 (JFIF), APP2
 *   (the ICC colour profile) and APP14 (Adobe colour transform) stay: dropping them changes how the
 *   photo's colours decode.
 * - PNG: `eXIf`, `tEXt`, `zTXt` and `iTXt`. Colour chunks (`iCCP`, `gAMA`, `cHRM`, `sRGB`) stay.
 * - WebP: the `EXIF` and `XMP ` chunks, with the extended header's two flags cleared to match.
 *
 * A file that does not parse as its declared type is returned UNCHANGED rather than refused: the
 * upload's own type check has already accepted it, and corrupting bytes we do not understand is
 * worse than passing them on. Pure functions over a Buffer, so each format is tested on its own.
 */
export function stripImageMetadata(data: Buffer, contentType: string): Buffer {
  switch (contentType) {
    case 'image/jpeg':
      return stripJpeg(data);
    case 'image/png':
      return stripPng(data);
    case 'image/webp':
      return stripWebp(data);
    default:
      return data;
  }
}

/** APP1 (EXIF, XMP), APP13 (IPTC) and COM: where a camera writes who, when and where. */
const JPEG_DROPPED_MARKERS = new Set([0xe1, 0xed, 0xfe]);

export function stripJpeg(data: Buffer): Buffer {
  if (data.length < 4 || data[0] !== 0xff || data[1] !== 0xd8) return data;
  const kept: Buffer[] = [data.subarray(0, 2)];
  let i = 2;
  while (i + 4 <= data.length) {
    if (data[i] !== 0xff) return data;
    const marker = data[i + 1];
    // Start of scan: the rest is entropy-coded image data, copied whole.
    if (marker === 0xda) {
      kept.push(data.subarray(i));
      return Buffer.concat(kept);
    }
    // Markers without a length (RSTn, TEM, a stray SOI).
    if ((marker >= 0xd0 && marker <= 0xd8) || marker === 0x01) {
      kept.push(data.subarray(i, i + 2));
      i += 2;
      continue;
    }
    const length = data.readUInt16BE(i + 2);
    if (length < 2 || i + 2 + length > data.length) return data;
    if (!JPEG_DROPPED_MARKERS.has(marker)) {
      kept.push(data.subarray(i, i + 2 + length));
    }
    i += 2 + length;
  }
  return data;
}

const PNG_SIGNATURE = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
]);
const PNG_DROPPED_CHUNKS = new Set(['eXIf', 'tEXt', 'zTXt', 'iTXt']);

export function stripPng(data: Buffer): Buffer {
  if (data.length < 8 || !data.subarray(0, 8).equals(PNG_SIGNATURE))
    return data;
  const kept: Buffer[] = [data.subarray(0, 8)];
  let i = 8;
  while (i + 12 <= data.length) {
    const length = data.readUInt32BE(i);
    const type = data.toString('latin1', i + 4, i + 8);
    const end = i + 12 + length; // length + type + data + CRC
    if (end > data.length) return data;
    if (!PNG_DROPPED_CHUNKS.has(type)) kept.push(data.subarray(i, end));
    i = end;
    if (type === 'IEND') return Buffer.concat(kept);
  }
  return data;
}

export function stripWebp(data: Buffer): Buffer {
  if (
    data.length < 12 ||
    data.toString('latin1', 0, 4) !== 'RIFF' ||
    data.toString('latin1', 8, 12) !== 'WEBP'
  ) {
    return data;
  }
  const kept: Buffer[] = [];
  let i = 12;
  while (i + 8 <= data.length) {
    const fourcc = data.toString('latin1', i, i + 4);
    const size = data.readUInt32LE(i + 4);
    // Chunks are padded to an even length.
    const end = i + 8 + size + (size % 2);
    if (end > data.length) return data;
    if (fourcc !== 'EXIF' && fourcc !== 'XMP ') {
      const chunk = Buffer.from(data.subarray(i, end));
      // VP8X carries "this file has EXIF" (bit 3) and "has XMP" (bit 2): cleared to match.
      if (fourcc === 'VP8X' && chunk.length > 8) chunk[8] &= ~0x0c;
      kept.push(chunk);
    }
    i = end;
  }
  const body = Buffer.concat(kept);
  const header = Buffer.alloc(12);
  header.write('RIFF', 0, 'latin1');
  header.writeUInt32LE(body.length + 4, 4);
  header.write('WEBP', 8, 'latin1');
  return Buffer.concat([header, body]);
}
