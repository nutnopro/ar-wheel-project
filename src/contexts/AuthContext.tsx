// src/contexts/AuthContext.tsx
import React, {
  createContext,
  useContext,
  useMemo,
  useState,
  ReactNode,
} from 'react';

type User = { role: 'visitor' | 'user' | 'admin'; email?: string } | null;

type AuthContextType = {
  user: User;
  isLoggedIn: boolean;
  login: (payload?: User) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User>(null);

  const login = (payload?: User) => setUser(payload ?? { role: 'visitor' });
  const logout = () => setUser(null);

  const value = useMemo(
    () => ({ user, isLoggedIn: !!user, login, logout }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
