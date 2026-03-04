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

// ============================================================
// Native Bridge Storage (ar_ prefix keys)
// ใช้ MMKV key แยกแต่ละ field เพื่อให้ native อ่านง่าย
// Android: MMKV.defaultMMKV().decodeString("ar_token")
// iOS:     MMKV.default()?.string(forKey: "ar_token")
// ============================================================

// --- Session ---
export interface SessionData {
  token: string;
  userId: string;
  role: string;
  username: string;
}

export const setSession = (session: SessionData) => {
  if (!storage) return;
  storage.set('ar_token', session.token);
  storage.set('ar_user_id', session.userId);
  storage.set('ar_role', session.role);
  storage.set('ar_username', session.username);
};

export const getSession = (): SessionData | null => {
  if (!storage) return null;
  const token = storage.getString('ar_token');
  if (!token) return null;
  return {
    token,
    userId: storage.getString('ar_user_id') || '',
    role: storage.getString('ar_role') || '',
    username: storage.getString('ar_username') || '',
  };
};

export const clearSession = () => {
  if (!storage) return;
  storage.delete('ar_token');
  storage.delete('ar_user_id');
  storage.delete('ar_role');
  storage.delete('ar_username');
};

// --- Selected Model (สำหรับ AR native) ---
export interface SelectedModelData {
  id: string;
  name: string;
  price: number;
  brand: string;
  modelUrl?: string;
  imageUrl?: string;
}

export const setSelectedModel = (model: SelectedModelData) => {
  if (!storage) return;
  // บันทึกทั้งก้อนเป็น JSON string ใน key เดียว
  // Native อ่านด้วย: MMKV.defaultMMKV().decodeString("ar_selected_model") แล้ว parse JSON
  storage.set('ar_selected_model', JSON.stringify(model));
};

export const getSelectedModel = (): SelectedModelData | null => {
  if (!storage) return null;
  const json = storage.getString('ar_selected_model');
  return json ? JSON.parse(json) : null;
};

export const clearSelectedModel = () => {
  if (!storage) return;
  storage.delete('ar_selected_model');
};

// --- Language ---
export const setLanguage = (lang: string) => {
  if (storage) storage.set('ar_language', lang);
};

export const getLanguage = (): string => {
  if (!storage) return 'en';
  return storage.getString('ar_language') || 'en';
};
