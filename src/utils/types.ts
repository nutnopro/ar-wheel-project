// src/utils/types.ts

export interface WheelModel {
  id: string;
  owner: string;
  name: string;
  price: string;         // Firebase เก็บเป็น string
  size: string;
  width: string;
  offset: string;
  pcd: string;
  brand: string;
  description: string;
  categories: string[];  // array ของ cateId
  images: string[];      // array ของ URL รูปภาพ
  modelUrl: string;      // full download URL ของไฟล์ .glb
  favoriteCount: number;
  createdAt: string;
  updatedAt: string;
}

// backward compat alias
export type Wheel = WheelModel;
