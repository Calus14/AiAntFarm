package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.service.IRoomService;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/rooms")
@Slf4j
public class RoomController {

  private final IRoomService roomService;

  public RoomController(IRoomService roomService) {
    this.roomService = roomService;
  }

  private static final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  // Keep emitters from living forever; clients should reconnect.
  private static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
  private static final long SSE_KEEPALIVE_MS = TimeUnit.SECONDS.toMillis(20);

  // Single scheduler for keepalives.
  private static final ScheduledExecutorService SSE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "sse-keepalive");
    t.setDaemon(true);
    return t;
  });

  static {
    SSE_SCHEDULER.scheduleAtFixedRate(RoomController::sendKeepalives,
        SSE_KEEPALIVE_MS, SSE_KEEPALIVE_MS, TimeUnit.MILLISECONDS);
  }

  private static void sendKeepalives() {
    // Comment-only keepalive so the client stays connected.
    for (var entry : emitters.entrySet()) {
      var list = entry.getValue();
      if (list == null || list.isEmpty()) continue;
      for (var e : list) {
        try {
          // A leading ':' line is a comment in SSE.
          e.send(": keepalive\n\n");
        } catch (Exception ex) {
          list.remove(e);
          try { e.complete(); } catch (Exception ignored) { }
        }
      }
    }
  }

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
    var emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitters.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    log.debug("SSE connect roomId={} emitters={}", roomId, emitters.get(roomId).size());

    // Send an initial comment so the client sees data quickly without having to parse a fake JSON payload.
    try {
      emitter.send(": connected\n\n");
    } catch (IOException ignored) { }

    Runnable cleanup = () -> {
      var list = emitters.get(roomId);
      if (list != null) {
        list.remove(emitter);
        if (list.isEmpty()) {
          emitters.remove(roomId);
        }
      }
      log.debug("SSE disconnect roomId={} emitters={}", roomId, emitters.getOrDefault(roomId, new CopyOnWriteArrayList<>()).size());
    };

    emitter.onCompletion(cleanup);
    emitter.onTimeout(() -> {
      try { emitter.complete(); } catch (Exception ignored) { }
      cleanup.run();
    });
    emitter.onError((ex) -> {
      try { emitter.completeWithError(ex); } catch (Exception ignored) { }
      cleanup.run();
    });

    return emitter;
  }

  @PostMapping("/{roomId}/messages")
  public ResponseEntity<?> post(@PathVariable String roomId,
                                @RequestBody PostMessageRequest req) {
    String user = currentUserId();
    try {
      var dto = roomService.postMessage(user, roomId, req);

      var list = emitters.getOrDefault(roomId, new CopyOnWriteArrayList<>());
      int sent = 0;
      for (var e : list) {
        try {
          e.send(SseEmitter.event().name("message").data(new SseEnvelope<>("message", dto)));
          sent++;
        } catch (Exception ex) {
          list.remove(e);
          try { e.complete(); } catch (Exception ignored) { }
        }
      }

      if (!list.isEmpty()) {
        log.debug("SSE broadcast roomId={} sent={} remainingEmitters={}", roomId, sent, list.size());
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
