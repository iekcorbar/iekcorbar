// Story Room file uploads — which file types are accepted and how they're
// grouped. Plain data, safe for both the client (StoryRoomTab) and the API
// (api/story/files).
//
// An allowlist on purpose: anything that a browser or OS would *run* (html,
// js, exe, bat, …) is refused, because files uploaded here get downloaded
// and opened by other writers. SVG is accepted but only ever downloaded,
// never shown inline (it can carry script).

export const STORY_FILE_MAX_BYTES = 100 * 1024 * 1024; // 100MB per file

export const STORY_FILE_CATEGORIES = [
  { key: 'image', label: 'Images', exts: ['png', 'jpg', 'jpeg', 'gif', 'webp', 'avif', 'bmp', 'svg', 'psd', 'kra', 'xcf', 'ase', 'aseprite', 'tga', 'tif', 'tiff'] },
  { key: 'model', label: '3D models', exts: ['obj', 'mtl', 'fbx', 'glb', 'gltf', 'stl', 'dae', '3ds', 'ply', 'blend', 'bbmodel', 'vox', 'usdz'] },
  { key: 'minecraft', label: 'Minecraft', exts: ['schem', 'schematic', 'litematic', 'nbt', 'mcstructure', 'mcfunction', 'mcmeta', 'mcpack', 'mcworld'] },
  { key: 'document', label: 'Documents', exts: ['pdf', 'docx', 'doc', 'odt', 'rtf', 'txt', 'md', 'csv', 'xlsx', 'xls', 'ods', 'pptx', 'odp', 'json', 'yml', 'yaml'] },
  { key: 'media', label: 'Audio & video', exts: ['mp3', 'ogg', 'wav', 'm4a', 'flac', 'mp4', 'webm', 'mov'] },
  { key: 'other', label: 'Archives & fonts', exts: ['zip', '7z', 'rar', 'ttf', 'otf', 'woff', 'woff2'] }
];

const EXT_TO_CATEGORY = new Map(STORY_FILE_CATEGORIES.flatMap(c => c.exts.map(e => [e, c.key])));

export const STORY_FILE_ACCEPT = STORY_FILE_CATEGORIES.flatMap(c => c.exts.map(e => `.${e}`)).join(',');

// Types the browser may show in-page (thumbnails, image preview, players).
const INLINE_TYPES = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', avif: 'image/avif', bmp: 'image/bmp',
  mp3: 'audio/mpeg', ogg: 'audio/ogg', wav: 'audio/wav', m4a: 'audio/mp4', mp4: 'video/mp4', webm: 'video/webm', mov: 'video/quicktime'
};

export function storyFileExt(name) {
  const m = /\.([a-z0-9]+)$/i.exec(String(name || ''));
  return m ? m[1].toLowerCase() : '';
}

export function storyFileCategory(name) {
  return EXT_TO_CATEGORY.get(storyFileExt(name)) || null;
}

export function isAllowedStoryFile(name) {
  return EXT_TO_CATEGORY.has(storyFileExt(name));
}

// MIME type when it's safe to display inline, else null (always download).
export function storyFileInlineType(name) {
  return INLINE_TYPES[storyFileExt(name)] || null;
}

export function isStoryImage(name) {
  return (storyFileInlineType(name) || '').startsWith('image/');
}

// Already-compressed formats: stored as-is inside ZIP downloads.
const COMPRESSED = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'avif', 'glb', 'zip', '7z', 'rar', 'mp3', 'ogg', 'm4a', 'flac', 'mp4', 'webm', 'mov', 'docx', 'xlsx', 'pptx', 'odt', 'ods', 'odp', 'mcpack', 'mcworld', 'woff', 'woff2', 'usdz', 'schem', 'litematic', 'nbt']);
export function isCompressedStoryFile(name) {
  return COMPRESSED.has(storyFileExt(name));
}

export function formatBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return `${v} B`;
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(v < 10 * 1024 ? 1 : 0)} KB`;
  if (v < 1024 * 1024 * 1024) return `${(v / (1024 * 1024)).toFixed(1)} MB`;
  return `${(v / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}
