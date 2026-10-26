// Server-side inspection of an uploaded image, without any image library.
// Used for profile banners: we never trust the browser-supplied file name or
// MIME type — the format, size and animation flag come from the bytes
// themselves, and anything we cannot parse is rejected.

const u16be = (b, o) => (b[o] << 8) | b[o + 1];
const u32be = (b, o) => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;
const u16le = (b, o) => b[o] | (b[o + 1] << 8);
const u24le = (b, o) => b[o] | (b[o + 1] << 8) | (b[o + 2] << 16);

function sniffPng(b) {
  // 8-byte signature, then the IHDR chunk: length(4) 'IHDR'(4) width(4) height(4)
  if (b.length < 24 || u32be(b, 0) !== 0x89504e47 || u32be(b, 4) !== 0x0d0a1a0a) return null;
  if (b.toString('latin1', 12, 16) !== 'IHDR') return null;
  // APNG (animated PNG) carries an 'acTL' chunk before the first 'IDAT'.
  const head = b.toString('latin1', 0, Math.min(b.length, 4096));
  const animated = head.includes('acTL') && (head.indexOf('acTL') < head.indexOf('IDAT') || head.indexOf('IDAT') === -1);
  return { type: 'png', ext: 'png', width: u32be(b, 16), height: u32be(b, 20), animated };
}

function sniffGif(b) {
  const sig = b.toString('latin1', 0, 6);
  if (b.length < 13 || (sig !== 'GIF87a' && sig !== 'GIF89a')) return null;
  // Animated = more than one image descriptor (0x2C). Counting separators
  // byte-by-byte could false-positive inside image data, so walk the blocks.
  let animated = false;
  let frames = 0;
  let pos = 13;
  if (b[10] & 0x80) pos += 3 * (1 << ((b[10] & 0x07) + 1)); // global colour table
  while (pos < b.length) {
    const marker = b[pos];
    if (marker === 0x3b) break; // trailer
    if (marker === 0x21) { // extension: skip its sub-blocks
      pos += 2;
      while (pos < b.length && b[pos] !== 0) pos += b[pos] + 1;
      pos += 1;
    } else if (marker === 0x2c) { // image descriptor
      frames += 1;
      if (frames > 1) { animated = true; break; }
      const flags = b[pos + 9];
      pos += 10;
      if (flags & 0x80) pos += 3 * (1 << ((flags & 0x07) + 1)); // local colour table
      pos += 1; // LZW minimum code size
      while (pos < b.length && b[pos] !== 0) pos += b[pos] + 1;
      pos += 1;
    } else {
      break; // malformed
    }
  }
  return { type: 'gif', ext: 'gif', width: u16le(b, 6), height: u16le(b, 8), animated };
}

function sniffJpeg(b) {
  if (b.length < 4 || b[0] !== 0xff || b[1] !== 0xd8) return null;
  let pos = 2;
  while (pos + 9 < b.length) {
    if (b[pos] !== 0xff) { pos += 1; continue; }
    const marker = b[pos + 1];
    if (marker === 0xff) { pos += 1; continue; }
    if (marker === 0xd8 || (marker >= 0xd0 && marker <= 0xd7) || marker === 0x01) { pos += 2; continue; }
    const len = u16be(b, pos + 2);
    // SOF0..SOF15 (except DHT 0xC4, JPG 0xC8, DAC 0xCC) hold the dimensions.
    if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
      return { type: 'jpeg', ext: 'jpg', height: u16be(b, pos + 5), width: u16be(b, pos + 7), animated: false };
    }
    pos += 2 + len;
  }
  return null;
}

function sniffWebp(b) {
  if (b.length < 30 || b.toString('latin1', 0, 4) !== 'RIFF' || b.toString('latin1', 8, 12) !== 'WEBP') return null;
  const kind = b.toString('latin1', 12, 16);
  if (kind === 'VP8X') {
    // flags byte at 20 (bit 1 = animation); canvas size is stored minus one
    return { type: 'webp', ext: 'webp', width: u24le(b, 24) + 1, height: u24le(b, 27) + 1, animated: !!(b[20] & 0x02) };
  }
  if (kind === 'VP8 ') {
    return { type: 'webp', ext: 'webp', width: u16le(b, 26) & 0x3fff, height: u16le(b, 28) & 0x3fff, animated: false };
  }
  if (kind === 'VP8L') {
    const bits = b[21] | (b[22] << 8) | (b[23] << 16) | (b[24] << 24);
    return { type: 'webp', ext: 'webp', width: (bits & 0x3fff) + 1, height: ((bits >> 14) & 0x3fff) + 1, animated: false };
  }
  return null;
}

// -> { type, ext, width, height, animated } or null when it is not a PNG, GIF,
// JPEG or WebP we can read.
export function sniffImage(buffer) {
  const b = Buffer.isBuffer(buffer) ? buffer : Buffer.from(buffer);
  try {
    return sniffPng(b) || sniffGif(b) || sniffJpeg(b) || sniffWebp(b);
  } catch {
    return null;
  }
}

// JPEGs from phones/cameras can carry EXIF (including GPS position) and XMP in
// APP1 segments. Drop those (and the comment segment) so an uploaded banner
// never leaks where a photo was taken. Everything needed to display the image
// (including the ICC colour profile in APP2) is kept.
export function stripJpegMetadata(buffer) {
  const b = Buffer.isBuffer(buffer) ? buffer : Buffer.from(buffer);
  if (b.length < 4 || b[0] !== 0xff || b[1] !== 0xd8) return b;
  const parts = [b.subarray(0, 2)];
  let pos = 2;
  while (pos + 4 <= b.length) {
    if (b[pos] !== 0xff) break;
    const marker = b[pos + 1];
    if (marker === 0xda) break; // start of scan: the rest is image data
    if (marker === 0xd8 || (marker >= 0xd0 && marker <= 0xd7) || marker === 0x01) {
      parts.push(b.subarray(pos, pos + 2));
      pos += 2;
      continue;
    }
    const len = u16be(b, pos + 2);
    const end = pos + 2 + len;
    if (end > b.length) break;
    const drop = marker === 0xe1 || marker === 0xfe; // APP1 (EXIF/XMP) and COM
    if (!drop) parts.push(b.subarray(pos, end));
    pos = end;
  }
  parts.push(b.subarray(pos));
  return Buffer.concat(parts);
}
