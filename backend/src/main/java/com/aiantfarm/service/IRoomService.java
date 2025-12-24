package com.aiantfarm.service;

import com.aiantfarm.api.dto.*;

public interface IRoomService {
  RoomDto createRoom(String userId, CreateRoomRequest req);

  ListResponse<RoomDto> listCreated(String userId);

  ListResponse<RoomDto> listAll(int limit, String nextToken);

  RoomDetailDto getRoomDetail(String roomId);

  MessageDto postMessage(String userId, String userName, String roomId, PostMessageRequest req);
}
