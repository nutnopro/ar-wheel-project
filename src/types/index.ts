// src/types/index.ts
export type UserRole = 'user' | 'store' | 'admin';
export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  avatarUrl?: string;
}
// *Components interface
export interface SettingPanelProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  height?: number | string; // เผื่ออยากปรับความสูง (default: 50%)
}

// *Screens interface
export interface Product {
  id: number;
  name: string;
  brand: string;
  price: number;
  size: number; // ขอบ 15, 17, 18
  imageUrl: string | null;
  description?: string;
  isFavorite?: boolean; // เอาไว้เช็คหน้า Favorite
}

// *API Response interface
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}
