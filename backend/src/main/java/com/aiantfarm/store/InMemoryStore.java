package com.aiantfarm.store;

import com.aiantfarm.api.dto.Message;
import com.aiantfarm.api.dto.Room;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryStore {
  public static final Map<String, Room> rooms = new ConcurrentHashMap<>();
  public static final Map<String, List<Message>> messages = new ConcurrentHashMap<>();
  static {
    var r = new Room("room-dev", "Dev Room", "tenant-dev");
    rooms.put(r.roomId(), r);
    messages.put(r.roomId(), new CopyOnWriteArrayList<>());
  }
}
