// src/utils/storage.ts
import { MMKV } from 'react-native-mmkv';

export let storage: MMKV | null = null;

try {
  storage = new MMKV();
  console.log('✅ MMKV Initialized');
} catch (e) {
  console.log('⚠️ MMKV Failed to load (Remote Debugging might be on)');
}

// --- Token ---
export const getToken = () => {
  if (!storage) return null;
  return storage.getString('userToken');
};
export const setToken = (token: string) => {
  if (storage) storage.set('userToken', token);
};
export const removeToken = () => {
  if (storage) storage.delete('userToken');
};

// --- User Data ---
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

// --- Categories ---
export const getCategories = () => {
  if (!storage) return [];
  const json = storage.getString('categories');
  return json ? JSON.parse(json) : [];
};
export const setCategories = (categories: any[]) => {
  if (storage) storage.set('categories', JSON.stringify(categories));
};
export const removeCategories = () => {
  if (storage) storage.delete('categories');
};

// ============================================================
// Native Bridge Storage (ar_ prefix keys)
// ============================================================

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

// --- Selected Model (for AR native) ---
export interface SelectedModelData {
  id: string;
  name: string;
  price: string;
  brand: string;
  modelUrl: string;
  localPath?: string;
  imageUrl: string;
}

export const setSelectedModel = (model: SelectedModelData) => {
  if (!storage) return;
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

// --- Model Paths ---
export const setModelPaths = (paths: string[]) => {
  if (storage) storage.set('ar_model_paths', JSON.stringify(paths));
};
export const getModelPaths = (): string[] => {
  if (!storage) return [];
  const raw = storage.getString('ar_model_paths');
  return raw ? JSON.parse(raw) : [];
};

// --- Language ---
export const setLanguage = (lang: string) => {
  if (storage) storage.set('ar_language', lang);
};
export const getLanguage = (): string => {
  if (!storage) return 'en';
  return storage.getString('ar_language') || 'en';
};
