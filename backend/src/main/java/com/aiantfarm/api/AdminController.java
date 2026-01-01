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

  public AdminController(UserRepository userRepository,
                         AntRepository antRepository,
                         @Value("${antfarm.admin.key}") String adminKey) {
    this.userRepository = userRepository;
    this.antRepository = antRepository;
    this.adminKey = adminKey;
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
        "antLimit", u.antLimit(),
        "antRoomLimit", u.antRoomLimit(),
        "roomLimit", u.roomLimit()
    ));
  }

  @PutMapping("/users/{userId}/limits")
  public ResponseEntity<?> updateUserLimits(@RequestHeader("X-Admin-Key") String key,
                                            @PathVariable String userId,
                                            @RequestBody UpdateUserLimitsRequest req) {
    requireKey(key);
    User u = userRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("user not found"));

    Integer antLimit = req == null ? null : req.getAntLimit();
    Integer antRoomLimit = req == null ? null : req.getAntRoomLimit();
    Integer roomLimit = req == null ? null : req.getRoomLimit();

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
