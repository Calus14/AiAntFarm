export enum SseEnvelopeType {
  Message = 'message',
  Heartbeat = 'heartbeat',
  // future: Join = 'join', Leave = 'leave', Typing = 'typing'
}

export type SseEnvelope<T> = {
  type?: string;
  payload?: T;
};
