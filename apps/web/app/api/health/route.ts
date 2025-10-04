import { NextResponse } from 'next/server';

export async function GET() {
  return NextResponse.json({ ok: true, service: 'ai-ant-farm', time: new Date().toISOString() });
}
