import api from './api';

export const productService = {
  // Get All (ดูรายการทั้งหมด)
  getAll: async () => {
    return api.get('/wheels'); // เปลี่ยน /wheels เป็น path ของจริง
  },

  // Get One (ดูรายละเอียดตาม ID)
  getById: async (id: string) => {
    return api.get(`/wheels/${id}`);
  },

  // Create (เพิ่มสินค้าใหม่ - ต้องมี Token ถึงทำได้)
  create: async (data: any) => {
    // ไม่ต้องใส่ Header เอง เพราะ Interceptor ในข้อ 1 ทำให้แล้ว!
    return api.post('/wheels', data); 
  },

  // Update (แก้ไข)
  update: async (id: string, data: any) => {
    return api.put(`/wheels/${id}`, data);
  },

  // Delete (ลบ)
  delete: async (id: string) => {
    return api.delete(`/wheels/${id}`);
  }
};