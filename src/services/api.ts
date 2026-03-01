// src/services/api.ts
import axios from 'axios';
import { Alert } from 'react-native';
import { 
  getToken, 
  removeToken, 
  removeUserData 
} from '../utils/storage';

// สร้าง instance กลางของ Axios
const api = axios.create({
  // ⚠️ IMPORTANT: แก้ IP Address ให้ตรงกับ backend ของคุณ
  // - Emulator Android: ใช้ 'http://10.0.2.2:3000'
  // - Device จริง: ใช้ 'http://<IP เครื่องคอมของคุณ>:3000'
  baseURL: 'https://ar-alloy-api.onrender.com',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// === Request Interceptor: แนบ Token ทุกครั้งถ้ามี ===
api.interceptors.request.use(
  async (config) => {
    try {
      const token = await getToken();
      if (token) {
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch (error) {
      console.error('Error getting token:', error);
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// === Response Interceptor: จัดการ 401 / session หมดอายุ ===
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;

    if (status === 401) {
      // เคลียร์ข้อมูล auth ออกจากเครื่อง
      await removeToken();
      await removeUserData();

      // แจ้งผู้ใช้
      Alert.alert(
        'Session expired',
        'Your login session has expired. Please login again.'
      );

      // TODO (ถ้าอยาก auto redirect): ใช้ navigationRef.reset({ ... }) ไปหน้า SignIn
    }

    return Promise.reject(error);
  },
);

export default api;