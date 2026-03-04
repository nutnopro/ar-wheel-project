import api from './api';

export const adminService = {
  // === Stores ===
  getStores: async () => {
    return api.get('/stores');
  },
  createStore: async (data: any) => {
    return api.post('/stores', data);
  },
  updateStore: async (id: string, data: any) => {
    return api.put(`/stores/${id}`, data);
  },
  deleteStore: async (id: string) => {
    return api.delete(`/stores/${id}`);
  },

  // === Categories ===
  getCategories: async () => {
    return api.get('/categories');
  },
  createCategory: async (data: any) => {
    return api.post('/categories', data);
  },
  updateCategory: async (id: string, data: any) => {
    return api.put(`/categories/${id}`, data);
  },
  deleteCategory: async (id: string) => {
    return api.delete(`/categories/${id}`);
  },
};
