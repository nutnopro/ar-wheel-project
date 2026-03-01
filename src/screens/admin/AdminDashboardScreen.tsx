import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, StatusBar, Alert } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../context/ThemeContext';
// import { useLanguage } from '../../context/LanguageContext'; // ปิดไว้ก่อนกัน Error
import { useAuth } from '../../context/AuthContext';

import Header from '../../components/Header';

const AdminDashboardScreen = () => {
  const navigation = useNavigation<any>();
  const { theme, isDarkMode } = useTheme();
  // const { t } = useLanguage(); // ปิดไว้ก่อน
  const { logout } = useAuth();

  // ใช้ข้อความภาษาอังกฤษตรงๆ เพื่อกัน Error เรื่อง Key ภาษาหาย
  const menuItems = [
    { title: 'Manage Users', icon: 'account-group', route: 'ManageUsers', color: '#3B82F6' },
    { title: 'Manage Stores', icon: 'store', route: 'ManageStores', color: '#10B981' },
    { title: 'Manage Models', icon: 'car-wheel', route: 'ManageModels', color: '#F59E0B' },
    { title: 'Manage Categories', icon: 'shape', route: 'ManageCategories', color: '#8B5CF6' },
    { title: 'System Logs', icon: 'file-document-outline', route: 'SystemLogs', color: '#64748B' },
  ];

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Header title="Admin Dashboard" />
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />

      <ScrollView contentContainerStyle={styles.scrollContent}>

        {/* Section Title */}
        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Settings</Text>
          <Text style={{ color: theme.subText, fontSize: 13 }}>System Preferences & Management</Text>
        </View>

        {/* Menu List */}
        <View style={[styles.menuContainer, { backgroundColor: theme.card }]}>
          {menuItems.map((item, index) => (
            <TouchableOpacity
              key={index}
              style={[
                styles.menuItem,
                { borderBottomColor: theme.border },
                index === menuItems.length - 1 ? { borderBottomWidth: 0 } : {}
              ]}
              onPress={() => item.route ? navigation.navigate(item.route) : Alert.alert('Coming Soon')}
            >
              <View style={[styles.iconBox, { backgroundColor: item.color + '15' }]}>
                <Icon name={item.icon} size={24} color={item.color} />
              </View>
              <Text style={[styles.menuTitle, { color: theme.text }]}>{item.title}</Text>
              <Icon name="chevron-right" size={22} color={theme.subText} style={styles.arrow} />
            </TouchableOpacity>
          ))}
        </View>

        {/* Logout Section */}
        <View style={[styles.menuContainer, { backgroundColor: theme.card, marginTop: 20 }]}>
          <TouchableOpacity
            style={styles.menuItem}
            onPress={logout}
          >
            <View style={[styles.iconBox, { backgroundColor: '#EF444415' }]}>
              <Icon name="logout" size={24} color="#EF4444" />
            </View>
            <Text style={[styles.menuTitle, { color: '#EF4444' }]}>Logout</Text>
          </TouchableOpacity>
        </View>

      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  scrollContent: { padding: 20, paddingTop: 10 },
  sectionHeader: { marginBottom: 15, paddingHorizontal: 4 },
  sectionTitle: { fontSize: 22, fontWeight: 'bold', marginBottom: 4 },

  menuContainer: {
    borderRadius: 16,
    overflow: 'hidden',
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, elevation: 2
  },
  menuItem: {
    flexDirection: 'row', alignItems: 'center',
    paddingVertical: 16, paddingHorizontal: 16,
    borderBottomWidth: 1,
  },
  iconBox: {
    width: 40, height: 40, borderRadius: 10,
    justifyContent: 'center', alignItems: 'center', marginRight: 16
  },
  menuTitle: { fontSize: 16, fontWeight: '500', flex: 1 },
  arrow: { opacity: 0.4 }
});

export default AdminDashboardScreen;