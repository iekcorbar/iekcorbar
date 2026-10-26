import { db } from '@/lib/db';
import { markdownToPlainText } from '@/lib/markdown';
import WikiClient from './WikiClient';

// Server Component wrapper — see events/page.js's comment for why this
// split exists. generateMetadata produces real per-article Open Graph tags
// for a pasted share link (?page=<slug>, see WikiClient's copyShareLink).
export async function generateMetadata({ searchParams }) {
  const { page: slug } = await searchParams;
  if (!slug) return {};

  const pages = await db.getWikiPages();
  const page = pages.find(p => p.slug === slug);
  if (!page) return {};

  const title = `${page.title} — Iekcorbar Wiki`;
  const description = markdownToPlainText(page.content);

  return {
    title,
    description,
    openGraph: { title: page.title, description },
    twitter: { card: 'summary', title: page.title, description }
  };
}

export default function WikiPage() {
  return <WikiClient />;
}
