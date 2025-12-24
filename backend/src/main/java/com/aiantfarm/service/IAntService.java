package com.aiantfarm.service;

import com.aiantfarm.api.dto.AntDetailDto;
import com.aiantfarm.api.dto.AntDto;
import com.aiantfarm.api.dto.AntRunDto;
import com.aiantfarm.api.dto.AntRoomAssignmentDto;
import com.aiantfarm.api.dto.AssignAntToRoomRequest;
import com.aiantfarm.api.dto.CreateAntRequest;
import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.UpdateAntRequest;

public interface IAntService {
  AntDto createAnt(String ownerUserId, CreateAntRequest req);
  ListResponse<AntDto> listMyAnts(String ownerUserId);
  AntDetailDto getAnt(String ownerUserId, String antId);
  AntDto updateAnt(String ownerUserId, String antId, UpdateAntRequest req);

  void assignToRoom(String ownerUserId, String antId, AssignAntToRoomRequest req);
  void unassignFromRoom(String ownerUserId, String antId, String roomId);

  ListResponse<AntRunDto> listRuns(String ownerUserId, String antId, Integer limit);

  /**
   * Room-scoped view: list assignments (and their state) for a specific room.
   * Intended for room UI: "show ants currently in this room".
   */
  ListResponse<AntRoomAssignmentDto> listAntsInRoom(String roomId);

  /**
   * Triggers an immediate one-off run of the ant across all assigned rooms.
   *
   * NOTE: This is separate from the timer-based schedule. Intended for manual UI trigger.
   */
  void runNow(String ownerUserId, String antId);

  // Delete an ant (owner only). Removes assignments and cancels scheduling.
  void deleteAnt(String ownerUserId, String antId);
}
