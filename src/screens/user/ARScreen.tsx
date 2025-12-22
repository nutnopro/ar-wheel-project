// src/screens/user/ARScreen.tsx (หน้า 8)
import React, { useEffect, useCallback } from 'react';
import {
  View,
  Text,
  NativeModules,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { COLORS } from '../../theme/colors';

const { ARLauncher } = NativeModules; // [cite: 64]

export default function ARScreen({ navigation }: any) {
  // เรียกใช้ Native Module ตาม [cite: 65]
  const openAR = useCallback(async () => {
    try {
      if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
        await ARLauncher.openARActivity();
      } else {
        Alert.alert('AR Feature is not available on this device');
      }
      // เมื่อกลับจาก Native ให้ถอยกลับหน้าเดิม
      navigation.goBack();
    } catch (err) {
      console.error('Failed to open AR:', err);
      navigation.goBack();
    }
  }, []);

  useEffect(() => {
    openAR();
  }, [openAR]);

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={COLORS.primary} />
      <Text style={{ marginTop: 20, color: COLORS.text }}>
        กำลังเปิดกล้อง AR...
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000',
  },
});
