import api from './api';

export const authService = {
  // ===== LOGIN =====
  login: async (emailOrUsername: string, pass: string) => {
    const payload = {
      usernameOrEmail: emailOrUsername,
      password: pass,
    };
    console.log('🚀 Sending Login Payload:', payload);
    // Response: { access_token, user, categories }
    const response = await api.post('/Auth/login', payload);
    console.log('📦 SERVER RESPONSE:', JSON.stringify(response.data, null, 2));
    return response;
  },

  // ===== REGISTER =====
  register: async (userData: {
    displayName: string;
    email: string;
    password: string;
    phoneNumber: string;
    dateOfBirth: string;
  }) => {
    console.log('🚀 Sending Register Payload:', userData);
    const response = await api.post('/Auth/register', userData);
    return response.data;
  },

  // ===== FORGOT PASSWORD =====
  forgotPassword: async (email: string) => {
    const response = await api.post('/Auth/Forgotpassword-auth', { Email: email });
    return response.data;
  },

  // ===== CHANGE PASSWORD =====
  changePassword: async (email: string, oldPassword: string, newPassword: string) => {
    const response = await api.post('/Auth/Changepassword', {
      email,
      oldPassword,
      newPassword,
    });
    // Response: { message, access_token }
    return response.data;
  },

  // ===== UPDATE PROFILE =====
  updateProfile: async (uid: string, data: any) => {
    try {
      const response = await api.patch(`/users/profile/${uid}`, data);
      if (response.data && response.data.user) {
        return response.data.user;
      }
      
      return response.data;
    } catch (error) {
      console.error('API Update Profile Error:', error);
      throw error;
    }
  },
  // ===== UPLOAD PROFILE IMAGE =====
  uploadProfileImage: async (uid: string, fileData: { uri: string; type: string; name: string }) => {
    const formData = new FormData();
    formData.append('file', {
      uri: fileData.uri,
      type: fileData.type,
      name: fileData.name,
    } as any);

    const response = await api.post(`/users/profile/${uid}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
