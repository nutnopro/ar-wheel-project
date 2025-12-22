// src/components/SettingPanel.tsx
import React from 'react';
import {
  View,
  Text,
  Modal,
  StyleSheet,
  TouchableWithoutFeedback,
  Dimensions,
} from 'react-native';
import { COLORS } from '../theme/colors'; // ตรวจสอบ Path ให้ถูกต้องตามโครงสร้างโปรเจค
import { SettingPanelProps } from '../types';

export default function SettingPanel({
  visible,
  onClose,
  title,
  children,
  height = '50%',
}: SettingPanelProps) {
  const resolvedHeight = React.useMemo(() => {
    if (typeof height === 'number') return height;
    if (typeof height === 'string' && height.trim().endsWith('%')) {
      const pct = parseFloat(height);
      if (!isNaN(pct)) {
        return (Dimensions.get('window').height * pct) / 100;
      }
    }
    return undefined;
  }, [height]);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose} // สำหรับปุ่ม Back ของ Android
    >
      <View style={styles.container}>
        {/* ส่วน Overlay สีดำจางๆ กดแล้วปิด */}
        <TouchableWithoutFeedback onPress={onClose}>
          <View style={styles.overlay} />
        </TouchableWithoutFeedback>

        {/* ส่วน Panel สีขาว */}
        <View
          style={[
            styles.panel,
            resolvedHeight ? { height: resolvedHeight } : {},
          ]}
        >
          {/* ขีดเล็กๆ ด้านบน (Handle) เพื่อความสวยงาม */}
          <View style={styles.handleContainer}>
            <View style={styles.handle} />
          </View>

          {/* หัวข้อ (ถ้ามี) */}
          {title && <Text style={styles.title}>{title}</Text>}

          {/* เนื้อหาข้างใน */}
          <View style={styles.content}>{children}</View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'flex-end', // ดัน Panel ลงข้างล่าง
  },
  overlay: {
    ...StyleSheet.absoluteFillObject, // เต็มหน้าจอ
    backgroundColor: 'rgba(0,0,0,0.4)', // สีดำจางๆ
  },
  panel: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 20,
    paddingBottom: 20,
    // Shadow
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 5,
    elevation: 5,
  },
  handleContainer: {
    alignItems: 'center',
    paddingVertical: 10,
  },
  handle: {
    width: 40,
    height: 5,
    backgroundColor: '#E2E8F0',
    borderRadius: 3,
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 16,
    textAlign: 'center',
  },
  content: {
    flex: 1,
  },
});
