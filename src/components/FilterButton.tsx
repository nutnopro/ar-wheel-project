import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  TouchableWithoutFeedback,
  ScrollView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../theme/colors';

interface Props {
  // ฟังก์ชันทำงานเมื่อกดปุ่ม "Apply"
  onApply: () => void;
  // ฟังก์ชันทำงานเมื่อกดปุ่ม "Reset" (ถ้าไม่ส่งมา ปุ่ม Reset จะไม่โชว์)
  onReset?: () => void;
  // จำนวน Filter ที่เลือกอยู่ (ถ้ามีจะโชว์จุดแดงแจ้งเตือนบนปุ่ม)
  activeCount?: number;
  // Content ด้านใน (เช่น Checkbox, Input)
  children: React.ReactNode;
}

export default function FilterButton({
  onApply,
  onReset,
  activeCount = 0,
  children,
}: Props) {
  const [visible, setVisible] = useState(false);

  const handleApply = () => {
    onApply();
    setVisible(false);
  };

  const handleReset = () => {
    if (onReset) onReset();
    // ปกติ Reset แล้วอาจจะยังไม่ปิด Modal หรือปิดเลยก็ได้ แล้วแต่ Design
    // setVisible(false);
  };

  return (
    <>
      {/* --- 1. ตัวปุ่ม (Button) --- */}
      <TouchableOpacity
        style={styles.button}
        onPress={() => setVisible(true)}
        activeOpacity={0.8}
      >
        <Ionicons name="options-outline" size={24} color="#fff" />

        {/* Badge แจ้งเตือนจำนวน Filter */}
        {activeCount > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>
              {activeCount > 9 ? '9+' : activeCount}
            </Text>
          </View>
        )}
      </TouchableOpacity>

      {/* --- 2. ตัว Overlay (Modal) --- */}
      <Modal
        visible={visible}
        transparent
        animationType="fade"
        onRequestClose={() => setVisible(false)} // สำหรับ Android Back Button
      >
        {/* พื้นหลังสีดำจางๆ กดแล้วปิด */}
        <TouchableOpacity
          style={styles.overlay}
          activeOpacity={1}
          onPress={() => setVisible(false)}
        >
          <TouchableWithoutFeedback>
            {/* กล่อง Modal ตรงกลาง */}
            <View style={styles.modalContainer}>
              {/* Header */}
              <View style={styles.header}>
                <Text style={styles.title}>ตัวกรอง (Filter)</Text>
                <TouchableOpacity onPress={() => setVisible(false)}>
                  <Ionicons name="close" size={24} color={COLORS.textDim} />
                </TouchableOpacity>
              </View>

              {/* Content (ใส่ ScrollView เผื่อ Filter ยาว) */}
              <ScrollView style={styles.content}>{children}</ScrollView>

              {/* Footer Buttons */}
              <View style={styles.footer}>
                {onReset && (
                  <TouchableOpacity
                    style={styles.resetBtn}
                    onPress={handleReset}
                  >
                    <Text style={styles.resetText}>ล้างค่า</Text>
                  </TouchableOpacity>
                )}

                <TouchableOpacity style={styles.applyBtn} onPress={handleApply}>
                  <Text style={styles.applyText}>นำไปใช้</Text>
                </TouchableOpacity>
              </View>
            </View>
          </TouchableWithoutFeedback>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  // --- Button Style ---
  button: {
    width: 48, // เท่ากับ SearchBar
    height: 48,
    backgroundColor: COLORS.primary,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    // Shadow
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 4,
  },
  badge: {
    position: 'absolute',
    top: -5,
    right: -5,
    backgroundColor: COLORS.error,
    borderRadius: 10,
    width: 20,
    height: 20,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },
  badgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: 'bold',
  },

  // --- Modal Style ---
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContainer: {
    width: '100%',
    maxHeight: '80%', // ไม่ให้สูงเกินหน้าจอ
    backgroundColor: '#fff',
    borderRadius: 20,
    padding: 20,
    // Shadow
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.25,
    shadowRadius: 20,
    elevation: 10,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: COLORS.text,
  },
  content: {
    marginBottom: 20,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
  },
  resetBtn: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  resetText: {
    color: COLORS.textDim,
    fontWeight: '600',
  },
  applyBtn: {
    backgroundColor: COLORS.primary,
    paddingVertical: 10,
    paddingHorizontal: 24,
    borderRadius: 8,
  },
  applyText: {
    color: '#fff',
    fontWeight: '600',
  },
});
