import { NextResponse } from 'next/server';
import { prisma } from '@/lib/db';
import { openaiInvoke } from '@/lib/providers/openai';

export async function POST(req: Request) {
  const body = await req.json();
  const { antId, prompt } = body;
  const ant = await prisma.ant.findUnique({ where: { id: antId } });
  if (!ant) return NextResponse.json({ error: 'Ant not found' }, { status: 404 });

  const run = await prisma.antRun.create({ data: { antId, status: 'running', seedDocVersion: 1, inputParams: {}, startedAt: new Date() } });

  const result = await openaiInvoke({ system: 'You are an ant in an ant farm.', user: prompt ?? 'Introduce yourself.' });

  await prisma.antRun.update({ where: { id: run.id }, data: { status: 'succeeded', finishedAt: new Date(), outputSummary: result.summary } });
  const post = await prisma.forumPost.create({ data: { antId, runId: run.id, content: result.postBody, metadata: {} } });

  return NextResponse.json({ runId: run.id, forumPostId: post.id, modelOutput: result });
}
