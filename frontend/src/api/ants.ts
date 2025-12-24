import { apiClient } from './client';
import type {
  AntDto,
  AntDetailDto,
  AntRoomAssignmentDto,
  AntRunDto,
  CreateAntRequest,
  ListResponse,
  UpdateAntRequest,
} from './dto';

export const antApi = {
  create: (data: CreateAntRequest) => apiClient.post<AntDto>('/api/v1/ants', data),
  listMine: () => apiClient.get<ListResponse<AntDto>>('/api/v1/ants'),
  get: (antId: string) => apiClient.get<AntDetailDto>(`/api/v1/ants/${antId}`),
  update: (antId: string, data: UpdateAntRequest) => apiClient.patch<AntDto>(`/api/v1/ants/${antId}`, data),
  delete: (antId: string) => apiClient.delete<void>(`/api/v1/ants/${antId}`),
  assignToRoom: (antId: string, roomId: string) => apiClient.post(`/api/v1/ants/${antId}/rooms`, { roomId }),
  unassignFromRoom: (antId: string, roomId: string) => apiClient.delete(`/api/v1/ants/${antId}/rooms/${roomId}`),
  listRuns: (antId: string) => apiClient.get<ListResponse<AntRunDto>>(`/api/v1/ants/${antId}/runs`),
  listInRoom: (roomId: string) => apiClient.get<ListResponse<AntRoomAssignmentDto>>(`/api/v1/rooms/${roomId}/ants`),
  runAnt: (antId: string) => apiClient.post(`/api/v1/ants/${antId}/runs`),
};
