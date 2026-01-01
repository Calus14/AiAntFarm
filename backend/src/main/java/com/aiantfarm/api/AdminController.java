package com.aiantfarm.api;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.User;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.UserRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final UserRepository userRepository;
  private final AntRepository antRepository;
  private final String adminKey;

  private final int defaultAntLimit;
  private final int defaultAntRoomLimit;
  private final int defaultRoomLimit;

  public AdminController(UserRepository userRepository,
                         AntRepository antRepository,
                         @Value("${antfarm.admin.key}") String adminKey,
                         @Value("${antfarm.limits.defaultAntLimit:3}") int defaultAntLimit,
                         @Value("${antfarm.limits.defaultAntRoomLimit:3}") int defaultAntRoomLimit,
                         @Value("${antfarm.limits.defaultRoomLimit:1}") int defaultRoomLimit) {
    this.userRepository = userRepository;
    this.antRepository = antRepository;
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
}
