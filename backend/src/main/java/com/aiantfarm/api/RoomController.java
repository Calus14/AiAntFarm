package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.store.InMemoryStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

  @Value("${app.jwt.secret}")
  private String secret;

  private Claims parse(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
      .build()
      .parseClaimsJws(token.replace("Bearer ", ""))
      .getBody();
  }

  @GetMapping
  public ResponseEntity<ListResponse<Room>> list(@RequestHeader("Authorization") String auth) {
    var claims = parse(auth);
    // TODO: filter by tenantId
    var items = new ArrayList<>(InMemoryStore.rooms.values());
    return ResponseEntity.ok(new ListResponse<>(items));
  }

  @GetMapping("/{roomId}")
  public ResponseEntity<RoomDetail> get(@RequestHeader("Authorization") String auth,
                                        @PathVariable String roomId) {
    var claims = parse(auth);
    var room = InMemoryStore.rooms.get(roomId);
    var msgs = InMemoryStore.messages.getOrDefault(roomId, List.of());
    return ResponseEntity.ok(new RoomDetail(room, msgs));
  }

  private static final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  @GetMapping(path="/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("token") String token, @PathVariable String roomId) {
    var claims = parse(token);
    var emitter = new SseEmitter(0L);
    emitters.computeIfAbsent(roomId, k -> new ArrayList<>()).add(emitter);
    try {
      emitter.send(SseEmitter.event().name("message").data("{}"));
    } catch (Exception ignored) {}
    emitter.onCompletion(() -> emitters.getOrDefault(roomId, List.of()).remove(emitter));
    emitter.onTimeout(() -> emitter.complete());
    return emitter;
  }

  @PostMapping("/{roomId}/messages")
  public ResponseEntity<?> post(@RequestHeader("Authorization") String auth,
                                @PathVariable String roomId,
                                @RequestBody PostMessageRequest req) {
    var claims = parse(auth);
    var id = UUID.randomUUID().toString();
    var msg = new Message(id, roomId, Instant.now().toEpochMilli(), "user", claims.getSubject(), req.text());
    InMemoryStore.messages.computeIfAbsent(roomId, k -> new ArrayList<>()).add(msg);
    // Fan-out to SSE
    var list = emitters.getOrDefault(roomId, List.of());
    var iterator = list.iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();
      try {
        e.send(SseEmitter.event().name("message").data(msg));
      } catch (Exception ex) {
        iterator.remove();
      }
    }
    return ResponseEntity.accepted().build();
  }
}
