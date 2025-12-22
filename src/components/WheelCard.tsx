// src/components/WheelCard.tsx
import React from 'react';
import { View, Text, Image, StyleSheet, TouchableOpacity } from 'react-native';
import { COLORS } from '../theme/colors';

interface Wheel {
  id: number | string;
  name: string;
  brand: string;
  price: number;
  img?: string | null; // รองรับกรณีไม่มีรูป
}

interface Props {
  wheel: Wheel;
  onPress: () => void;
}

export default function WheelCard({ wheel, onPress }: Props) {
  return (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.8} // กดแล้วจางลงนิดนึงให้รู้ว่ากด
      onPress={onPress}
    >
      {/* ส่วนรูปภาพ */}
      <View style={styles.imageContainer}>
        <Image
          source={
            wheel.img
              ? { uri: wheel.img }
              : { uri: 'https://via.placeholder.com/300' }
          }
          style={styles.image}
          resizeMode="cover"
        />
        {/* Badge ป้าย New/Sale (ถ้ามีอนาคตเพิ่มได้ตรงนี้) */}
      </View>

      {/* ส่วนข้อมูล */}
      <View style={styles.infoContainer}>
        <Text style={styles.brand}>{wheel.brand}</Text>
        <Text style={styles.name} numberOfLines={1}>
          {wheel.name}
        </Text>

        <View style={styles.footer}>
          <Text style={styles.price}>฿{wheel.price.toLocaleString()}</Text>

          {/* ปุ่ม + เล็กๆ หรือ Icon ลูกศร เพื่อกระตุ้นให้กด */}
          <View style={styles.iconBtn}>
            {/* ใส่ Icon บวก หรือลูกศรก็ได้ */}
            <Text
              style={{
                color: COLORS.primary,
                fontSize: 18,
                fontWeight: 'bold',
              }}
            >
              +
            </Text>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: COLORS.card,
    borderRadius: 16,
    marginBottom: 16,
    // Shadow Styling
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 4, // เงาสำหรับ Android
    overflow: 'hidden', // เพื่อให้รูปไม่ล้นขอบโค้ง
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.03)', // ขอบจางๆ เพิ่มมิติ
  },
  imageContainer: {
    height: 140,
    backgroundColor: '#F8FAFC',
    justifyContent: 'center',
    alignItems: 'center',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  infoContainer: {
    padding: 12,
  },
  brand: {
    fontSize: 12,
    color: COLORS.textDim,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  name: {
    fontSize: 16,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 8,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  price: {
    fontSize: 16,
    fontWeight: '700',
    color: COLORS.primary,
  },
  iconBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#EFF6FF', // สีฟ้าอ่อนมากๆ
    justifyContent: 'center',
    alignItems: 'center',
  },
});
