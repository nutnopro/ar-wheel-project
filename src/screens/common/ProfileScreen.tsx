// src/screens/user/ProfileScreen.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Image,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';
import SettingPanel from '../../components/SettingPanel'; //  Import Component ที่สร้างใหม่

// Mock Role
let USER_ROLE = 'admin';

const ListItem = ({ icon, title, onPress }: any) => (
  <TouchableOpacity style={styles.row} onPress={onPress}>
    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
      <Ionicons
        name={icon}
        size={22}
        color={COLORS.textDim}
        style={{ marginRight: 12 }}
      />
      <Text style={styles.rowText}>{title}</Text>
    </View>
    <Ionicons name="chevron-forward" size={20} color={COLORS.textDim} />
  </TouchableOpacity>
);

export default function ProfileScreen({ navigation }: any) {
  // Settings Panel State
  const [activePanel, setActivePanel] = useState<string | null>(null);

  // Helper function เพื่อดึง Title ตาม Panel ที่เปิด
  const getPanelTitle = () => {
    switch (activePanel) {
      case 'language':
        return 'Language';
      case 'theme':
        return 'Theme';
      case 'ar':
        return 'AR Preference';
      default:
        return '';
    }
  };

  // Render Panel Content (เหลือแค่เนื้อหาข้างใน ไม่ต้องจัดการ Modal แล้ว)
  const renderPanelContent = () => {
    if (activePanel === 'language') {
      return (
        <View>
          <ListItem
            icon="language"
            title="English"
            onPress={() => setActivePanel(null)}
          />
          <ListItem
            icon="language"
            title="Thai"
            onPress={() => setActivePanel(null)}
          />
        </View>
      );
    }
    if (activePanel === 'theme') {
      return (
        <View>
          <ListItem
            icon="contrast"
            title="System"
            onPress={() => setActivePanel(null)}
          />
          <ListItem
            icon="moon"
            title="Dark"
            onPress={() => setActivePanel(null)}
          />
          <ListItem
            icon="sunny"
            title="Light"
            onPress={() => setActivePanel(null)}
          />
        </View>
      );
    }
    if (activePanel === 'ar') {
      return (
        <View>
          <Text style={{ marginBottom: 10, color: COLORS.textDim }}>
            Marker Size
          </Text>
          {['15"', '16"', '17"', '18" (Default)', '19"'].map(s => (
            <ListItem
              key={s}
              icon="scan-outline"
              title={s}
              onPress={() => setActivePanel(null)}
            />
          ))}
        </View>
      );
    }
    return null;
  };

  // --- Visitor View ---
  if (USER_ROLE === 'visitor') {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.roleTitle}>Visitor</Text>
        <TouchableOpacity
          style={styles.btnPrimary}
          onPress={() => navigation.navigate('Login')}
        >
          <Text style={{ color: '#fff' }}>Login</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.btnOutline}
          onPress={() => navigation.navigate('Register')}
        >
          <Text style={{ color: COLORS.primary }}>Register</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // --- Authenticated Views ---
  return (
    <View style={{ flex: 1, backgroundColor: COLORS.background }}>
      <ScrollView>
        {/* Profile Header */}
        <View style={styles.header}>
          <Image
            source={{ uri: 'https://via.placeholder.com/100' }}
            style={styles.avatar}
          />
          <Text style={styles.name}>
            {USER_ROLE === 'store' ? 'My Store Name' : 'Username'}
          </Text>
          <Text style={styles.email}>user@email.com</Text>
          <TouchableOpacity
            style={styles.editBtn}
            onPress={() => navigation.navigate('EditProfile')}
          >
            <Text style={{ color: '#fff', fontSize: 12 }}>Edit Profile</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          {/* MENU ITEMS (เหมือนเดิม) */}
          {USER_ROLE === 'user' && (
            <ListItem
              icon="heart-outline"
              title="Favorites"
              onPress={() => navigation.navigate('Favorites')}
            />
          )}

          {USER_ROLE === 'store' && (
            <>
              <ListItem
                icon="cube-outline"
                title="Manage Models"
                onPress={() => navigation.navigate('ManageModels')}
              />
              <ListItem
                icon="bar-chart-outline"
                title="Statistics"
                onPress={() => navigation.navigate('StoreStatistics')}
              />
            </>
          )}

          {USER_ROLE === 'admin' && (
            <>
              <ListItem
                icon="settings-outline"
                title="System Management"
                onPress={() => navigation.navigate('SystemManagement')}
              />
              <ListItem
                icon="bar-chart-outline"
                title="Statistics"
                onPress={() => navigation.navigate('AdminStatistics')}
              />
              <ListItem
                icon="document-text-outline"
                title="Logs"
                onPress={() => navigation.navigate('Logs')}
              />
            </>
          )}

          <View style={styles.divider} />
          <ListItem
            icon="language-outline"
            title="Language"
            onPress={() => setActivePanel('language')}
          />
          <ListItem
            icon="color-palette-outline"
            title="Theme"
            onPress={() => setActivePanel('theme')}
          />
          <ListItem
            icon="scan-outline"
            title="AR Preference"
            onPress={() => setActivePanel('ar')}
          />
          <ListItem
            icon="log-out-outline"
            title="Logout"
            onPress={() => navigation.replace('Login')}
          />
        </View>
      </ScrollView>

      {/* เรียกใช้ SettingPanel ที่สร้างขึ้นใหม่ */}
      <SettingPanel
        visible={!!activePanel}
        onClose={() => setActivePanel(null)}
        title={getPanelTitle()}
        height="50%" // กำหนดความสูงได้ถ้าต้องการ
      >
        {renderPanelContent()}
      </SettingPanel>
    </View>
  );
}

const styles = StyleSheet.create({
  centerContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  roleTitle: { fontSize: 24, fontWeight: 'bold', marginBottom: 20 },
  btnPrimary: {
    backgroundColor: COLORS.primary,
    paddingVertical: 12,
    paddingHorizontal: 40,
    borderRadius: 8,
    marginBottom: 12,
  },
  btnOutline: {
    borderWidth: 1,
    borderColor: COLORS.primary,
    paddingVertical: 12,
    paddingHorizontal: 40,
    borderRadius: 8,
  },
  header: {
    alignItems: 'center',
    padding: 30,
    backgroundColor: '#fff',
    marginBottom: 12,
  },
  avatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    marginBottom: 10,
    backgroundColor: '#ddd',
  },
  name: { fontSize: 20, fontWeight: 'bold' },
  email: { color: COLORS.textDim, marginBottom: 10 },
  editBtn: {
    backgroundColor: COLORS.primary,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
  },
  section: { backgroundColor: '#fff', padding: 16 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderColor: '#f0f0f0',
  },
  rowText: { fontSize: 16, color: COLORS.text },
  divider: { height: 20 },
  // ลบ styles ของ Modal เดิมออกไปได้เลย
});
