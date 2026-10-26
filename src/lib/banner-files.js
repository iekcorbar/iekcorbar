import fs from 'fs';
import path from 'path';

// Where uploaded profile banners live (served by the /uploads/* handler in
// app.js/server.js) and the one place that deletes them.
export const BANNER_UPLOAD_DIR = path.join(process.cwd(), 'public', 'uploads', 'banners');

// Only ever unlink a file this feature wrote: the URL must be one of ours and
// only its base name is used, so a poisoned database value can't escape the folder.
export async function deleteBannerFile(url) {
  if (typeof url !== 'string' || !url.startsWith('/uploads/banners/')) return;
  await fs.promises.unlink(path.join(BANNER_UPLOAD_DIR, path.basename(url))).catch(() => {});
}
