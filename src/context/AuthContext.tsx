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
  setSession,
  clearSession,
  clearSelectedModel,
  setCategories as setStorageCategories,
  getCategories as getStorageCategories,
  removeCategories,
} from '../utils/storage';

export type UserRole = 'visitor' | 'user' | 'store' | 'admin' | null;

interface AuthContextType {
  userRole: UserRole;
  isLoading: boolean;
  isAppReady: boolean;
  login: (email: string, pass: string) => Promise<void>;
  loginAsVisitor: () => void;
  logout: () => void;
  userData: any | null;
  categories: any[];
  updateProfile: (newData: any) => void;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [userRole, setUserRole] = useState<UserRole>(null);
  const [userData, setUserData] = useState<any | null>(null);
  const [categories, setCategoriesState] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isAppReady, setIsAppReady] = useState(false);

  // 1. Auto-login check
  useEffect(() => {
    const checkLogin = async () => {
      try {
        const token = getToken();
        const savedUser = getUserData();

        // Check visitor
        if (savedUser?.role === 'visitor') {
          setUserRole('visitor');
          return;
        }

        if (token && savedUser) {
          setUserData(savedUser);
          setUserRole(savedUser.role || 'user');
          // Load saved categories
          const savedCategories = getStorageCategories();
          if (savedCategories) setCategoriesState(savedCategories);
        }
      } finally {
        setTimeout(() => {
          setIsAppReady(true);
        }, 1500); 
      }
    };
    checkLogin();
  }, []);

  // 2. Login
  const login = async (emailOrUser: string, pass: string) => {
    setIsLoading(true);
    try {
      const response = await authService.login(emailOrUser, pass);
      const { access_token, user, categories: cats } = response.data;
      if (!access_token) throw new Error('No access token received');

      // Save to storage
      setToken(access_token);
      setStorageUser(user);
      if (cats) setStorageCategories(cats);

      // Save session for native AR
      setSession({
        token: access_token,
        userId: user.uid || '',
        role: user.role || 'user',
        username: user.displayName || user.email || '',
      });

      // Update state
      setUserData(user);
      setUserRole(user.role || 'user');
      if (cats) setCategoriesState(cats);
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
      setSession({ token: '', userId: '', role: 'visitor', username: 'Guest' });
      setIsLoading(false);
    }, 500);
  };

  const logout = () => {
    removeToken();
    removeUserData();
    removeCategories();
    clearSession();
    clearSelectedModel();
    setUserRole(null);
    setUserData(null);
    setCategoriesState([]);
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
        isAppReady,
        login,
        loginAsVisitor,
        logout,
        userData,
        categories,
        updateProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
