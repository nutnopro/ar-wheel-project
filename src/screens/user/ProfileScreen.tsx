// src/screens/user/ProfileScreen.tsx
import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Image,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons'; // [cite: 1]
import { COLORS } from '../../theme/colors';

const MenuRow = ({ icon, title, onPress, color }: any) => (
  <TouchableOpacity style={styles.row} onPress={onPress}>
    <View
      style={[styles.iconBox, { backgroundColor: color || COLORS.primary }]}
    >
      <Ionicons name={icon} size={20} color="#fff" />
    </View>
    <Text style={styles.rowText}>{title}</Text>
    <Ionicons name="chevron-forward" size={20} color={COLORS.textDim} />
  </TouchableOpacity>
);

export default function ProfileScreen({ navigation }: any) {
  return (
    <ScrollView style={{ flex: 1, backgroundColor: COLORS.background }}>
      <View style={styles.header}>
        <Image
          source={{ uri: 'https://via.placeholder.com/100' }}
          style={styles.avatar}
        />
        <Text style={styles.name}>สมชาย รักรถ</Text>
        <Text style={styles.email}>somchai@example.com</Text>

        <TouchableOpacity
          style={styles.editBtn}
          onPress={() => navigation.navigate('EditProfile')}
        >
          <Text style={{ color: '#fff', fontSize: 12 }}>แก้ไขข้อมูล</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <MenuRow
          icon="person"
          title="แก้ไขข้อมูลส่วนตัว"
          onPress={() => navigation.navigate('EditProfile')}
        />
        <MenuRow
          icon="heart"
          title="รายการโปรด"
          onPress={() => navigation.navigate('Favorites')}
          color="#EF4444"
        />
        <MenuRow
          icon="settings"
          title="การตั้งค่า"
          onPress={() => navigation.navigate('Settings')}
          color="#64748B"
        />
        <MenuRow
          icon="log-out"
          title="ออกจากระบบ"
          onPress={() => navigation.replace('Login')}
          color="#000"
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: 'center',
    padding: 30,
    backgroundColor: COLORS.card,
    borderBottomLeftRadius: 30,
    borderBottomRightRadius: 30,
    marginBottom: 20,
    elevation: 2,
  },
  avatar: { width: 100, height: 100, borderRadius: 50, marginBottom: 12 },
  name: { fontSize: 20, fontWeight: 'bold', color: COLORS.text },
  email: { fontSize: 14, color: COLORS.textDim, marginBottom: 12 },
  editBtn: {
    backgroundColor: COLORS.primary,
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 20,
  },
  section: { paddingHorizontal: 20 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
  },
  iconBox: {
    width: 36,
    height: 36,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  rowText: { flex: 1, fontSize: 16, color: COLORS.text, fontWeight: '500' },
});
