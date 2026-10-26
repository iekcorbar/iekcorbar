import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
import steamworksSeed from '@/lib/wiki-seeds/steamworks.json';
import kitchenSeed from '@/lib/wiki-seeds/kitchen.json';
import wildlifeSeed from '@/lib/wiki-seeds/wildlife.json';
import sakaguraSeed from '@/lib/wiki-seeds/sakagura.json';
import kuraSeed from '@/lib/wiki-seeds/kura.json';
import satchelSeed from '@/lib/wiki-seeds/satchel.json';
import zukanSeed from '@/lib/wiki-seeds/zukan.json';
import gouwSeed from '@/lib/wiki-seeds/gouw.json';
import additionsSeed from '@/lib/wiki-seeds/additions.json';
import powerSeed from '@/lib/wiki-seeds/power.json';
// Get all wiki pages, plus the staff-defined chapter (category) order if one
// has ever been saved. Reuses the generic `settings` key-value store (same
// one api/settings/route.js exposes) rather than a dedicated table, since
// this is just one more string value alongside java_version/map_url.
export async function GET() {
  try {
    const pages = await db.getWikiPages();
    const settings = await db.getSettings();
    let categoryOrder = null;
    if (settings.wiki_category_order) {
      try { categoryOrder = JSON.parse(settings.wiki_category_order); } catch { categoryOrder = null; }
    }
    return NextResponse.json({ pages, categoryOrder });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve wiki pages" }, { status: 500 });
  }
}

// Real uploaded file attachments (PDFs/screenshots) — same runtime-upload
// pattern as guide/journal/chat (public/uploads/<feature>/, streamed by
// app.js/server.js's generic /uploads/* interceptor, no server change
// needed). Separate from the older inline-base64-image-in-markdown feature,
// which only ever handled images pasted into the article body and can't do
// PDFs at all — see schema.sql's note on wiki_pages.attachments.
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024; // 20MB, matches the guide feature
const ALLOWED_TYPES = ['application/pdf'];

function isAllowedFile(file) {
  return file.type?.startsWith('image/') || ALLOWED_TYPES.includes(file.type);
}

async function saveWikiAttachment(file, pageId, request) {
  const bytes = await file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const uploadDir = path.join(process.cwd(), 'public', 'uploads', 'wiki');
  await fs.promises.mkdir(uploadDir, { recursive: true });

  const ext = path.extname(file.name || '') || (file.type === 'application/pdf' ? '.pdf' : '');
  const safePageId = String(pageId || Date.now()).replace(/[^a-zA-Z0-9_]/g, '');
  const filename = `${safePageId}-${Date.now()}${ext}`;
  await fs.promises.writeFile(path.join(uploadDir, filename), buffer);

  const host = request.headers.get('host') || 'localhost:3000';
  const protocol = request.headers.get('x-forwarded-proto') || 'http';
  return {
    url: `${protocol}://${host}/uploads/wiki/${filename}`,
    name: file.name || filename
  };
}

// Create or update a wiki page, OR (multipart/form-data) add/remove a file
// attachment on an existing page.
export async function POST(request) {
  try {
    const contentType = request.headers.get('content-type') || '';

    if (contentType.includes('multipart/form-data')) {
      const auth = await requirePermission(request, 'wiki');
      if (auth.error) return auth.error;

      const formData = await request.formData();
      const id = formData.get('id');
      const action = formData.get('action');

      if (!id) {
        return NextResponse.json({ error: "Save the page before attaching files." }, { status: 400 });
      }

      if (action === 'add_attachment') {
        const file = formData.get('file');
        if (!file || typeof file === 'string' || !file.size) {
          return NextResponse.json({ error: "No file provided" }, { status: 400 });
        }
        if (!isAllowedFile(file)) {
          return NextResponse.json({ error: "Only images and PDF files are allowed" }, { status: 400 });
        }
        if (file.size > MAX_ATTACHMENT_BYTES) {
          return NextResponse.json({ error: "Attachment must be smaller than 20MB" }, { status: 400 });
        }
        const attachment = await saveWikiAttachment(file, id, request);
        const saved = await db.addWikiAttachment(id, attachment);
        if (!saved) {
          return NextResponse.json({ error: "Could not save attachment (database unreachable). Please try again shortly." }, { status: 500 });
        }
        await logAudit(auth.user, { area: 'wiki', action: 'add attachment', target: `page ${id}`, detail: file.name });
        return NextResponse.json({ success: true, pages: await db.getWikiPages() });
      }

      if (action === 'remove_attachment') {
        const url = formData.get('url');
        if (!url) return NextResponse.json({ error: "Missing attachment url" }, { status: 400 });
        const removed = await db.removeWikiAttachment(id, url);
        if (!removed) {
          return NextResponse.json({ error: "Could not remove attachment (database unreachable). Please try again shortly." }, { status: 500 });
        }
        await logAudit(auth.user, { area: 'wiki', action: 'remove attachment', target: `page ${id}` });
        return NextResponse.json({ success: true, pages: await db.getWikiPages() });
      }

      return NextResponse.json({ error: "Unknown action" }, { status: 400 });
    }

    const auth = await requirePermission(request, 'wiki');
    if (auth.error) return auth.error;

    const body = await request.json();
    const { id, title, category, content, parentId, action, revisionIndex, orderedIds, categories, oldCategory, newCategory } = body;

    // Staff "Import Steamworks pages" button: writes the bundled plugin docs
    // (src/lib/wiki-seeds, generated from docs/wiki-steamworks) into the wiki.
    // Existing pages with the same slug are updated (old text kept as a
    // revision), new ones get a fixed id so a burst of inserts can't collide
    // on saveWikiPage's Date.now() ids. Only a brand-new main page notifies
    // players - one bell entry instead of one per page.
    if (action === 'import_seed') {
      const seeds = { steamworks: steamworksSeed, additions: additionsSeed, power: powerSeed, kitchen: kitchenSeed, wildlife: wildlifeSeed, sakagura: sakaguraSeed, kura: kuraSeed, satchel: satchelSeed, zukan: zukanSeed, gouw: gouwSeed };
      const seed = seeds[body.seed];
      if (!seed) return NextResponse.json({ error: "Unknown seed" }, { status: 400 });
      const before = await db.getWikiPages();
      const idOf = (slug) => before.find(p => p.slug === slug)?.id || `${body.seed}-${slug}`.slice(0, 50);
      const clash = seed.pages.find(sp => before.some(p => p.slug === sp.slug && p.category !== sp.category));
      if (clash) {
        return NextResponse.json({ error: `A page "${clash.title}" (${clash.slug}) already exists in another category - rename it first.` }, { status: 409 });
      }
      let created = 0, updated = 0;
      for (const [i, sp] of seed.pages.entries()) {
        const exists = before.some(p => p.slug === sp.slug);
        await db.saveWikiPage({
          id: idOf(sp.slug),
          slug: sp.slug,
          title: sp.title,
          category: sp.category,
          content: sp.content,
          parentId: sp.parent ? idOf(sp.parent) : null,
          updatedBy: auth.user.mcUsername || auth.user.email || 'Staff',
          silent: !(i === 0 && !exists),
        });
        exists ? updated++ : created++;
      }
      const after = await db.getWikiPages();
      const missing = seed.pages.filter(sp => !after.some(p => p.slug === sp.slug)).map(sp => sp.title);
      if (missing.length) {
        return NextResponse.json({ error: `Could not save: ${missing.join(', ')} (database unreachable?)`, pages: after }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'wiki', action: 'import pages', target: body.seed, detail: `${created} created, ${updated} updated` });
      return NextResponse.json({ success: true, created, updated, pages: after });
    }

    // Renames a category across every page inside it — used both by the
    // "Manage Categories" rename action and the "strip numbering" bulk
    // cleanup button.
    if (action === 'rename_category') {
      if (!oldCategory || !newCategory || !newCategory.trim()) {
        return NextResponse.json({ error: "Missing oldCategory/newCategory" }, { status: 400 });
      }
      const result = await db.renameWikiCategory(oldCategory, newCategory.trim());
      if (!result.success) {
        return NextResponse.json({ error: "Could not rename the category (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'wiki', action: 'rename category', target: `${oldCategory} -> ${newCategory.trim()}` });
      return NextResponse.json({ success: true, count: result.count, pages: await db.getWikiPages() });
    }

    // Handle drag-reorder of the chapter (category) chips themselves
    if (action === 'reorder_categories') {
      if (!Array.isArray(categories) || categories.length === 0) {
        return NextResponse.json({ error: "Missing categories" }, { status: 400 });
      }
      const saved = await db.saveSettings({ wiki_category_order: JSON.stringify(categories) });
      if (!saved) {
        return NextResponse.json({ error: "Could not save the new category order (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'wiki', action: 'reorder categories', target: 'Wiki chapters' });
      return NextResponse.json({ success: true, categoryOrder: categories });
    }

    // Handle drag-reorder of sibling pages in the wiki index view
    if (action === 'reorder') {
      if (!Array.isArray(orderedIds) || orderedIds.length === 0) {
        return NextResponse.json({ error: "Missing orderedIds" }, { status: 400 });
      }
      const saved = await db.reorderWikiPages(orderedIds);
      if (!saved) {
        return NextResponse.json({ error: "Could not save the new order (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'wiki', action: 'reorder pages', target: 'Wiki topics' });
      return NextResponse.json({ success: true, pages: await db.getWikiPages() });
    }

    // Handle revision rollback action
    if (action === 'restore_revision') {
      if (!id || revisionIndex === undefined) {
        return NextResponse.json({ error: "Missing parameters for revision rollback" }, { status: 400 });
      }
      const restoredPage = await db.restoreWikiRevision(id, revisionIndex);
      if (restoredPage) {
        await logAudit(auth.user, { area: 'wiki', action: 'restore revision', target: restoredPage.title || `page ${id}` });
        return NextResponse.json({ success: true, page: restoredPage });
      } else {
        return NextResponse.json({ error: "Failed to restore revision" }, { status: 500 });
      }
    }

    if (!title || !category || !content) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
    }

    const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');

    const newPage = {
      id: id || undefined,
      slug,
      title,
      category,
      content,
      parentId: parentId || null,
      // Real session identity now that this route checks one, rather than
      // trusting a client-sent updatedBy string (anyone could previously
      // claim to be any staff member in the "Last edited by" attribution).
      updatedBy: auth.user.mcUsername || auth.user.email || "Staff"
    };

    await db.saveWikiPage(newPage);
    await logAudit(auth.user, { area: 'wiki', action: id ? 'update' : 'create', target: title, detail: category });
    return NextResponse.json({ success: true, page: newPage });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save wiki page" }, { status: 500 });
  }
}

// Delete a wiki page
export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'wiki');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const category = searchParams.get('category');

    // Whole-category delete — separate from single-page delete below,
    // used by the staff "Manage Categories" widget.
    if (category) {
      const result = await db.deleteWikiCategory(category);
      if (!result.success) {
        return NextResponse.json({ error: "Could not delete the category (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'wiki', action: 'delete category', target: category, detail: `${result.count} pages removed` });
      return NextResponse.json({ success: true, count: result.count, pages: await db.getWikiPages() });
    }

    if (!id) {
      return NextResponse.json({ error: "Missing page ID or category" }, { status: 400 });
    }

    const doomedTitle = (await db.getWikiPages()).find(p => p.id === id)?.title;
    const deleted = await db.deleteWikiPage(id);
    if (!deleted) {
      return NextResponse.json({ error: "Could not delete the page (database unreachable, or it was already gone). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'wiki', action: 'delete', target: doomedTitle || `page ${id}` });
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete wiki page" }, { status: 500 });
  }
}
