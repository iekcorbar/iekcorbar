import { db } from '@/lib/db';
import { markdownToPlainText } from '@/lib/markdown';
import NewsClient from './NewsClient';

// Server Component wrapper — see events/page.js's comment for why this
// split exists. generateMetadata produces real per-post Open Graph tags
// for a pasted share link (?post=<id>, see NewsClient's copyShareLink).
export async function generateMetadata({ searchParams }) {
  const { post: postId } = await searchParams;
  if (!postId) return {};

  const posts = await db.getNewsPosts();
  const post = posts.find(p => String(p.id) === String(postId));
  if (!post) return {};

  const kind = post.type === 'upcoming' ? 'Upcoming' : 'News';
  const title = `${post.title} — Iekcorbar ${kind}`;
  const description = markdownToPlainText(post.content);

  return {
    title,
    description,
    openGraph: { title: post.title, description },
    twitter: { card: 'summary', title: post.title, description }
  };
}

export default function NewsPage() {
  return <NewsClient />;
}
