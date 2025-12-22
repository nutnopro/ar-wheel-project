import React from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  TouchableOpacity,
  ViewStyle,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../theme/colors';

// เราจะไม่รับ interface Wheel แล้ว แต่รับเป็นค่าที่เอาไปโชว์เลย
interface Props {
  // 1. Data Props (ข้อมูลที่จะแสดง)
  title: string; // เช่น ชื่อสินค้า, Username, ชื่อร้าน
  subtitle?: string; // เช่น ยี่ห้อ, Email, ที่อยู่ (Optional)
  price?: string | number; // ราคา (Optional: ถ้าไม่มีก็ไม่โชว์)
  imageUrl?: string | null; // รูปภาพ

  // 2. Behavior Props (การทำงาน)
  onPress: () => void;
  variant?: 'grid' | 'list';

  // 3. Action Props (ปุ่มด้านขวาล่าง หรือ ขวาสุด)
  // อนุญาตให้ส่ง Component ปุ่มเองได้ (เช่น ปุ่ม Edit/Delete คู่กัน)
  renderAction?: () => React.ReactNode;

  // หรือถ้าจะใช้แบบง่ายๆ ก็ส่งแค่ชื่อ Icon
  actionIcon?: React.ComponentProps<typeof Ionicons>['name'];
  onActionPress?: () => void;
}

export default function UniversalCard({
  title,
  subtitle,
  price,
  imageUrl,
  onPress,
  variant = 'grid',
  renderAction,
  actionIcon,
  onActionPress,
}: Props) {
  const isGrid = variant === 'grid';

  return (
    <TouchableOpacity
      style={[styles.card, isGrid ? styles.cardGrid : styles.cardList]}
      activeOpacity={0.8}
      onPress={onPress}
    >
      {/* --- Image Section --- */}
      <View
        style={isGrid ? styles.imageContainerGrid : styles.imageContainerList}
      >
        <Image
          source={
            imageUrl
              ? { uri: imageUrl }
              : { uri: 'https://via.placeholder.com/300' }
          }
          style={styles.image}
          resizeMode="cover"
        />
      </View>

      {/* --- Info Section --- */}
      <View style={styles.infoContainer}>
        <View>
          {/* Subtitle (เช่น Brand, Email) */}
          {subtitle && (
            <Text style={styles.subtitle} numberOfLines={1}>
              {subtitle}
            </Text>
          )}

          {/* Title (เช่น Name, Username) */}
          <Text style={styles.title} numberOfLines={isGrid ? 1 : 2}>
            {title}
          </Text>
        </View>

        <View style={styles.footer}>
          {/* Price หรือ Info อื่นๆ (ถ้าไม่มี price ส่งมา บรรทัดนี้จะหายไปเอง) */}
          {price !== undefined ? (
            <Text style={styles.price}>
              {typeof price === 'number' ? `฿${price.toLocaleString()}` : price}
            </Text>
          ) : (
            <View /> // Spacer เพื่อให้ปุ่ม Action ยังอยู่ขวาสุดเสมอ
          )}

          {/* Action Area */}
          {/* ถ้ามีการส่ง Custom Render มา (เช่นปุ่ม Edit+Delete) ให้ใช้เลย */}
          {renderAction
            ? renderAction()
            : // ถ้าไม่มี ให้ใช้ปุ่มวงกลมมาตรฐาน
              actionIcon && (
                <TouchableOpacity
                  style={styles.iconBtn}
                  onPress={onActionPress}
                >
                  <Ionicons
                    name={actionIcon}
                    size={18}
                    color={COLORS.primary}
                  />
                </TouchableOpacity>
              )}
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  // --- Common ---
  card: {
    backgroundColor: COLORS.card,
    borderRadius: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 4,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.03)',
  },
  image: { width: '100%', height: '100%' },
  infoContainer: {
    padding: 12,
    flex: 1,
    justifyContent: 'space-between',
  },
  subtitle: {
    fontSize: 12,
    color: COLORS.textDim,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 8,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 4,
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
    backgroundColor: '#EFF6FF',
    justifyContent: 'center',
    alignItems: 'center',
  },

  // --- Grid Specific ---
  cardGrid: { flexDirection: 'column' },
  imageContainerGrid: {
    width: '100%',
    height: 140,
    backgroundColor: '#F8FAFC',
  },

  // --- List Specific ---
  cardList: { flexDirection: 'row', height: 120 },
  imageContainerList: {
    width: 120,
    height: '100%',
    backgroundColor: '#F8FAFC',
  },
});
