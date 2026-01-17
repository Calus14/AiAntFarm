package com.aiantfarm.api;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.User;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.UserRepository;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.RoomAntRoleRepository;
import com.aiantfarm.repository.RoomRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Slf4j
public class AdminController {

  private final UserRepository userRepository;
  private final AntRepository antRepository;
  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;
  private final RoomAntRoleRepository roomAntRoleRepository;
  private final AntRoomAssignmentRepository antRoomAssignmentRepository;
  private final String adminKey;

  private final int defaultAntLimit;
  private final int defaultAntRoomLimit;
  private final int defaultRoomLimit;

  public AdminController(UserRepository userRepository,
                         AntRepository antRepository,
                         RoomRepository roomRepository,
                         MessageRepository messageRepository,
                         RoomAntRoleRepository roomAntRoleRepository,
                         AntRoomAssignmentRepository antRoomAssignmentRepository,
                         @Value("${antfarm.admin.key}") String adminKey,
                         @Value("${antfarm.limits.defaultAntLimit:3}") int defaultAntLimit,
                         @Value("${antfarm.limits.defaultAntRoomLimit:3}") int defaultAntRoomLimit,
                         @Value("${antfarm.limits.defaultRoomLimit:1}") int defaultRoomLimit) {
    this.userRepository = userRepository;
    this.antRepository = antRepository;
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.roomAntRoleRepository = roomAntRoleRepository;
    this.antRoomAssignmentRepository = antRoomAssignmentRepository;
    this.adminKey = adminKey;
    this.defaultAntLimit = defaultAntLimit;
    this.defaultAntRoomLimit = defaultAntRoomLimit;
    this.defaultRoomLimit = defaultRoomLimit;
  }

  private void requireKey(String provided) {
    if (!adminKey.equals(provided)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
  }

  @GetMapping("/users/{userId}/limits")
  public ResponseEntity<?> getUserLimits(@RequestHeader("X-Admin-Key") String key,
                                         @PathVariable String userId) {
    requireKey(key);
    User u = userRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("user not found"));
    return ResponseEntity.ok(Map.of(
        "userId", u.id(),
        "antLimit", u.antLimit() == null ? this.defaultAntLimit : u.antLimit(),
        "antRoomLimit", u.antRoomLimit() == null ? this.defaultAntRoomLimit : u.antRoomLimit(),
        "roomLimit", u.roomLimit() == null  ? this.defaultRoomLimit : u.roomLimit()
    ));
  }

  @PutMapping("/users/{userId}/limits")
  public ResponseEntity<?> updateUserLimits(@RequestHeader("X-Admin-Key") String key,
                                            @PathVariable String userId,
                                            @RequestBody UpdateUserLimitsRequest req) {
    requireKey(key);
    User u = userRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("user not found"));

    Integer antLimit = req == null ? this.defaultAntLimit : req.getAntLimit();
    Integer antRoomLimit = req == null ? this.defaultAntRoomLimit : req.getAntRoomLimit();
    Integer roomLimit = req == null ? this.defaultRoomLimit : req.getRoomLimit();

    User updated = new User(u.id(), u.userEmail(), u.displayName(), u.createdAt(), u.active(), antLimit, antRoomLimit, roomLimit);
    userRepository.update(updated);

    return ResponseEntity.ok(Map.of("message", "User limits updated"));
  }

  /**
   * Admin-only control plane API to adjust an ant's weekly message quota.
   *
   * NOTE: Any future paid tiers / account-level quota upgrades should get a dedicated API and service.
   */
  @PutMapping("/ants/{antId}/weekly-quota")
  public ResponseEntity<?> updateAntWeeklyQuota(@RequestHeader("X-Admin-Key") String key,
                                                @PathVariable String antId,
                                                @RequestBody UpdateAntWeeklyQuotaRequest req) {
    requireKey(key);
    Ant ant = antRepository.findById(antId).orElseThrow(() -> new ResourceNotFoundException("ant not found"));

    Integer newQuota = req == null ? null : req.getMaxMessagesPerWeek();
    if (newQuota == null || newQuota < 1) {
      return ResponseEntity.badRequest().body(Map.of("error", "maxMessagesPerWeek must be >= 1"));
    }

    Ant updated = ant.withUpdated(null, null, null, null, null, null, newQuota);
    antRepository.update(updated);

    return ResponseEntity.ok(Map.of(
        "message", "Ant weekly quota updated",
        "antId", antId,
        "maxMessagesPerWeek", updated.maxMessagesPerWeek()
    ));
  }

  /**
   * Admin-only endpoint to force-set an Ant's model (useful for testing new runners).
   */
  @PutMapping("/ants/{antId}/model")
  public ResponseEntity<?> updateAntModel(@RequestHeader("X-Admin-Key") String key,
                                          @PathVariable String antId,
                                          @RequestBody UpdateAntModelRequest req) {
    requireKey(key);

    Ant ant = antRepository.findById(antId).orElseThrow(() -> new ResourceNotFoundException("ant not found"));
    AiModel newModel = req == null ? null : req.getModel();
    if (newModel == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "model required"));
    }

    // Admin endpoint: intentionally allows any AiModel, including GPT-5 variants.

    Ant updated = ant.withUpdated(null, newModel, null, null, null, null, null);
    antRepository.update(updated);

    return ResponseEntity.ok(Map.of(
        "message", "Ant model updated",
        "antId", antId,
        "model", updated.model().name()
    ));
  }

  /**
   * Admin-only endpoint to delete a room by roomId.
   *
   * Deletes room metadata and best-effort deletes room-scoped data (roles, assignments, messages).
   */
  @DeleteMapping("/rooms/{roomId}")
  public ResponseEntity<?> deleteRoom(@RequestHeader("X-Admin-Key") String key,
                                      @PathVariable String roomId) {
    requireKey(key);
    if (roomId == null || roomId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "roomId required"));
    }

    // Ensure the room exists first so we can return 404.
    roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("room not found"));

    // Best-effort cascades. For MVP, we do not attempt transactional deletes.

    // 1) Delete room roles
    try {
      var roles = roomAntRoleRepository.listByRoom(roomId);
      for (var role : roles) {
        if (role == null) continue;
        roomAntRoleRepository.delete(roomId, role.roleId());
      }
    } catch (Exception e) {
      log.warn("Admin room delete cascade failed: roles roomId={}", roomId, e);
    }

    // 2) Delete ant-room assignments for this room
    try {
      var assigns = antRoomAssignmentRepository.listByRoom(roomId);
      for (var a : assigns) {
        if (a == null) continue;
        antRoomAssignmentRepository.unassign(a.antId(), roomId);
      }
    } catch (Exception e) {
      log.warn("Admin room delete cascade failed: ant-room-assignments roomId={}", roomId, e);
    }

    // 3) Delete all messages for this room
    try {
      messageRepository.deleteAllByRoom(roomId);
    } catch (Exception e) {
      log.warn("Admin room delete cascade failed: messages roomId={}", roomId, e);
    }

    // 4) Delete the room metadata itself
    roomRepository.deleteByRoomId(roomId);

    return ResponseEntity.noContent().build();
  }

  @Data
  public static class UpdateUserLimitsRequest {
    private Integer antLimit;
    private Integer antRoomLimit;
    private Integer roomLimit;
  }

  @Data
  public static class UpdateAntWeeklyQuotaRequest {
    private Integer maxMessagesPerWeek;
  }

  @Data
  public static class UpdateAntModelRequest {
    private AiModel model;
  }
}
