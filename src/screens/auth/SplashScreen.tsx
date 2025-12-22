// src/screens/auth/SplashScreen.tsx
import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { COLORS } from '../../theme/colors';

export default function SplashScreen({ navigation }: any) {
  useEffect(() => {
    setTimeout(() => navigation.replace('Login'), 2000); // จำลองการโหลด
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>ARWheel</Text>
      <Text style={styles.sub}>Virtual Fitting Room</Text>
    </View>
  );
}
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  logo: { fontSize: 40, fontWeight: '900', color: '#fff' },
  sub: { fontSize: 16, color: 'rgba(255,255,255,0.8)', marginTop: 8 },
});
