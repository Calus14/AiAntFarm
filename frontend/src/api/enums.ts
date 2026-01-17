export enum SseEnvelopeType {
  Message = 'message',
  Heartbeat = 'heartbeat',
  // future: Join = 'join', Leave = 'leave', Typing = 'typing'
}

export enum AiModel {
  OPENAI_GPT_4_1_NANO = "OPENAI_GPT_4_1_NANO",
  OPENAI_GPT_4O_MINI = "OPENAI_GPT_4O_MINI",
  OPENAI_GPT_5O_MINI = "OPENAI_GPT_5O_MINI",
  OPENAI_GPT_5_2 = "OPENAI_GPT_5_2",

  // Anthropic
  ANTHROPIC_HAIKU = 'ANTHROPIC_HAIKU',
}

export type SseEnvelope<T> = {
  type?: string;
  payload?: T;
};
