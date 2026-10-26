import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// Get all projects + signups
export async function GET() {
  try {
    const projects = await db.getCommunityProjects();
    const signups = await db.getProjectSignups();
    return NextResponse.json({ projects, signups });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve projects" }, { status: 500 });
  }
}

// Create or update a project (staff only)
export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_projects');
    if (auth.error) return auth.error;

    const body = await request.json();
    const { id, title, category, description, status, maxParticipants, createdBy } = body;

    if (!title || !description) {
      return NextResponse.json({ error: "Title and description are required." }, { status: 400 });
    }

    const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');

    const project = {
      id: id || undefined,
      slug,
      title,
      category: category || 'General',
      description,
      status: status || 'open',
      maxParticipants: maxParticipants ? parseInt(maxParticipants) : null,
      createdBy: createdBy || 'Staff'
    };

    const saved = await db.saveCommunityProject(project);
    if (!saved) {
      return NextResponse.json({ error: "Failed to save project (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'settings_projects', action: id ? 'update' : 'create', target: title });
    return NextResponse.json({ success: true, project });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save project" }, { status: 500 });
  }
}

// Delete a project (staff only)
export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_projects');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing project ID" }, { status: 400 });
    }

    await db.deleteCommunityProject(id);
    await logAudit(auth.user, { area: 'settings_projects', action: 'delete', target: `project ${id}` });
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete project" }, { status: 500 });
  }
}
