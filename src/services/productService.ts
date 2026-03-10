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

    const res = await api.get('/models', { params });

    return res.data;
  },

  // Get single model by ID
  getById: async (id: string) => {

    const res = await api.get(`/models/${id}`);

    return res.data;
  },

  // Create model (store/admin)
  create: async (data: any) => {

    const res = await api.post('/models', data);

    return res.data;
  },

  // Update model (store/admin)
  update: async (id: string, data: any) => {

    const res = await api.patch(`/models/${id}`, data);

    return res.data;
  },

  // Delete model (store/admin)
  delete: async (id: string) => {

    const res = await api.delete(`/models/${id}`);

    return res.data;
  },

  // Upload file to model (store/admin)
  uploadFile: async (id: string, file: any) => {

    const formData = new FormData();

    formData.append('file', file);

    const res = await api.post(`/models/upload/${id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });

    return res.data;
  },

  // Create model with metadata and files
  createWithFile: async (
    data: any,
    glbFile?: any,
    usdzFile?: any,
    imageFiles?: any[],
  ) => {

    const formData = new FormData();

    Object.keys(data).forEach(key => {

      if (Array.isArray(data[key])) {

        data[key].forEach((val: string) =>
          formData.append(key, val),
        );

      } else {

        formData.append(key, data[key]);

      }

    });

    if (glbFile) {
      formData.append('androidModelFile', glbFile);
    }

    if (usdzFile) {
      formData.append('iosModelFile', usdzFile);
    }

    if (imageFiles && imageFiles.length > 0) {

      imageFiles.forEach(img => {

        formData.append('imageFiles', img);

      });

    }

    const res = await api.post('/models/with-file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });

    return res.data;
  },

  // Update model data and files
  updateWithFile: async (
    id: string,
    data: any,
    glbFile?: any,
    usdzFile?: any,
    imageFiles?: any[],
  ) => {

    const formData = new FormData();

    Object.keys(data).forEach(key => {

      if (
        data[key] !== undefined &&
        data[key] !== null &&
        data[key] !== ''
      ) {

        if (Array.isArray(data[key])) {

          data[key].forEach((val: string) =>
            formData.append(key, val),
          );

        } else {

          formData.append(key, data[key]);

        }

      }

    });

    if (glbFile) formData.append('androidModelFile', glbFile);

    if (usdzFile) formData.append('iosModelFile', usdzFile);

    if (imageFiles && imageFiles.length > 0) {

      imageFiles.forEach(img =>
        formData.append('imageFiles', img),
      );

    }

    const res = await api.patch(`/models/${id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });

    return res.data;
  },
};