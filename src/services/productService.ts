import api from './api';

export const productService = {
  // Get models with pagination (10 per page)
  getAll: async (params?: {
    searchTerm?: string;
    lastVisibleId?: string;
    categoryId?: string;
    minPrice?: number;
    maxPrice?: number;
    os?: string;
  }) => {
    return api.get('/models', { params });
  },

  // Get single model by ID
  getById: async (id: string) => {
    return api.get(`/models/${id}`);
  },

  // Create model (store/admin)
  create: async (data: any) => {
    return api.post('/models', data);
  },

  // Update model (store/admin)
  update: async (id: string, data: any) => {
    return api.patch(`/models/${id}`, data);
  },

  // Delete model (store/admin)
  delete: async (id: string) => {
    return api.delete(`/models/${id}`);
  },

  // Upload file to model (store/admin)
  uploadFile: async (id: string, file: any) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/models/upload/${id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  // Create model with metadata and files
  createWithFile: async (data: any, modelFile?: any, imageFile?: any) => {
    const formData = new FormData();
    // Append all metadata fields
    Object.keys(data).forEach(key => {
      if (Array.isArray(data[key])) {
        // e.g. categories
        data[key].forEach((val: string) => formData.append(key, val));
      } else {
        formData.append(key, data[key]);
      }
    });

    if (modelFile) {
      formData.append('modelFile', modelFile);
    }
    if (imageFile) {
      formData.append('imageFile', imageFile);
    }

    return api.post('/models/with-file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};
