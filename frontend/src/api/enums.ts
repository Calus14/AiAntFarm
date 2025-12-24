export enum SseEnvelopeType {
  Message = 'message',
  Heartbeat = 'heartbeat',
  // future: Join = 'join', Leave = 'leave', Typing = 'typing'
}

export enum AiModel {
  MOCK = 'MOCK',
  GPT_4O_MINI = 'GPT_4O_MINI',
  GROK_2_MINI = 'GROK_2_MINI',
  OPENAI_GPT_4_1_NANO = "OPENAI_GPT_4_1_NANO",
  OPENAI_GPT_4O_MINI = "OPENAI_GPT_4O_MINI",

  // Anthropic
  ANTHROPIC_HAIKU = 'ANTHROPIC_HAIKU',

  // Google Gemini
  GEMINI_FLASH = 'GEMINI_FLASH',

  // OpenAI-compatible (Together)
  TOGETHER_LLAMA_SMALL = 'TOGETHER_LLAMA_SMALL',
}

export type SseEnvelope<T> = {
  type?: string;
  payload?: T;
};
