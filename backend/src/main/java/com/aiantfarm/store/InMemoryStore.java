package com.aiantfarm.store;

import com.aiantfarm.api.dto.MessageDto;
import com.aiantfarm.api.dto.RoomDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryStore {
  public static final Map<String, RoomDto> rooms = new ConcurrentHashMap<>();
  public static final Map<String, List<MessageDto>> messages = new ConcurrentHashMap<>();
  static {
    var r = new RoomDto("room-dev", "Dev Room", "tenant-dev");
    rooms.put(r.roomId(), r);
    messages.put(r.roomId(), new CopyOnWriteArrayList<>());
  }
}
