import React, { createContext, useState, useContext } from 'react';

// 1. กำหนดคำแปล (Dictionary)
const translations = {
  en: {
    // Tab Bar
    tab_home: 'Home',
    tab_ar: 'AR',
    tab_profile: 'Profile',

    // Home Screen
    app_name: 'Wheel AR',
    search_placeholder: 'Search model...',
    filter: 'Filter',
    all_category: 'All',

    // Auth & Profile
    welcome: 'Welcome Back',
    signin_subtitle: 'Sign in to continue',
    guest_user: 'Guest User',
    signin_register: 'Sign In / Register',
    logout: 'Log Out',
    exit_visitor: 'Exit Visitor Mode',

    // Profile Menu
    menu_account: 'Account',
    menu_favorites: 'Favorites',
    menu_change_password: 'Change Password',
    menu_preferences: 'Preferences',
    menu_dark_mode: 'Dark Mode',
    menu_language: 'Language',
    menu_ar_pref: 'AR Preferences',

    // Language Screen
    select_language: 'Select Language',
    lang_thai: 'Thai',
    lang_english: 'English',

    // [NEW] Admin Section
    admin_dashboard: 'Admin Dashboard',
    manage_users: 'Manage Users',
    manage_stores: 'Manage Stores',
    manage_models: 'Manage Models',
    manage_categories: 'Manage Categories',
    system_logs: 'System Logs',
    add_new: 'Add New',
    action_edit: 'Edit',
    action_delete: 'Delete',
    confirm_delete: 'Are you sure you want to delete?',
    cancel: 'Cancel',
  },
  th: {
    // Tab Bar
    tab_home: 'หน้าแรก',
    tab_ar: 'AR',
    tab_profile: 'โปรไฟล์',

    // Home Screen
    app_name: 'Wheel AR',
    search_placeholder: 'ค้นหารุ่นล้อแม็ก...',
    filter: 'ตัวกรอง',
    all_category: 'ทั้งหมด',

    // Auth & Profile
    welcome: 'ยินดีต้อนรับ',
    signin_subtitle: 'เข้าสู่ระบบเพื่อใช้งานต่อ',
    guest_user: 'ผู้เยี่ยมชม',
    signin_register: 'เข้าสู่ระบบ / สมัครสมาชิก',
    logout: 'ออกจากระบบ',
    exit_visitor: 'ออกจากโหมดผู้เยี่ยมชม',

    // Profile Menu
    menu_account: 'บัญชีผู้ใช้',
    menu_favorites: 'รายการโปรด',
    menu_change_password: 'เปลี่ยนรหัสผ่าน',
    menu_preferences: 'การตั้งค่าทั่วไป',
    menu_dark_mode: 'โหมดมืด',
    menu_language: 'ภาษา',
    menu_ar_pref: 'ตั้งค่า AR',

    // Language Screen
    select_language: 'เลือกภาษา',
    lang_thai: 'ภาษาไทย',
    lang_english: 'ภาษาอังกฤษ',

    // [NEW] Admin Section
    admin_dashboard: 'แดชบอร์ดผู้ดูแล',
    manage_users: 'จัดการผู้ใช้งาน',
    manage_stores: 'จัดการร้านค้า',
    manage_models: 'จัดการรุ่นโมเดล',
    manage_categories: 'จัดการหมวดหมู่',
    system_logs: 'บันทึกระบบ',
    add_new: 'เพิ่มรายการใหม่',
    action_edit: 'แก้ไข',
    action_delete: 'ลบ',
    confirm_delete: 'คุณแน่ใจหรือไม่ที่จะลบ?',
    cancel: 'ยกเลิก',
  },
};

// 2. สร้าง Context
const LanguageContext = createContext<any>(null);

export const LanguageProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const [language, setLanguage] = useState<'en' | 'th'>('en');

  const changeLanguage = (lang: 'en' | 'th') => {
    setLanguage(lang);
  };

  const t = translations[language];

  return (
    <LanguageContext.Provider value={{ language, changeLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => useContext(LanguageContext);
