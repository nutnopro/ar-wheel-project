// src/utils/types.ts

export interface WheelModel {
  id: string;
  owner: string;
  name: string;
  price: number;
  size: string;
  width: string;
  offset: string;
  pcd: string;
  brand: string;
  description: string;
  categories: string[];        // array of categoryId
  images: string[];             // array of image URLs
  androidModelUrl: string;      // .glb file URL
  iosModelUrl: string;          // .usdz file URL
  favoriteCount: number;
  createdAt: string;
  updatedAt: string;
}

// backward compat alias
export type Wheel = WheelModel;

export interface Category {
  id: string;
  name: string;
  description: string;
  isActive: boolean;
  createdAt: string;
}

export interface UserData {
  uid: string;
  email: string;
  displayName: string;
  phoneNumber: string;
  dateOfBirth: string;
  gender: string | null;
  address: {
    houseNumber: string;
    street: string;
    subdistrict: string;
    district: string;
    stateOrProvince: string;
    country: string;
    postcode: string;
  } | null;
  profileImg: string | null;
  role: 'user' | 'store' | 'admin';
  createdAt: string;
  updatedAt: string;
  loginAt: string;
}

export interface FavoriteItem {
  id: string;
  name: string;
  price: number;
  images: string;  // first image only
  addedAt: string;
}
