import api from './api';

export const authService = {
  // ===== LOGIN =====
  login: async (username: string, pass: string) => {

    // 1. เตรียมข้อมูล Payload (ชื่อ key ต้องตรงกับที่ Backend คาดหวัง)
    const payload = {
      usernameOrEmail: username,
      password: pass
    };

    // (Log ดูว่ากำลังส่งอะไรไป และส่งไปที่ไหน)
    console.log("🚀 Sending Login Payload:", payload);
    console.log("👉 Target URL:", api.defaults.baseURL + '/Auth/login');

    // 2. ส่งข้อมูลไปที่ /Auth/login
    // ผลลัพธ์จะเป็น: https://ar-alloy-api.onrender.com/Auth/login
    const response = await api.post('/Auth/login', payload);

    // 3. Log ดูสิ่งที่ Server ตอบกลับมา (สำคัญมากสำหรับการ Debug)
    console.log("📦 SERVER RESPONSE:", JSON.stringify(response.data, null, 2));

    // 4. ส่งข้อมูล (เช่น Token, User Data) กลับไปให้หน้าจอ UI ใช้งาน
    return response; // return ทั้ง response object เพื่อให้ดึง response.data ได้
  },

  // ===== REGISTER =====
  register: async (userData: any) => {
    console.log("🚀 Sending Register Payload:", userData);

    // ส่งไปที่ /Auth/register
    const response = await api.post('/Auth/register', userData);

    return response.data;
  },
};