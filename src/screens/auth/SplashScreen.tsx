// src/screens/auth/SplashScreen.tsx
import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { COLORS } from '../../theme/colors';

export default function SplashScreen({ navigation }: any) {
  return (
    <TouchableOpacity
      style={styles.container}
      activeOpacity={1}
      onPress={() => navigation.replace('Login')}
    >
      <Text style={styles.logo}>ARWheel</Text>
      <Text style={styles.sub}>Tap to start</Text>
    </TouchableOpacity>
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
  sub: { fontSize: 16, color: 'rgba(255,255,255,0.8)', marginTop: 20 },
});
