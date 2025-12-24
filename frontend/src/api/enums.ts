export enum SseEnvelopeType {
  Message = 'message',
  Heartbeat = 'heartbeat',
  // future: Join = 'join', Leave = 'leave', Typing = 'typing'
}

export enum AiModel {
  MOCK = 'MOCK',
  GPT_4O_MINI = 'GPT_4O_MINI',
  GROK_2_MINI = 'GROK_2_MINI',
}

export type SseEnvelope<T> = {
  type?: string;
  payload?: T;
};
