// src/screens/user/ProductDetailScreen.tsx
import React from 'react';
import { View, Text, Image, StyleSheet, ScrollView } from 'react-native';
import CustomButton from '../../components/CustomButton';
import { COLORS } from '../../theme/colors';

export default function ProductDetailScreen({ route, navigation }: any) {
  const { product } = route.params;

  return (
    <View style={{ flex: 1, backgroundColor: COLORS.card }}>
      <ScrollView>
        <Image
          source={{ uri: 'https://via.placeholder.com/600x600' }}
          style={{ width: '100%', height: 300, backgroundColor: '#eee' }}
        />
        <View style={styles.content}>
          <Text style={styles.brand}>{product.brand}</Text>
          <Text style={styles.name}>{product.name}</Text>
          <Text style={styles.price}>฿{product.price.toLocaleString()}</Text>
          <Text style={styles.desc}>
            รายละเอียดสินค้า ล้ออัลลอยคุณภาพสูง น้ำหนักเบา แข็งแรงทนทาน
            เหมาะสำหรับรถเก๋งและรถ SUV...
          </Text>
        </View>
      </ScrollView>

      <View style={styles.actionArea}>
        {/* ปุ่มเปิด AR -> ส่งข้อมูลไปหน้า ARScreen หรือเรียก Native ตรงนี้ก็ได้ */}
        <CustomButton
          title="ลองใส่กับรถ (AR View)"
          onPress={() => navigation.navigate('ARNative')}
          variant="primary"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { padding: 20 },
  brand: { color: COLORS.textDim, fontSize: 14, fontWeight: '600' },
  name: {
    fontSize: 24,
    fontWeight: 'bold',
    color: COLORS.text,
    marginVertical: 4,
  },
  price: {
    fontSize: 22,
    fontWeight: '700',
    color: COLORS.primary,
    marginBottom: 16,
  },
  desc: { fontSize: 16, color: COLORS.secondary, lineHeight: 24 },
  actionArea: { padding: 20, borderTopWidth: 1, borderTopColor: COLORS.border },
});
