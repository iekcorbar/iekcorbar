import { db } from '@/lib/db';
import { markdownToPlainText } from '@/lib/markdown';
import EventsClient from './EventsClient';

// Deliberately a Server Component (unlike EventsClient, which keeps the
// 'use client' interactivity) so generateMetadata below can produce real
// per-event Open Graph / Twitter card tags for a pasted share link
// (?event=<id>, see EventsClient's copyShareLink) — a link sharer expects
// Discord/WhatsApp/etc. to preview THAT event, not the generic site-wide
// title from layout.js, which is all a client-only page can ever produce
// since those platforms read the HTML response directly rather than
// running JS. Same fix, same reasoning, as journal/shared/[token]/page.js.
export async function generateMetadata({ searchParams }) {
  const { event: eventId } = await searchParams;
  if (!eventId) return {};

  const events = await db.getEvents();
  const event = events.find(e => String(e.id) === String(eventId));
  if (!event) return {};

  const title = `${event.title} — Iekcorbar Events`;
  const description = markdownToPlainText(event.description);
  const images = event.image && event.image !== 'derby' && event.image !== 'boss' ? [event.image] : undefined;

  return {
    title,
    description,
    openGraph: { title: event.title, description, images },
    twitter: { card: images ? 'summary_large_image' : 'summary', title: event.title, description, images }
  };
}

export default function EventsPage() {
  return <EventsClient />;
}
