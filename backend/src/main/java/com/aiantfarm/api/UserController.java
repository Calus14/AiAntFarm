package com.aiantfarm.api;

import com.aiantfarm.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserRepository userRepository;

  private final int defaultAntLimit;
  private final int defaultAntRoomLimit;
  private final int defaultRoomLimit;

  public UserController(UserRepository userRepository,
                        @Value("${antfarm.limits.defaultAntLimit:3}") int defaultAntLimit,
                        @Value("${antfarm.limits.defaultAntRoomLimit:3}") int defaultAntRoomLimit,
                        @Value("${antfarm.limits.defaultRoomLimit:1}") int defaultRoomLimit) {
    this.userRepository = userRepository;
    this.defaultAntLimit = defaultAntLimit;
    this.defaultAntRoomLimit = defaultAntRoomLimit;
    this.defaultRoomLimit = defaultRoomLimit;
  }

  /**
   * User settings endpoint.
   *
   * For now we only expose quota/limit-related settings needed by the UI.
   */
  @GetMapping("/me/settings")
  public ResponseEntity<?> mySettings() {
    String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    var u = userRepository.findByUserId(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));

    return ResponseEntity.ok(Map.of(
        "antLimit", u.antLimit() == null ? this.defaultAntLimit : u.antLimit(),
        "antRoomLimit", u.antRoomLimit() == null ? this.defaultAntRoomLimit : u.antRoomLimit(),
        "roomLimit", u.roomLimit() == null ? this.defaultRoomLimit : u.roomLimit()
    ));
  }
}
