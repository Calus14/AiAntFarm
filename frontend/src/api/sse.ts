export interface SseEvent {
  event: string | null;
  data: string;
}

interface SseParseState {
  buffer: string;
}

function initState(): SseParseState {
  return { buffer: '' };
}

function parseSseFromBuffer(state: SseParseState): SseEvent[] {
  const events: SseEvent[] = [];

  // SSE events are separated by a blank line.
  while (true) {
    const delimiterIndex = state.buffer.indexOf('\n\n');
    if (delimiterIndex === -1) break;

    const rawEvent = state.buffer.slice(0, delimiterIndex);
    state.buffer = state.buffer.slice(delimiterIndex + 2);

    let eventName: string | null = null;
    const dataLines: string[] = [];

    for (const line of rawEvent.split('\n')) {
      if (!line) continue;
      if (line.startsWith(':')) continue; // comment/keepalive

      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim() || null;
        continue;
      }

      if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).trimStart());
        continue;
      }
    }

    const data = dataLines.join('\n');
    if (dataLines.length === 0) continue;
    events.push({ event: eventName, data });
  }

  return events;
}

export async function streamSse(url: string, options: {
  headers?: HeadersInit;
  signal: AbortSignal;
  onEvent: (ev: SseEvent) => void;
}): Promise<void> {
  const response = await fetch(url, {
    headers: options.headers,
    signal: options.signal,
  });

  if (!response.ok) {
    throw new Error(`SSE request failed: ${response.status} ${response.statusText}`);
  }

  if (!response.body) {
    throw new Error('SSE response has no body');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const state = initState();

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    state.buffer += decoder.decode(value, { stream: true });

    const events = parseSseFromBuffer(state);
    for (const ev of events) {
      options.onEvent(ev);
    }
  }
}
