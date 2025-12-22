import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  LayoutAnimation,
  Platform,
  UIManager,
  Keyboard,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../theme/colors';

// เปิดใช้งาน LayoutAnimation สำหรับ Android
if (
  Platform.OS === 'android' &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

interface Props {
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  onBlur?: () => void; // ทำงานเมื่อกดย่อเก็บ
}

export default function SearchButton({
  value,
  onChangeText,
  placeholder = 'Search...',
  onBlur,
}: Props) {
  const [isExpanded, setIsExpanded] = useState(false);
  const inputRef = useRef<TextInput>(null);

  // ฟังก์ชันขยายช่องค้นหา
  const handleExpand = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setIsExpanded(true);
  };

  // ฟังก์ชันย่อเก็บช่องค้นหา
  const handleCollapse = () => {
    Keyboard.dismiss();
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setIsExpanded(false);
    onChangeText(''); // ล้างข้อความเมื่อปิด (Optional: ลบได้ถ้าไม่อยากล้าง)
    if (onBlur) onBlur();
  };

  return (
    <View
      style={[styles.container, isExpanded ? styles.containerExpanded : null]}
    >
      {isExpanded ? (
        // --- 1. แสดงตอนขยาย (Search Bar) ---
        <View style={styles.inputWrapper}>
          <Ionicons
            name="search"
            size={20}
            color={COLORS.textDim}
            style={styles.icon}
          />
          <TextInput
            ref={inputRef}
            style={styles.input}
            value={value}
            onChangeText={onChangeText}
            placeholder={placeholder}
            placeholderTextColor={COLORS.textDim}
            autoFocus // ให้โฟกัสทันทีที่เปิด
            autoCorrect={false}
          />
          <TouchableOpacity onPress={handleCollapse} style={styles.closeBtn}>
            <Ionicons name="close-circle" size={20} color={COLORS.textDim} />
          </TouchableOpacity>
        </View>
      ) : (
        // --- 2. แสดงตอนหุบ (Button Icon) ---
        <TouchableOpacity
          style={styles.button}
          onPress={handleExpand}
          activeOpacity={0.8}
        >
          <Ionicons name="search" size={24} color={COLORS.primary} />
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    backgroundColor: '#fff',
    // Default width เท่าปุ่ม
    width: 48,
    // Common styles
    overflow: 'hidden',
  },
  containerExpanded: {
    flex: 1, // เมื่อขยาย ให้กินพื้นที่ที่เหลือทั้งหมดใน Row
    backgroundColor: '#F1F5F9', // เปลี่ยนสีพื้นหลังเป็นสีเทาอ่อนแบบ Input
    borderWidth: 1,
    borderColor: 'transparent',
  },

  // สไตล์ตอนเป็นปุ่ม
  button: {
    width: '100%',
    height: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff', // พื้นหลังขาว
    borderWidth: 1,
    borderColor: COLORS.border, // มีขอบบางๆ ให้ดูเท่ากับ FilterButton
    borderRadius: 12,
  },

  // สไตล์ตอนเป็น Input
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    height: '100%',
  },
  icon: { marginRight: 8 },
  input: {
    flex: 1,
    fontSize: 16,
    color: COLORS.text,
    paddingVertical: 0,
    height: '100%',
  },
  closeBtn: {
    padding: 4,
  },
});
