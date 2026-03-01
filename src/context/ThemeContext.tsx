import React, { createContext, useState, useContext } from 'react';

// กำหนดชุดสีสำหรับ Light Mode และ Dark Mode
export const themes = {
  light: {
    background: '#F8F9FA',
    text: '#1E293B',
    card: '#FFFFFF',
    icon: '#2563EB',
    subText: '#64748B',
    border: '#E2E8F0',
    tabBar: '#FFFFFF',
  },
  dark: {
    background: '#0F172A',
    text: '#F8FAFC',
    card: '#1E293B',
    icon: '#60A5FA',
    subText: '#94A3B8',
    border: '#334155',
    tabBar: '#1E293B',
  },
};

const ThemeContext = createContext<any>(null);

export const ThemeProvider = ({ children }: { children: React.ReactNode }) => {
  const [isDarkMode, setIsDarkMode] = useState(false);

  const toggleTheme = () => {
    setIsDarkMode(!isDarkMode);
  };

  const theme = isDarkMode ? themes.dark : themes.light;

  return (
    <ThemeContext.Provider value={{ isDarkMode, toggleTheme, theme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);
