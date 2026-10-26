import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// Sign up for a project
export async function POST(request) {
  try {
    const { projectId, username } = await request.json();

    if (!projectId || !username) {
      return NextResponse.json({ error: "Missing projectId or username" }, { status: 400 });
    }

    // Check if project exists
    const projects = await db.getCommunityProjects();
    const project = projects.find(p => p.id === projectId);
    if (!project) {
      return NextResponse.json({ error: "Project not found" }, { status: 404 });
    }

    // Check if project is still open or in progress
    if (project.status === 'completed') {
      return NextResponse.json({ error: "This project is already completed." }, { status: 400 });
    }

    // Check max participants
    if (project.maxParticipants) {
      const signups = (await db.getProjectSignups()).filter(s => s.projectId === projectId);
      if (signups.length >= project.maxParticipants) {
        return NextResponse.json({ error: "This project has reached maximum participants." }, { status: 400 });
      }
    }

    await db.signUpForProject(projectId, username);
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to sign up" }, { status: 500 });
  }
}

// Leave a project
export async function DELETE(request) {
  try {
    const { searchParams } = new URL(request.url);
    const projectId = searchParams.get('projectId');
    const username = searchParams.get('username');

    if (!projectId || !username) {
      return NextResponse.json({ error: "Missing projectId or username" }, { status: 400 });
    }

    await db.withdrawFromProject(projectId, username);
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to leave project" }, { status: 500 });
  }
}
