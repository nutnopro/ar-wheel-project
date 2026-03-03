// src/data/mockData.ts

// 1. ประกาศ Interface ให้ตรงกับที่หน้า Home เรียกใช้
export interface Wheel {
  id: string;
  name: string;
  price: number;
  brand: string;
  category?: string;
  image: string;
  model_url?: string;
}

// 2. ข้อมูลตัวอย่าง
//    เพื่อให้ "ทุกภาพ" ในแอปใช้โมเดลล้อ demo ตัวเดียวกัน
//    เราจะใช้ URL เดียวกันสำหรับทุก item
const DEMO_WHEEL_IMAGE =
  'https://img.freepik.com/free-photo/car-wheel-isolated-white-background_1012-320.jpg';

export const MOCK_WHEELS: Wheel[] = [
  {
    id: '1',
    name: 'BBS FI-R',
    price: 8500,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Sport',
  },
  {
    id: '2',
    name: 'Vossen HF-5',
    price: 3200,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Luxury',
  },
  {
    id: '3',
    name: 'Rays TE37',
    price: 4500,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Sport',
  },
  {
    id: '4',
    name: 'Enkei RPF1',
    price: 1500,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Classic',
  },
  {
    id: '5',
    name: 'HRE P101',
    price: 9500,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Luxury',
  },
  {
    id: '6',
    name: 'OZ Racing',
    price: 2100,
    image: DEMO_WHEEL_IMAGE,
    brand: 'Sport',
  },
];
