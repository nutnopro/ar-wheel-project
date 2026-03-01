// src/context/AuthContext.tsx
import React, { createContext, useState, useContext, useEffect } from 'react';
import { Alert } from 'react-native';
import { authService } from '../services/authService';
import {
  setToken,
  getToken,
  removeToken,
  setUserData as setStorageUser,
  getUserData,
  removeUserData,
} from '../utils/storage';

export type UserRole = 'visitor' | 'user' | 'store' | 'admin' | null;

interface AuthContextType {
  userRole: UserRole;
  isLoading: boolean;
  login: (email: string, pass: string) => Promise<void>;
  loginAsVisitor: () => void;
  logout: () => void;
  userData: any | null;
  updateProfile: (newData: any) => void;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [userRole, setUserRole] = useState<UserRole>(null);
  const [userData, setUserData] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // 1. ตรวจสอบ Session เก่าตอนเปิดแอป (Auto Login)
  useEffect(() => {
    const checkLogin = () => {
      const token = getToken();
      const savedUser = getUserData();

      // ✅ ตรวจสอบสถานะ Visitor
      if (savedUser?.role === 'visitor') {
        setUserRole('visitor');
        return;
      }

      if (token && savedUser) {
        setUserData(savedUser);
        // ถ้า backend ส่ง role มา: 'visitor' | 'user' | 'store' | 'admin'
        // ถ้าไม่มี ให้ default = 'user'
        setUserRole(savedUser.role || 'user');
      }
    };
    checkLogin();
  }, []);

  // 2. ฟังก์ชัน Login จริง
  const login = async (emailOrUser: string, pass: string) => {
    setIsLoading(true);
    try {
      const response = await authService.login(emailOrUser, pass);
      const { access_token, user } = response.data;
      if (!access_token) throw new Error('No access token received');

      // บันทึกลงเครื่อง
      setToken(access_token);
      setStorageUser(user);

      // อัปเดต State
      setUserData(user);
      setUserRole(user.role || 'user');
    } catch (error: any) {
      console.error('Login Error:', error);
      const msg = error?.response?.data?.message || 'Invalid email or password';
      Alert.alert('Login Failed', msg);
    } finally {
      setIsLoading(false);
    }
  };

  const loginAsVisitor = () => {
    setIsLoading(true);
    setTimeout(() => {
      setUserRole('visitor');
      setUserData(null);
      setStorageUser({ role: 'visitor' });
      setIsLoading(false);
    }, 500);
  };

  const logout = () => {
    removeToken();
    removeUserData();
    setUserRole(null);
    setUserData(null);
  };

  const updateProfile = (newData: any) => {
    setUserData((prev: any) => {
      const updated = { ...prev, ...newData };
      setStorageUser(updated);
      return updated;
    });
  };

  return (
    <AuthContext.Provider
      value={{
        userRole,
        isLoading,
        login,
        loginAsVisitor,
        logout,
        userData,
        updateProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
