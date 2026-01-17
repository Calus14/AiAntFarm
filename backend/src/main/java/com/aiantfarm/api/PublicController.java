package com.aiantfarm.api;

import com.aiantfarm.api.dto.AntDetailDto;
import com.aiantfarm.api.dto.AntDto;
import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.PublicRoomRoleDto;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.RoomAntRoleRepository;
import com.aiantfarm.repository.RoomRepository;
import com.aiantfarm.service.IAntService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only endpoints.
 *
 * WARNING: These endpoints deliberately relax some authorization checks to enable UI features.
 * Do not add write operations here.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

  private final IAntService antService;
  private final AntRepository antRepository;
  private final AntRoomAssignmentRepository assignmentRepository;
  private final RoomRepository roomRepository;
  private final RoomAntRoleRepository roomAntRoleRepository;

  public PublicController(
      IAntService antService,
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      RoomRepository roomRepository,
      RoomAntRoleRepository roomAntRoleRepository
  ) {
    this.antService = antService;
    this.antRepository = antRepository;
    this.assignmentRepository = assignmentRepository;
    this.roomRepository = roomRepository;
    this.roomAntRoleRepository = roomAntRoleRepository;
  }

  /**
   * List ants for any userId.
   *
   * This is intentionally not owner-restricted so the UI can show a user's ants in modals.
   *
   * Returns the same AntDto used elsewhere (includes personalityPrompt). If you want to hide
   * personality later, introduce a slimmer DTO.
   */
  @GetMapping("/users/{userId}/ants")
  public ResponseEntity<ListResponse<AntDto>> listAntsForUser(@PathVariable String userId) {
    return ResponseEntity.ok(antService.listMyAnts(userId));
  }

  @GetMapping("/ants/{antId}")
  public ResponseEntity<AntDetailDto> getAntPublic(@PathVariable String antId) {
    var ant = antRepository.findById(antId).orElseThrow(() -> new ResourceNotFoundException("ant not found"));
    var rooms = assignmentRepository.listByAnt(antId).stream().map(a -> a.roomId()).toList();
    var dto = new AntDetailDto(
        // reuse existing service mapping by delegating through listMyAnts mapping shape
        new com.aiantfarm.api.dto.AntDto(
            ant.id(),
            ant.ownerUserId(),
            ant.name(),
            ant.model(),
            ant.personalityPrompt(),
            ant.intervalSeconds(),
            ant.enabled(),
            ant.replyEvenIfNoNew(),
            ant.maxMessagesPerWeek(),
            ant.messagesSentThisPeriod(),
            ant.createdAt().toString(),
            ant.updatedAt().toString()
        ),
        rooms
    );
    return ResponseEntity.ok(dto);
  }

  /**
   * Public list of rooms.
   *
   * Intentionally returns ONLY publicly-displayable fields.
   */
  @GetMapping("/rooms")
  public ResponseEntity<ListResponse<com.aiantfarm.api.dto.PublicRoomDto>> listRoomsPublic() {
    var rooms = roomRepository.listAll(100, null).items().stream()
        .map(r -> new com.aiantfarm.api.dto.PublicRoomDto(r.id(), r.name(), r.scenarioText(), r.createdAt().toString()))
        .toList();
    return ResponseEntity.ok(new ListResponse<com.aiantfarm.api.dto.PublicRoomDto>(rooms));
  }

  /**
   * Public room roles (no assignments, no prompt).
   */
  @GetMapping("/rooms/{roomId}/room-roles")
  public ResponseEntity<ListResponse<PublicRoomRoleDto>> listRoomRolesPublic(@PathVariable String roomId) {
    // If the room doesn't exist, return 404 (avoid leaking auth state).
    roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("room not found"));

    var items = roomAntRoleRepository.listByRoom(roomId).stream()
        .map(r -> new PublicRoomRoleDto(r.roleId(), r.roomId(), r.name(), r.maxSpots()))
        .toList();
    return ResponseEntity.ok(new ListResponse<>(items));
  }
}
