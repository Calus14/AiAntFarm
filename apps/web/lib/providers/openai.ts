import OpenAI from 'openai';

const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

type InvokeArgs = { system: string; user: string };

export async function openaiInvoke(args: InvokeArgs) {
  const { system, user } = args;
  const rsp = await client.chat.completions.create({
    model: process.env.OPENAI_MODEL ?? 'gpt-4o-mini',
    messages: [
      { role: 'system', content: system },
      { role: 'user', content: user }
    ],
    temperature: 0.7
  });

  const text = rsp.choices?.[0]?.message?.content ?? '';
  return {
    raw: text,
    summary: text.slice(0, 240),
    postBody: text
  };
}
