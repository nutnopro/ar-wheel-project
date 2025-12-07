// src/contexts/ThemeContext.tsx
import React, {
  createContext,
  useContext,
  useMemo,
  useState,
  ReactNode,
} from 'react';

type Colors = {
  primary: string;
  primaryPressed: string;
  accent: string;
  bg: string;
  bgOverlay: string;
  card: string;
  text: string;
  textDim: string;
  border: string;
  tabBar: string;
};

type ThemeContextType = {
  isDark: boolean;
  colors: Colors;
  toggleTheme: () => void;
  setDark: (v: boolean) => void;
};

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

const LIGHT: Colors = {
  primary: '#3A7AFE',
  primaryPressed: '#2F63D1',
  accent: '#00C6AE',
  bg: '#ffffff',
  bgOverlay: '#171B20CC',
  card: '#F8FAFC',
  text: '#0A0D12',
  textDim: '#97A3B6',
  border: '#E6E9EE',
  tabBar: '#ffffff',
};

const DARK: Colors = {
  primary: '#5B9BFF',
  primaryPressed: '#3F6FC7',
  accent: '#00E1C4',
  bg: '#0A0D12',
  bgOverlay: '#0E1319CC',
  card: '#161B22',
  text: '#FFFFFF',
  textDim: '#97A3B6',
  border: '#1F2630',
  tabBar: '#0A0D12',
};

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [isDark, setIsDark] = useState<boolean>(true);
  const colors = isDark ? DARK : LIGHT;

  const value = useMemo(
    () => ({
      isDark,
      colors,
      toggleTheme: () => setIsDark(v => !v),
      setDark: (v: boolean) => setIsDark(v),
    }),
    [isDark],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
};
