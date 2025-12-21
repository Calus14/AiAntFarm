package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.service.IRoomService;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

  private final IRoomService roomService;

  public RoomController(IRoomService roomService) {
    this.roomService = roomService;
  }

  private static final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  @PostMapping
  public ResponseEntity<?> create(@RequestBody CreateRoomRequest req) {
    String userId = currentUserId();
    try {
      var dto = roomService.createRoom(userId, req);
      return ResponseEntity.status(201).body(dto);
    } catch (RoomAlreadyExistsException e) {
      return ResponseEntity.status(409).body(Map.of("error", "Room already exists"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @GetMapping("/created")
  public ResponseEntity<ListResponse<RoomDto>> listCreated() {
    String userId = currentUserId();
    var resp = roomService.listCreated(userId);
    return ResponseEntity.ok(resp);
  }

  @GetMapping
  public ResponseEntity<ListResponse<RoomDto>> listAll() {
    var resp = roomService.listAll(100, null);
    return ResponseEntity.ok(resp);
  }

  @GetMapping("/{roomId}")
  public ResponseEntity<RoomDetailDto> get(@PathVariable String roomId) {
    try {
      var dto = roomService.getRoomDetail(roomId);
      return ResponseEntity.ok(dto);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping(path="/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String roomId) {
    var emitter = new SseEmitter(0L);
    emitters.computeIfAbsent(roomId, k -> new ArrayList<>()).add(emitter);
    try {
      emitter.send(SseEmitter.event().name("message").data("{}"));
    } catch (Exception ignored) {}
    emitter.onCompletion(() -> emitters.getOrDefault(roomId, List.of()).remove(emitter));
    emitter.onTimeout(emitter::complete);
    return emitter;
  }

  @PostMapping("/{roomId}/messages")
  public ResponseEntity<?> post(@PathVariable String roomId,
                                @RequestBody PostMessageRequest req) {
    String user = currentUserId();
    try {
      var dto = roomService.postMessage(user, roomId, req);

      var list = emitters.getOrDefault(roomId, List.of());
      var it = list.iterator();
      while (it.hasNext()) {
        var e = it.next();
        try {
          e.send(SseEmitter.event().name("message").data(dto));
        } catch (Exception ex) {
          it.remove();
        }
      }
      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  private String currentUserId() {
    return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
