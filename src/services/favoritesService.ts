import api from './api';

export const favoritesService = {
  // Add to favorites
  add: async (uid: string, data: { id: string; name: string; price: number; images: string }) => {
    return api.post(`/users/favorites/${uid}`, data);
  },

  // Get all favorites
  getAll: async (uid: string) => {
    return api.get(`/users/favorites/${uid}`);
  },

  // Remove from favorites
  remove: async (uid: string, modelId: string) => {
    return api.delete(`/users/favorites/${uid}/${modelId}`);
  },
};
