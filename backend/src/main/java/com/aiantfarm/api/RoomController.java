package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.service.IRoomService;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

  private final IRoomService roomService;

  public RoomController(IRoomService roomService) {
    this.roomService = roomService;
  }

  @Value("${app.jwt.secret}")
  private String secret;

  private static final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  @PostMapping
  public ResponseEntity<?> create(@RequestHeader("Authorization") String auth,
                                  @RequestBody CreateRoomRequest req) {
    try {
      Claims claims = parse(auth);
      String userId = userId(claims);

      try {
        var dto = roomService.createRoom(userId, req);
        return ResponseEntity.status(201).body(dto);
      } catch (RoomAlreadyExistsException e) {
        return ResponseEntity.status(409).body(Map.of("error", "Room already exists"));
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().build();
      }
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping("/created")
  public ResponseEntity<ListResponse<RoomDto>> listCreated(@RequestHeader("Authorization") String auth) {
    if (auth == null || !auth.startsWith("Bearer ")) {
      return ResponseEntity.status(401).build();
    }
    try {
      Claims claims = parse(auth);
      String userId = userId(claims);

      var resp = roomService.listCreated(userId);
      return ResponseEntity.ok(resp);
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping
  public ResponseEntity<ListResponse<RoomDto>> listAll(@RequestHeader("Authorization") String auth) {
    try {
      parse(auth);
      var resp = roomService.listAll(100, null);
      return ResponseEntity.ok(resp);
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping("/{roomId}")
  public ResponseEntity<RoomDetailDto> get(@RequestHeader("Authorization") String auth,
                                           @PathVariable String roomId) {
    try {
      parse(auth); // validate token; content not required here beyond validation

      try {
        var dto = roomService.getRoomDetail(roomId);
        return ResponseEntity.ok(dto);
      } catch (ResourceNotFoundException e) {
        return ResponseEntity.notFound().build();
      }
    } catch (JwtException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping(path="/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("token") String token, @PathVariable String roomId) {
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
}
