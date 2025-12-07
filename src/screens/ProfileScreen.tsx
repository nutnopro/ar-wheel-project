// src/screens/ProfileScreen.tsx
import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useTheme } from '../contexts/ThemeContext';
import { useAuth } from '../contexts/AuthContext';

export default function ProfileScreen() {
  const { colors } = useTheme();
  const { user, logout } = useAuth();

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      <Text style={[styles.title, { color: colors.text }]}>Profile</Text>
      <Text style={{ color: colors.textDim }}>
        Role: {user?.role ?? 'visitor'}
      </Text>
      <TouchableOpacity
        onPress={() => logout()}
        style={{
          marginTop: 20,
          padding: 12,
          backgroundColor: colors.primary,
          borderRadius: 10,
        }}
      >
        <Text style={{ color: '#fff' }}>Logout</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  title: { fontSize: 20, fontWeight: '900' },
});
