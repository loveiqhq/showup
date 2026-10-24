import {
  stripImageMetadata,
  stripJpeg,
  stripPng,
  stripWebp,
} from './image-metadata';

const seg = (marker: number, payload: string) => {
  const body = Buffer.from(payload, 'latin1');
  const head = Buffer.alloc(4);
  head[0] = 0xff;
  head[1] = marker;
  head.writeUInt16BE(body.length + 2, 2);
  return Buffer.concat([head, body]);
};

function jpeg(): Buffer {
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xe0, 'JFIF\0'),
    seg(0xe1, 'Exif\0\0GPSLatitude=52.4862'),
    seg(0xe1, 'http://ns.adobe.com/xap/1.0/ location'),
    seg(0xe2, 'ICC_PROFILE\0'),
    seg(0xed, 'Photoshop 3.0 IPTC'),
    seg(0xfe, 'a comment'),
    seg(0xdb, 'quant'),
    Buffer.from([0xff, 0xda, 0x00, 0x04, 0x01, 0x02, 0xaa, 0xbb, 0xff, 0xd9]),
  ]);
}

const pngChunk = (type: string, payload: string) => {
  const body = Buffer.from(payload, 'latin1');
  const head = Buffer.alloc(8);
  head.writeUInt32BE(body.length, 0);
  head.write(type, 4, 'latin1');
  return Buffer.concat([head, body, Buffer.alloc(4)]);
};

function png(): Buffer {
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', 'header13bytes'),
    pngChunk('iCCP', 'colour'),
    pngChunk('tEXt', 'Comment\0taken at home'),
    pngChunk('eXIf', 'MM\0*GPS'),
    pngChunk('iTXt', 'XML:com.adobe.xmp'),
    pngChunk('IDAT', 'pixels'),
    pngChunk('IEND', ''),
  ]);
}

const riffChunk = (fourcc: string, payload: Buffer) => {
  const head = Buffer.alloc(8);
  head.write(fourcc, 0, 'latin1');
  head.writeUInt32LE(payload.length, 4);
  return Buffer.concat([
    head,
    payload,
    payload.length % 2 ? Buffer.alloc(1) : Buffer.alloc(0),
  ]);
};

function webp(): Buffer {
  const vp8x = Buffer.alloc(10);
  vp8x[0] = 0x0c | 0x20; // EXIF + XMP + ICC flags
  const body = Buffer.concat([
    riffChunk('VP8X', vp8x),
    riffChunk('VP8 ', Buffer.from('frame', 'latin1')),
    riffChunk('EXIF', Buffer.from('MM\0*GPSLatitude', 'latin1')),
    riffChunk('XMP ', Buffer.from('<x:xmpmeta/>', 'latin1')),
  ]);
  const header = Buffer.alloc(12);
  header.write('RIFF', 0, 'latin1');
  header.writeUInt32LE(body.length + 4, 4);
  header.write('WEBP', 8, 'latin1');
  return Buffer.concat([header, body]);
}

describe('stripJpeg', () => {
  it('removes EXIF, XMP, IPTC and comments, and keeps JFIF, the colour profile and the image', () => {
    const out = stripJpeg(jpeg()).toString('latin1');
    expect(out).not.toContain('GPSLatitude');
    expect(out).not.toContain('xap/1.0');
    expect(out).not.toContain('IPTC');
    expect(out).not.toContain('a comment');
    expect(out).toContain('JFIF');
    expect(out).toContain('ICC_PROFILE');
    expect(out).toContain('quant');
    // The scan and the end-of-image marker are copied untouched.
    expect(stripJpeg(jpeg()).subarray(-6)).toEqual(
      Buffer.from([0x01, 0x02, 0xaa, 0xbb, 0xff, 0xd9]),
    );
  });

  it('returns bytes it cannot parse unchanged', () => {
    const broken = Buffer.from([0xff, 0xd8, 0x00, 0x11, 0x22, 0x33]);
    expect(stripJpeg(broken)).toBe(broken);
    const notJpeg = Buffer.from('hello');
    expect(stripJpeg(notJpeg)).toBe(notJpeg);
  });
});

describe('stripPng', () => {
  it('removes eXIf and the text chunks, and keeps the image and colour chunks', () => {
    const out = stripPng(png()).toString('latin1');
    expect(out).not.toContain('eXIf');
    expect(out).not.toContain('taken at home');
    expect(out).not.toContain('com.adobe.xmp');
    expect(out).toContain('IHDR');
    expect(out).toContain('iCCP');
    expect(out).toContain('IDAT');
    expect(out).toContain('IEND');
  });

  it('returns a truncated file unchanged', () => {
    const truncated = png().subarray(0, 30);
    expect(stripPng(truncated)).toBe(truncated);
  });
});

describe('stripWebp', () => {
  it('removes the EXIF and XMP chunks, clears their flags and fixes the RIFF size', () => {
    const out = stripWebp(webp());
    const text = out.toString('latin1');
    expect(text).not.toContain('GPSLatitude');
    expect(text).not.toContain('xmpmeta');
    expect(text).toContain('VP8 ');
    expect(out.readUInt32LE(4)).toBe(out.length - 8);
    // VP8X flags byte: EXIF (0x08) and XMP (0x04) cleared, ICC (0x20) kept.
    expect(out[12 + 8]).toBe(0x20);
  });
});

describe('stripImageMetadata', () => {
  it('picks the stripper by the declared type and leaves any other type alone', () => {
    expect(
      stripImageMetadata(jpeg(), 'image/jpeg').toString('latin1'),
    ).not.toContain('GPS');
    const other = Buffer.from('not an image');
    expect(stripImageMetadata(other, 'image/gif')).toBe(other);
  });
});
