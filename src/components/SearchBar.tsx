//src/components/SearchBar.tsx
import React from 'react';
import { View, TextInput, StyleSheet, ViewStyle } from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../theme/colors';

interface Props {
  value?: string;
  onChangeText?: (text: string) => void;
  placeholder?: string;
  containerStyle?: ViewStyle;
  editable?: boolean; // เพิ่มตัวนี้เผื่อใช้เป็นแค่ปุ่มกด (หน้า Home)
}

export default function SearchBar({
  value,
  onChangeText,
  placeholder = 'ค้นหา...',
  containerStyle,
  editable = true,
}: Props) {
  return (
    <View style={[styles.container, containerStyle]}>
      <Ionicons
        name="search"
        size={20}
        color={COLORS.textDim}
        style={styles.icon}
      />
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={COLORS.textDim}
        editable={editable}
        autoCorrect={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F1F5F9', // สีเทาอ่อนๆ ตัดกับพื้นหลังขาว
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 48,
    borderWidth: 1,
    borderColor: 'transparent', // เผื่ออยากใส่ขอบ
  },
  icon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: COLORS.text,
    height: '100%',
    paddingVertical: 0, // แก้ bug ตัวหนังสือลอยใน Android
  },
});
