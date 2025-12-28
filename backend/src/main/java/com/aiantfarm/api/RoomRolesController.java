package com.aiantfarm.api;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.Room;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.RoomRepository;
import com.aiantfarm.service.RoomAntRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@Slf4j
public class RoomRolesController {

  private final RoomAntRoleService roomAntRoleService;
  private final RoomRepository roomRepository;

  public RoomRolesController(RoomAntRoleService roomAntRoleService, RoomRepository roomRepository) {
    this.roomAntRoleService = roomAntRoleService;
    this.roomRepository = roomRepository;
  }

  private static String currentUserId() {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) throw new SecurityException("unauthorized");
    return String.valueOf(auth.getPrincipal());
  }

  @PutMapping("/{roomId}/scenario")
  public ResponseEntity<?> updateScenario(@PathVariable String roomId, @RequestBody UpdateRoomScenarioRequest req) {
    String userId = currentUserId();
    try {
      Room room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("room not found"));
      if (room.createdByUserId() == null || !room.createdByUserId().equals(userId)) {
        return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
      }

      String scenarioText = req == null || req.scenarioText() == null ? "" : req.scenarioText();
      Room updated = room.withScenarioText(scenarioText);
      roomRepository.update(updated);
      return ResponseEntity.ok(new RoomDto(updated.id(), updated.name(), updated.createdByUserId(), updated.scenarioText(), updated.createdAt().toString()));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/{roomId}/room-roles")
  public ResponseEntity<?> list(@PathVariable String roomId) {
    String userId = currentUserId();
    try {
      return ResponseEntity.ok(roomAntRoleService.list(userId, roomId));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
    }
  }

  @PostMapping("/{roomId}/room-roles")
  public ResponseEntity<?> create(@PathVariable String roomId, @RequestBody CreateRoomAntRoleRequest req) {
    String userId = currentUserId();
    try {
      RoomAntRoleDto dto = roomAntRoleService.create(userId, roomId, req);
      return ResponseEntity.status(201).body(dto);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @PutMapping("/{roomId}/room-roles/{roleId}")
  public ResponseEntity<?> update(@PathVariable String roomId, @PathVariable String roleId, @RequestBody UpdateRoomAntRoleRequest req) {
    String userId = currentUserId();
    try {
      RoomAntRoleDto dto = roomAntRoleService.update(userId, roomId, roleId, req);
      return ResponseEntity.ok(dto);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{roomId}/room-roles/{roleId}")
  public ResponseEntity<?> delete(@PathVariable String roomId, @PathVariable String roleId) {
    String userId = currentUserId();
    try {
      roomAntRoleService.delete(userId, roomId, roleId);
      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
    }
  }
}
