import api from './api';

export const adminService = {

  // ================= USERS =================
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


  // ================= STORES =================
  getStores: async () => {
    return api.get('/stores');
  },

  createStore: async (data: any) => {
    return api.post('/stores', data);
  },

  updateStore: async (id: string, data: any) => {
    return api.patch(`/stores/${id}`, data);
  },

  deleteStore: async (id: string) => {
    return api.delete(`/stores/${id}`);
  },


  // ================= CATEGORIES =================
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


  // ================= MODELS =================
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


  // ================= LOGS =================
  getLogs: async () => {
    return api.get('/logs');
  },


  // ================= SYSTEM STATS =================
  getSystemStats: async () => {
    return api.get('/statistics/system');
  },


  // ================= STORE STATS =================
  getStoreStats: async (storeId: string) => {
    if (!storeId) throw new Error('storeId is required');

    return api.get('/statistics/store', {
      params: { storeId },
    });
  },
};