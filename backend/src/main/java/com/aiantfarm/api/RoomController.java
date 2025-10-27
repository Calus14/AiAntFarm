package com.aiantfarm.api;

import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.MessageDto;
import com.aiantfarm.api.dto.PostMessageRequest;
import com.aiantfarm.api.dto.RoomDto;
import com.aiantfarm.api.dto.RoomDetailDto;
import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.Room;
import com.aiantfarm.store.MessageStore;
import com.aiantfarm.store.Page;
import com.aiantfarm.store.RoomStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

  private final RoomStore roomStore;
  private final MessageStore messageStore;

  public RoomController(RoomStore roomStore, MessageStore messageStore) {
    this.roomStore = roomStore;
    this.messageStore = messageStore;
  }

  @Value("${app.jwt.secret}")
  private String secret;

  @GetMapping
  public ResponseEntity<ListResponse<RoomDto>> list(@RequestHeader("Authorization") String auth) {
    var claims = parse(auth);
    var tenant = tenantId(claims);

    // simple single-page list for now (bump limit later if needed)
    Page<Room> page = roomStore.listByTenant(tenant, 100, null);
    List<RoomDto> items = page.items().stream().map(RoomController::toDto).toList();

    return ResponseEntity.ok(new ListResponse<>(items));
  }

  @GetMapping("/{roomId}")
  public ResponseEntity<RoomDetailDto> get(@RequestHeader("Authorization") String auth,
                                           @PathVariable String roomId) {
    var claims = parse(auth);
    var tenant = tenantId(claims);

    var optRoom = roomStore.findById(tenant, roomId);
    if (optRoom.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    var roomDto = toDto(optRoom.get());

    // first page of messages (oldest first)
    Page<Message> page = messageStore.listByRoom(tenant, roomId, 200, null);
    List<MessageDto> msgs = page.items().stream().map(RoomController::toDto).toList();

    return ResponseEntity.ok(new RoomDetailDto(roomDto, msgs));
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
    emitter.onTimeout(emitter::complete);
    return emitter;
  }

  @PostMapping("/{roomId}/messages")
  public ResponseEntity<?> post(@RequestHeader("Authorization") String auth,
                                @PathVariable String roomId,
                                @RequestBody PostMessageRequest req) {
    var claims = parse(auth);
    var tenant = tenantId(claims);
    var user = userId(claims);

    // Ensure room exists
    if (roomStore.findById(tenant, roomId).isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    // Persist via store (domain)
    var domainMsg = Message.createUser(tenant, roomId, user, req.text());
    domainMsg = messageStore.create(domainMsg);

    // Map to DTO for SSE fan-out
    var dto = toDto(domainMsg);

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
  }

  private Claims parse(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseClaimsJws(token.replace("Bearer ", ""))
            .getBody();
  }

  private static String tenantId(Claims claims) {
    Object v = claims.get("tenantId");
    if (v == null) v = claims.get("tenant");
    if (v == null) v = claims.get("tid");
    return v != null ? v.toString() : "default";
  }

  private static String userId(Claims claims) {
    // subject is fine for now; adjust if your token uses a different claim
    return claims.getSubject();
  }

  // --- DTO mappers (domain -> api dto) ---

  private static RoomDto toDto(Room r) {
    return new RoomDto(r.id(), r.name(), r.tenantId());
  }

  private static MessageDto toDto(Message m) {
    String author =
            m.authorType() == AuthorType.USER
                    ? "user"
                    : (m.authorType() == AuthorType.ANT ? "ant" : "system");
    long tsMs = m.createdAt().toEpochMilli();
    return new MessageDto(m.id(), m.roomId(), tsMs, author, m.authorUserId(), m.content());
  }
}
