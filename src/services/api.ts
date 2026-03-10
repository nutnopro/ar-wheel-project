// src/services/api.ts
import axios from 'axios';
import { Alert } from 'react-native';
import { getToken, removeToken, removeUserData } from '../utils/storage';

const api = axios.create({
  baseURL: 'https://ar-alloy-api.onrender.com',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// === Request Interceptor ===
api.interceptors.request.use(
  config => {
    try {
      const token = getToken();

      if (token) {
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch (error) {
      console.error('Error getting token:', error);
    }

    return config;
  },
  error => {
    return Promise.reject(error);
  },
);

// === Response Interceptor ===
api.interceptors.response.use(
  response => response,
  async error => {
    const status = error?.response?.status;

    if (status === 401) {
      removeToken();
      removeUserData();

      Alert.alert(
        'Session expired',
        'Your login session has expired. Please login again.',
      );
    }

    return Promise.reject(error);
  },
);

export default api;