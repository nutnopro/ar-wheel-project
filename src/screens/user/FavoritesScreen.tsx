//src/screens/user/FavoritesScreen.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  Image,
  TouchableOpacity,
  SafeAreaView,
  Alert,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

// Mock Data สำหรับทดสอบ (ระบบจริงจะดึงจาก Context/Database)
const MOCK_FAVORITES = [
  {
    id: 1,
    name: 'Sport Classic',
    brand: 'Racing Pro',
    price: 8900,
    size: 15,
    img: null,
  },
  {
    id: 3,
    name: 'Performance Pro',
    brand: 'Racing Elite',
    price: 10500,
    size: 16,
    img: null,
  },
  {
    id: 4,
    name: 'Drift King',
    brand: 'Sideways',
    price: 12000,
    size: 17,
    img: null,
  },
];

export default function FavoritesScreen({ navigation }: any) {
  const [favorites, setFavorites] = useState(MOCK_FAVORITES);

  // ฟังก์ชันลบรายการ
  const handleRemove = (id: number) => {
    Alert.alert(
      'ลบรายการ',
      'คุณต้องการลบสินค้านี้ออกจากรายการโปรดใช่หรือไม่?',
      [
        { text: 'ยกเลิก', style: 'cancel' },
        {
          text: 'ลบ',
          style: 'destructive',
          onPress: () =>
            setFavorites(prev => prev.filter(item => item.id !== id)),
        },
      ],
    );
  };

  // UI เมื่อไม่มีสินค้าในรายการ
  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <View style={styles.emptyIconBg}>
        <Ionicons
          name="heart-dislike-outline"
          size={60}
          color={COLORS.textDim}
        />
      </View>
      <Text style={styles.emptyTitle}>ยังไม่มีรายการโปรด</Text>
      <Text style={styles.emptySub}>
        กดหัวใจที่สินค้าที่คุณสนใจ{'\n'}เพื่อบันทึกไว้ดูภายหลัง
      </Text>
      <TouchableOpacity
        style={styles.browseButton}
        onPress={() => navigation.navigate('Home')}
      >
        <Text style={styles.browseButtonText}>เลือกชมสินค้า</Text>
      </TouchableOpacity>
    </View>
  );

  // UI ของแต่ละรายการ (Card แนวนอน)
  const renderItem = ({ item }: any) => (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.7}
      onPress={() => navigation.navigate('ProductDetail', { product: item })}
    >
      {/* รูปภาพจำลอง */}
      <View style={styles.imageBox}>
        <Image
          source={{ uri: 'https://via.placeholder.com/150' }}
          style={styles.image}
        />
      </View>

      {/* ข้อมูลสินค้า */}
      <View style={styles.infoBox}>
        <View>
          <Text style={styles.brand}>{item.brand}</Text>
          <Text style={styles.name} numberOfLines={1}>
            {item.name}
          </Text>
          <Text style={styles.specs}>ขนาด {item.size} นิ้ว</Text>
        </View>
        <Text style={styles.price}>฿{item.price.toLocaleString()}</Text>
      </View>

      {/* ปุ่มลบ (ถังขยะ) */}
      <TouchableOpacity
        style={styles.deleteBtn}
        onPress={() => handleRemove(item.id)}
      >
        <Ionicons name="trash-outline" size={20} color={COLORS.error} />
      </TouchableOpacity>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>รายการโปรด</Text>
        <Text style={styles.itemCount}>{favorites.length} รายการ</Text>
      </View>

      {/* Content */}
      <View style={styles.content}>
        {favorites.length > 0 ? (
          <FlatList
            data={favorites}
            keyExtractor={item => item.id.toString()}
            renderItem={renderItem}
            contentContainerStyle={{ paddingBottom: 20 }}
            showsVerticalScrollIndicator={false}
          />
        ) : (
          renderEmptyState()
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    backgroundColor: COLORS.background,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.05)',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '900',
    color: COLORS.text,
  },
  itemCount: {
    fontSize: 14,
    color: COLORS.textDim,
  },
  content: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  // Card Styles
  card: {
    flexDirection: 'row',
    backgroundColor: COLORS.card,
    borderRadius: 16,
    padding: 12,
    marginBottom: 16,
    alignItems: 'center',
    // Shadow
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
  },
  imageBox: {
    width: 80,
    height: 80,
    borderRadius: 12,
    backgroundColor: '#F1F5F9',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  image: {
    width: 60,
    height: 60,
    resizeMode: 'contain',
  },
  infoBox: {
    flex: 1,
    justifyContent: 'space-between',
    height: 80,
    paddingVertical: 2,
  },
  brand: {
    fontSize: 12,
    color: COLORS.textDim,
    fontWeight: '600',
  },
  name: {
    fontSize: 16,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 4,
  },
  specs: {
    fontSize: 12,
    color: COLORS.secondary,
    backgroundColor: '#F1F5F9',
    alignSelf: 'flex-start',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  price: {
    fontSize: 16,
    fontWeight: '700',
    color: COLORS.primary,
  },
  deleteBtn: {
    padding: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  // Empty State Styles
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 60,
  },
  emptyIconBg: {
    width: 120,
    height: 120,
    backgroundColor: '#F1F5F9',
    borderRadius: 60,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 8,
  },
  emptySub: {
    fontSize: 14,
    color: COLORS.textDim,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 32,
  },
  browseButton: {
    paddingVertical: 12,
    paddingHorizontal: 32,
    backgroundColor: COLORS.primary,
    borderRadius: 30,
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 4,
  },
  browseButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
