import { MMKV } from 'react-native-mmkv';

let storage: MMKV | null = null;

try {
  // ใส่ try-catch ดักไว้ ถ้าสร้างไม่ได้ ให้ข้ามไปเลย แอปจะไม่พัง
  storage = new MMKV();
  console.log('✅ MMKV Initialized');
} catch (e) {
  console.log('⚠️ MMKV Failed to load (Remote Debugging might be on)');
}

// --- ฟังก์ชันใช้งาน (เขียนแบบปลอดภัย) ---

export const getToken = () => {
  if (!storage) return null; // ถ้า storage พัง ให้คืนค่า null
  return storage.getString('userToken');
};

export const setToken = (token: string) => {
  if (storage) storage.set('userToken', token);
};

export const removeToken = () => {
  if (storage) storage.delete('userToken');
};

export const getUserData = () => {
  if (!storage) return null;
  const json = storage.getString('userData');
  return json ? JSON.parse(json) : null;
};

export const setUserData = (user: any) => {
  if (storage) storage.set('userData', JSON.stringify(user));
};

export const removeUserData = () => {
  if (storage) storage.delete('userData');
};
