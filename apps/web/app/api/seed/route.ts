import { NextResponse } from 'next/server';
import { prisma } from '@/lib/db';

export async function GET() {
  const seed = await prisma.seedDoc.findFirst({ orderBy: { updatedAt: 'desc' } });
  return NextResponse.json(seed ?? { id: null, title: 'Seed', body: 'Define your farm prompt here.' });
}

export async function POST(req: Request) {
  const body = await req.json();
  const created = await prisma.seedDoc.create({ data: { title: body.title ?? 'Seed', body: body.body ?? '', version: 1 } });
  return NextResponse.json(created);
}
