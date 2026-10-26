import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { ACHIEVEMENT_SLUGS } from '@/lib/achievements';

// Journal entries are private to the owner, or visible to an accepted friend
// (see the social-journal-friends-features memory). Every handler checks the
// real session cookie instead of trusting a client-sent email — GET also
// accepts a `username` param (resolved to an email server-side) so a
// friend's profile page can request someone else's logbook without ever
// needing to know their email, matching the privacy stance already taken by
// api/members/route.js (which never exposes raw emails either).
async function requireOwner(request, email) {
  if (!email) {
    return { error: NextResponse.json({ error: "Missing email" }, { status: 400 }) };
  }
  const sessionUser = await getSessionUser(request);
  if (!sessionUser || sessionUser.email.toLowerCase() !== email.toLowerCase()) {
    return { error: NextResponse.json({ error: "Not authenticated for this account" }, { status: 401 }) };
  }
  return { user: sessionUser };
}

export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const emailParam = searchParams.get('email');
    const usernameParam = searchParams.get('username');

    const sessionUser = await getSessionUser(request);
    if (!sessionUser) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }

    let targetEmail = emailParam;
    if (!targetEmail && usernameParam) {
      const allUsers = await db.getUsers();
      const target = allUsers.find(u => u.mcUsername && u.mcUsername.toLowerCase() === usernameParam.toLowerCase());
      if (!target) return NextResponse.json({ error: "Player not found" }, { status: 404 });
      targetEmail = target.email;
    }
    if (!targetEmail) {
      return NextResponse.json({ error: "Missing email or username" }, { status: 400 });
    }

    const isOwner = sessionUser.email.toLowerCase() === targetEmail.toLowerCase();
    if (!isOwner) {
      const friends = await db.areFriends(sessionUser.email, targetEmail);
      if (!friends) {
        return NextResponse.json({ error: "You can only view your own logbook, or a friend's." }, { status: 403 });
      }
    }

    const entries = await db.getJournalEntries(targetEmail);
    // Never leak the owner's email to a friend viewer — they only ever
    // interacted with this person via their mcUsername.
    const shaped = isOwner ? entries : entries.map(({ userEmail, ...e }) => e);
    return NextResponse.json({ entries: shaped });
  } catch (err) {
    console.error("Journal GET error:", err);
    return NextResponse.json({ error: "Failed to load journal entries" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const contentType = request.headers.get('content-type') || '';
    let id, email, entryDate, content, file, removeImage, action, targetUsername;

    if (contentType.includes('multipart/form-data')) {
      const formData = await request.formData();
      id = formData.get('id') || undefined;
      email = formData.get('email');
      entryDate = formData.get('entryDate');
      content = formData.get('content');
      file = formData.get('file');
      removeImage = formData.get('removeImage') === 'true';
    } else {
      const body = await request.json();
      ({ id, email, entryDate, content, removeImage, action, targetUsername } = body);
    }

    const auth = await requireOwner(request, email);
    if (auth.error) return auth.error;

    // --- Share a page with a friend: notifies them, doesn't touch the entry itself ---
    if (action === 'share') {
      if (!id || !targetUsername) {
        return NextResponse.json({ error: "Missing entry id or target username" }, { status: 400 });
      }
      const entries = await db.getJournalEntries(email);
      const entry = entries.find(e => e.id === id);
      if (!entry) return NextResponse.json({ error: "Entry not found" }, { status: 404 });

      const allUsers = await db.getUsers();
      const target = allUsers.find(u => u.mcUsername && u.mcUsername.toLowerCase() === targetUsername.toLowerCase());
      if (!target) return NextResponse.json({ error: "Player not found" }, { status: 404 });

      const friends = await db.areFriends(email, target.email);
      if (!friends) {
        return NextResponse.json({ error: "You can only share logbook pages with friends" }, { status: 403 });
      }

      await db.createUserNotification({
        recipientEmail: target.email,
        type: 'journal_share',
        message: `${auth.user.mcUsername} shared a logbook page with you.`,
        link: `/members/${auth.user.mcUsername}?journal=${id}`
      });

      return NextResponse.json({ success: true });
    }

    // --- Real external sharing: mint (or reuse) a public share token for
    // this entry so "Share Externally" can hand navigator.share() a real
    // URL instead of raw diary text with nowhere for it to point — see the
    // new public GET /api/journal/shared/[token] route + /journal/shared/
    // [token] page, neither of which requires a session. ---
    if (action === 'get-share-link') {
      if (!id) return NextResponse.json({ error: "Missing entry id" }, { status: 400 });
      const entries = await db.getJournalEntries(email);
      const entry = entries.find(e => e.id === id);
      if (!entry) return NextResponse.json({ error: "Entry not found" }, { status: 404 });

      let token = entry.shareToken;
      if (!token) {
        token = crypto.randomBytes(16).toString('hex');
        const saved = await db.setJournalEntryShareToken(id, email, token);
        if (!saved) {
          return NextResponse.json({ error: "Could not create a share link (database unreachable). Please try again shortly." }, { status: 500 });
        }
      }

      const host = request.headers.get('host') || 'localhost:3000';
      const protocol = request.headers.get('x-forwarded-proto') || 'http';
      return NextResponse.json({ success: true, shareUrl: `${protocol}://${host}/journal/shared/${token}` });
    }

    // --- Revoke a previously-created share link ---
    if (action === 'unshare') {
      if (!id) return NextResponse.json({ error: "Missing entry id" }, { status: 400 });
      await db.setJournalEntryShareToken(id, email, null);
      return NextResponse.json({ success: true });
    }

    if (!entryDate || !content || !content.trim()) {
      return NextResponse.json({ error: "Date and entry text are required" }, { status: 400 });
    }

    let imageUrl;
    if (id && !file && !removeImage) {
      // Editing an existing entry without touching the image — keep whatever it had.
      const existingEntries = await db.getJournalEntries(email);
      const existing = existingEntries.find(e => e.id === id);
      imageUrl = existing ? existing.imageUrl : null;
    } else {
      imageUrl = null;
    }

    if (file && typeof file !== 'string' && file.size > 0) {
      const bytes = await file.arrayBuffer();
      const buffer = Buffer.from(bytes);

      const uploadDir = path.join(process.cwd(), 'public', 'uploads', 'journal');
      await fs.promises.mkdir(uploadDir, { recursive: true });

      const sanitizedEmail = email.replace(/[^a-zA-Z0-9_]/g, '_');
      const filename = `${sanitizedEmail}-${Date.now()}.png`;
      const filePath = path.join(uploadDir, filename);
      await fs.promises.writeFile(filePath, buffer);

      const host = request.headers.get('host') || 'localhost:3000';
      const protocol = request.headers.get('x-forwarded-proto') || 'http';
      imageUrl = `${protocol}://${host}/uploads/journal/${filename}`;
    }

    const isNewEntry = !id;
    const finalId = id || String(Date.now());
    const saved = await db.saveJournalEntry({ id: finalId, email, entryDate, content: content.trim(), imageUrl });
    if (!saved) {
      return NextResponse.json({ error: "Could not save journal entry (database unreachable). Please try again shortly." }, { status: 500 });
    }
    if (isNewEntry && auth.user.mcUsername) {
      db.awardAchievement(auth.user.mcUsername, ACHIEVEMENT_SLUGS.FIRST_JOURNAL_ENTRY).catch(() => {});
    }

    const entries = await db.getJournalEntries(email);
    return NextResponse.json({ success: true, entries, id: finalId });
  } catch (err) {
    console.error("Journal POST error:", err);
    return NextResponse.json({ error: "Failed to save journal entry" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const email = searchParams.get('email');

    const auth = await requireOwner(request, email);
    if (auth.error) return auth.error;

    if (!id) {
      return NextResponse.json({ error: "Missing entry id" }, { status: 400 });
    }

    await db.deleteJournalEntry(id, email);
    const entries = await db.getJournalEntries(email);
    return NextResponse.json({ success: true, entries });
  } catch (err) {
    console.error("Journal DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete journal entry" }, { status: 500 });
  }
}
