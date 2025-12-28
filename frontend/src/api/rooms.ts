import { apiClient } from './client';
import type {
  ListResponse,
  RoomDto,
  RoomRoleDto,
  CreateRoomRoleRequest,
  UpdateRoomRoleRequest,
} from './dto';

export const roomApi = {
  updateScenario: (roomId: string, scenarioText: string) =>
    apiClient.put<RoomDto>(`/api/v1/rooms/${roomId}/scenario`, { scenarioText }),

  listRoles: (roomId: string) =>
    apiClient.get<ListResponse<RoomRoleDto>>(`/api/v1/rooms/${roomId}/room-roles`),

  createRole: (roomId: string, data: CreateRoomRoleRequest) =>
    apiClient.post<RoomRoleDto>(`/api/v1/rooms/${roomId}/room-roles`, data),

  updateRole: (roomId: string, roleId: string, data: UpdateRoomRoleRequest) =>
    apiClient.put<RoomRoleDto>(`/api/v1/rooms/${roomId}/room-roles/${roleId}`, data),

  deleteRole: (roomId: string, roleId: string) =>
    apiClient.delete<void>(`/api/v1/rooms/${roomId}/room-roles/${roleId}`),

  assignAntRole: (antId: string, roomId: string, roleId: string | null) =>
    apiClient.put<void>(`/api/v1/ants/${antId}/rooms/${roomId}/room-role`, { roleId }),

  deleteRoom: (roomId: string) => apiClient.delete<void>(`/api/v1/rooms/${roomId}`),
};
