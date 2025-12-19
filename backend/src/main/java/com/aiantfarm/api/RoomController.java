package com.aiantfarm.api;

import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.MessageDto;
import com.aiantfarm.api.dto.PostMessageRequest;
import com.aiantfarm.api.dto.RoomDto;
import com.aiantfarm.api.dto.RoomDetailDto;
import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.Room;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.RoomRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;

  public RoomController(RoomRepository roomRepository, MessageRepository messageRepository) {
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
  }

  @Value("${app.jwt.secret}")
  private String secret;

  @GetMapping("/created")
  public ResponseEntity<ListResponse<RoomDto>> listCreated(@RequestHeader("Authorization") String auth) {
    if (auth == null || !auth.startsWith("Bearer ")) {
      return ResponseEntity.status(401).build();
    }
    try {
      Claims claims = parse(auth);
      String userId = userId(claims);

      Page<Room> page = roomRepository.listByUserCreatedId(userId, 100, null);
      List<RoomDto> items = page.items().stream().map(RoomController::toDto).collect(Collectors.toList());

      return ResponseEntity.ok(new ListResponse<>(items));
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping("/{roomId}")
  public ResponseEntity<RoomDetailDto> get(@RequestHeader("Authorization") String auth,
                                           @PathVariable String roomId) {
    try {
      parse(auth); // validate token; content not required here beyond validation

      var optRoom = roomRepository.findById(roomId);
      if (optRoom.isEmpty()) {
        return ResponseEntity.notFound().build();
      }
      var roomDto = toDto(optRoom.get());

      Page<Message> page = messageRepository.listByRoom(roomId, 200, null);
      List<MessageDto> msgs = page.items().stream().map(RoomController::toDto).collect(Collectors.toList());

      return ResponseEntity.ok(new RoomDetailDto(roomDto, msgs));
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  private static final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  @GetMapping(path="/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("token") String token, @PathVariable String roomId) {
    // token may be a bare token or "Bearer ..." - parse handles either
    parse(token);
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
    try {
      Claims claims = parse(auth);
      String user = userId(claims);

      // Ensure room exists
      if (roomRepository.findById(roomId).isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      // Persist via store (domain) - single-tenant signature
      var domainMsg = Message.createUserMsg(roomId, user, req.text());
      domainMsg = messageRepository.create(domainMsg);

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
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  private Claims parse(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .build()
        .parseClaimsJws(token.replace("Bearer ", ""))
        .getBody();
  }

  private static String userId(Claims claims) {
    return claims.getSubject();
  }

  // --- DTO mappers (domain -> api dto) ---

  private static RoomDto toDto(Room r) {
    return new RoomDto(r.id(), r.name(), r.createdByUserId());
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
