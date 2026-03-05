import api from './api';

export const adminService = {
  // === Users ===
  getUsers: async () => {
    return api.get('/users');
  },
  createUser: async (data: any) => {
    return api.post('/users', data);
  },
  updateUser: async (uid: string, data: any) => {
    return api.patch(`/users/profile/${uid}`, data);
  },
  deleteUser: async (uid: string) => {
    return api.delete(`/users/${uid}`);
  },

  // === Categories ===
  getCategories: async () => {
    return api.get('/categories');
  },
  createCategory: async (data: any) => {
    return api.post('/categories', data);
  },
  updateCategory: async (id: string, data: any) => {
    return api.patch(`/categories/${id}`, data);
  },
  deleteCategory: async (id: string) => {
    return api.delete(`/categories/${id}`);
  },

  // === Models (admin manages ALL) ===
  getModels: async (params?: any) => {
    return api.get('/models', { params });
  },
  createModel: async (data: any) => {
    return api.post('/models', data);
  },
  updateModel: async (id: string, data: any) => {
    return api.patch(`/models/${id}`, data);
  },
  deleteModel: async (id: string) => {
    return api.delete(`/models/${id}`);
  },

  // === Logs ===
  getLogs: async () => {
    return api.get('/logs');
  },

  // === System Statistics ===
  getSystemStats: async () => {
    return api.get('/statistics/system');
  },

  // === Store Statistics ===
  getStoreStats: async (storeId: string) => {
    return api.get(`/stores/stats/${storeId}`);
  },
};
