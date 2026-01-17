package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.service.IAntService;
import com.aiantfarm.service.IRoomService;
import com.aiantfarm.domain.Message;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
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
  private final IAntService antService;

  public RoomController(IRoomService roomService, IAntService antService) {
    this.roomService = roomService;
    this.antService = antService;
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
          e.send(SseEmitter.event().comment("keepalive"));
        } catch (AsyncRequestNotUsableException | IllegalStateException ex) {
          // Normal case: client disconnected / response already closed.
          list.remove(e);
          try { e.complete(); } catch (Exception ignored) { }
        } catch (Exception ex) {
          // Any other send failure -> cleanup emitter.
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
    // IMPORTANT: For SSE, we must ensure the user is authenticated BEFORE starting the response.
    // Otherwise, Spring Security may deny after the response is committed, causing noisy logs.
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    var emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitters.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    log.debug("SSE connect roomId={} emitters={}", roomId, emitters.get(roomId).size());

    // Send an initial comment so the client sees data quickly without having to parse a fake JSON payload.
    try {
      emitter.send(SseEmitter.event().comment("connected"));
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
    String userId = currentUserId();
    String userDisplayName = currentUserDisplayName();
    try {
      var dto = roomService.postMessage(userId, userDisplayName, roomId, req);

      // Override senderName for immediate SSE broadcast (room history mapping can stay simple for now).
      var dtoWithName = new MessageDto(dto.id(), dto.roomId(), dto.ts(), dto.senderType(), dto.senderId(), userDisplayName, dto.text());
      broadcastEnvelope(roomId, new SseEnvelope<>("message", dtoWithName));

      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Broadcast a newly created domain Message to all SSE clients for the room.
   *
   * This is intentionally static so non-controller code (e.g., AntService) can publish room messages
   * without re-implementing SSE fan-out.
   */
  public static void broadcastMessage(String roomId, Message msg) {
    broadcastMessage(roomId, msg, null);
  }

  /**
   * Broadcast helper that can include a display name for the sender.
   * For Ant messages, pass the Ant's configured name so the UI can render it.
   */
  public static void broadcastMessage(String roomId, Message msg, String senderName) {
    if (roomId == null || roomId.isBlank() || msg == null) return;

    String author =
        msg.authorType() == AuthorType.USER
            ? "user"
            : (msg.authorType() == AuthorType.ANT ? "ant" : "system");

    long tsMs = msg.createdAt().toEpochMilli();
    var dto = new MessageDto(msg.id(), msg.roomId(), tsMs, author, msg.authorId(), senderName, msg.content());
    broadcastEnvelope(roomId, new SseEnvelope<>("message", dto));
  }

  private static void broadcastEnvelope(String roomId, SseEnvelope<?> env) {
    var list = emitters.getOrDefault(roomId, new CopyOnWriteArrayList<>());
    int sent = 0;
    for (var e : list) {
      try {
        e.send(SseEmitter.event().name(env.type()).data(env));
        sent++;
      } catch (AsyncRequestNotUsableException | IllegalStateException ex) {
        // Normal case: client disconnected / response already closed.
        list.remove(e);
        try { e.complete(); } catch (Exception ignored) { }
      } catch (Exception ex) {
        list.remove(e);
        try { e.complete(); } catch (Exception ignored) { }
      }
    }

    if (!list.isEmpty()) {
      log.debug("SSE broadcast roomId={} eventType={} sent={} remainingEmitters={}", roomId, env.type(), sent, list.size());
    }
  }

  @GetMapping("/{roomId}/ants")
  public ResponseEntity<ListResponse<AntRoomAssignmentDto>> listAntsInRoom(@PathVariable String roomId) {
    return ResponseEntity.ok(antService.listAntsInRoom(roomId));
  }

  @DeleteMapping("/{roomId}")
  public ResponseEntity<?> delete(@PathVariable String roomId) {
    String userId = currentUserId();
    try {
      roomService.deleteRoom(userId, roomId);
      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  private String currentUserId() {
    return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  private String currentUserDisplayName() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return null;
    Object details = auth.getDetails();
    return (details instanceof String s && !s.isBlank()) ? s : null;
  }
}
