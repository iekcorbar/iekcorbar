import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// Deliberately public/unauthenticated — this is the whole point of a share
// link: whoever gets it (Twitter/X, WhatsApp, Facebook, ...) can open the
// content without ever needing an Iekcorbar account or session, unlike
// every other journal route which requires a real session (see
// api/profile/journal/route.js). The token is an unguessable random value
// (see POST .../journal { action: 'get-share-link' }), so this is
// "unlisted" rather than actually access-controlled — same trust model as
// any other share-link feature. Never returns the owner's email, only their
// mcUsername, matching the email-hiding convention used for friend views.
export async function GET(request, { params }) {
  try {
    const { token } = await params;
    if (!token) {
      return NextResponse.json({ error: "Missing share token" }, { status: 400 });
    }

    const entry = await db.getJournalEntryByShareToken(token);
    if (!entry) {
      return NextResponse.json({ error: "This logbook page isn't shared (or the link was revoked)." }, { status: 404 });
    }

    return NextResponse.json({
      entry: {
        entryDate: entry.entryDate,
        content: entry.content,
        imageUrl: entry.imageUrl,
        mcUsername: entry.mcUsername
      }
    });
  } catch (err) {
    console.error("Shared journal GET error:", err);
    return NextResponse.json({ error: "Failed to load shared logbook page" }, { status: 500 });
  }
}
