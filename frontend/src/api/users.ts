import { apiClient } from './client';
import type { ListResponse, AntDto, UserSettingsDto } from './dto';

export const userApi = {
  getMySettings: () => apiClient.get<UserSettingsDto>('/api/v1/users/me/settings'),
  listAntsForUser: (userId: string) => apiClient.get<ListResponse<AntDto>>(`/api/v1/public/users/${userId}/ants`),
};
