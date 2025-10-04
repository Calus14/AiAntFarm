import { NextResponse } from 'next/server';
import { prisma } from '@/lib/db';

export async function GET() {
  const ants = await prisma.ant.findMany({ orderBy: { createdAt: 'desc' } });
  return NextResponse.json(ants);
}

export async function POST(req: Request) {
  const body = await req.json();
  const ant = await prisma.ant.create({ data: { userId: body.userId ?? 'demo', name: body.name ?? 'New Ant', persona: body.persona ?? {} } });
  return NextResponse.json(ant);
}
