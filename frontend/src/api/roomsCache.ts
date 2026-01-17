import { apiClient } from './client';
import type { ListResponse, RoomDto } from './dto';

const TTL_MS = 60_000;

let roomsCache: RoomDto[] = [];
let roomsByIdCache: Record<string, string> = {};
let lastFetchMs = 0;
let inFlight: Promise<RoomDto[]> | null = null;

function setCache(rooms: RoomDto[]) {
  roomsCache = rooms;
  roomsByIdCache = Object.fromEntries(rooms.map((r) => [r.roomId, r.name]));
  lastFetchMs = Date.now();
}

export async function getRoomsCached(opts?: { force?: boolean }): Promise<RoomDto[]> {
  const force = !!opts?.force;
  const fresh = Date.now() - lastFetchMs < TTL_MS;

  if (!force && fresh && roomsCache.length > 0) {
    return roomsCache;
  }

  if (inFlight) return inFlight;

  inFlight = (async () => {
    try {
      const res = await apiClient.get<ListResponse<RoomDto>>('/api/v1/rooms');
      const items = (res.data as any)?.items;
      const rooms: RoomDto[] = Array.isArray(items) ? items : [];
      setCache(rooms);
      return rooms;
    } finally {
      inFlight = null;
    }
  })();

  return inFlight;
}

export function getRoomName(roomId: string): string | undefined {
  return roomsByIdCache[roomId];
}

export function startRoomsCachePolling(intervalMs = TTL_MS): () => void {
  const id = setInterval(() => {
    void getRoomsCached({ force: true });
  }, intervalMs);
  return () => clearInterval(id);
}
