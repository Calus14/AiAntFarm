package com.aiantfarm.api;

import com.aiantfarm.api.dto.AntDto;
import com.aiantfarm.api.dto.AssignAntToRoomRequest;
import com.aiantfarm.api.dto.CreateAntRequest;
import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.UpdateAntRequest;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.service.IAntService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ants")
@Slf4j
public class AntController {

  private final IAntService antService;

  public AntController(IAntService antService) {
    this.antService = antService;
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody CreateAntRequest req) {
    String userId = currentUserId();
    try {
      var dto = antService.createAnt(userId, req);
      return ResponseEntity.status(201).body(dto);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping
  public ResponseEntity<ListResponse<AntDto>> listMine() {
    String userId = currentUserId();
    return ResponseEntity.ok(antService.listMyAnts(userId));
  }

  @GetMapping("/{antId}")
  public ResponseEntity<?> get(@PathVariable String antId) {
    String userId = currentUserId();
    try {
      return ResponseEntity.ok(antService.getAnt(userId, antId));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    }
  }

  @PatchMapping("/{antId}")
  public ResponseEntity<?> update(@PathVariable String antId, @RequestBody UpdateAntRequest req) {
    String userId = currentUserId();
    try {
      return ResponseEntity.ok(antService.updateAnt(userId, antId, req));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/{antId}/rooms")
  public ResponseEntity<?> assignToRoom(@PathVariable String antId, @RequestBody AssignAntToRoomRequest req) {
    String userId = currentUserId();
    try {
      antService.assignToRoom(userId, antId, req);
      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{antId}/rooms/{roomId}")
  public ResponseEntity<?> unassignFromRoom(@PathVariable String antId, @PathVariable String roomId) {
    String userId = currentUserId();
    try {
      antService.unassignFromRoom(userId, antId, roomId);
      return ResponseEntity.accepted().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    }
  }

  @GetMapping("/{antId}/runs")
  public ResponseEntity<?> listRuns(@PathVariable String antId, @RequestParam(required = false) Integer limit) {
    String userId = currentUserId();
    try {
      return ResponseEntity.ok(antService.listRuns(userId, antId, limit));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    }
  }

  @PostMapping("/{antId}/runs")
  public ResponseEntity<?> runNow(@PathVariable String antId) {
    String userId = currentUserId();
    try {
      antService.runNow(userId, antId);
      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    }
  }

  // DELETE an ant (owner-only). Removes assignments and cancels scheduler.
  @DeleteMapping("/{antId}")
  public ResponseEntity<?> delete(@PathVariable String antId) {
    String userId = currentUserId();
    try {
      antService.deleteAnt(userId, antId);
      return ResponseEntity.accepted().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (SecurityException e) {
      return ResponseEntity.status(403).build();
    }
  }

  private String currentUserId() {
    return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
