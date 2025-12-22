// src/screens/admin/SystemDashboard.tsx
import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { COLORS } from '../../theme/colors';

const StatCard = ({ title, value, color }: any) => (
  <View style={[styles.stat, { borderLeftColor: color }]}>
    <Text style={{ color: COLORS.textDim, fontSize: 12 }}>{title}</Text>
    <Text style={{ color: COLORS.text, fontSize: 24, fontWeight: 'bold' }}>
      {value}
    </Text>
  </View>
);

export default function SystemDashboard() {
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: COLORS.background, padding: 16 }}
    >
      <Text style={styles.title}>ภาพรวมระบบ</Text>

      <View style={styles.grid}>
        <StatCard title="ยอดเข้าชม" value="1,204" color="#3B82F6" />
        <StatCard title="จำนวนสินค้า" value="45" color="#10B981" />
        <StatCard title="ผู้ใช้งาน" value="320" color="#F59E0B" />
      </View>

      <Text style={[styles.title, { marginTop: 24 }]}>เมนูจัดการ</Text>
      {/* ปุ่มกดไปหน้าต่างๆ */}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 16,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  stat: {
    width: '48%',
    backgroundColor: COLORS.card,
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    borderLeftWidth: 4,
    elevation: 1,
  },
});
