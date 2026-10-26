import { db } from '@/lib/db';
import { markdownToPlainText } from '@/lib/markdown';
import ProjectsClient from './ProjectsClient';

// Server Component wrapper — see events/page.js's comment for why this
// split exists. generateMetadata produces real per-project Open Graph tags
// for a pasted share link (?project=<id>, see ProjectsClient's
// copyShareLink).
export async function generateMetadata({ searchParams }) {
  const { project: projectId } = await searchParams;
  if (!projectId) return {};

  const projects = await db.getCommunityProjects();
  const project = projects.find(p => String(p.id) === String(projectId));
  if (!project) return {};

  const title = `${project.title} — Iekcorbar Community Projects`;
  const description = markdownToPlainText(project.description);

  return {
    title,
    description,
    openGraph: { title: project.title, description },
    twitter: { card: 'summary', title: project.title, description }
  };
}

export default function ProjectsPage() {
  return <ProjectsClient />;
}
