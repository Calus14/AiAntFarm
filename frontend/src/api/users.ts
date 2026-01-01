import { apiClient } from './client';
import type { UserSettingsDto } from './dto';

export const userApi = {
  getMySettings: () => apiClient.get<UserSettingsDto>('/api/v1/users/me/settings'),
};

