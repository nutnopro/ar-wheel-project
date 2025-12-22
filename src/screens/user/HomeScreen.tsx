// src/screens/user/HomeScreen.tsx
import React from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  SafeAreaView,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import SearchBar from '../../components/SearchBar'; // ปรับจาก [cite: 4]
import WheelCard from '../../components/WheelCard'; // ปรับจาก [cite: 10]
import { COLORS } from '../../theme/colors';

// Mock Data
const FEATURED = [
  { id: 1, name: 'Sport Classic', brand: 'Racing Pro', price: 8900, img: null },
  { id: 2, name: 'Urban Style', brand: 'City Drive', price: 7500, img: null },
];

export default function HomeScreen({ navigation }: any) {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: COLORS.background }}>
      <ScrollView>
        <View style={{ padding: 16 }}>
          <Text style={styles.header}>ค้นหาล้อแม็ก</Text>
          <Text style={styles.subHeader}>ที่เหมาะกับรถคุณ</Text>

          <TouchableOpacity onPress={() => navigation.navigate('SearchFilter')}>
            <View pointerEvents="none">
              <SearchBar placeholder="ค้นหาแบรนด์, รุ่น..." />
            </View>
          </TouchableOpacity>
        </View>

        <Text style={styles.sectionTitle}>สินค้าแนะนำ</Text>
        <FlatList
          horizontal
          data={FEATURED}
          keyExtractor={item => item.id.toString()}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: 16 }}
          renderItem={({ item }) => (
            <View style={{ width: 200, marginRight: 16 }}>
              {/* ใช้ WheelCard เดิมแต่ปรับ Style ให้ minimal */}
              <WheelCard
                wheel={item}
                onPress={() =>
                  navigation.navigate('ProductDetail', { product: item })
                }
              />
            </View>
          )}
        />

        <Text style={styles.sectionTitle}>มาใหม่ล่าสุด</Text>
        {/* List สินค้าแนวตั้ง */}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  header: {
    fontSize: 32,
    fontWeight: '900',
    color: COLORS.text,
    paddingHorizontal: 4,
  },
  subHeader: {
    fontSize: 18,
    color: COLORS.textDim,
    marginBottom: 20,
    paddingHorizontal: 4,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    margin: 16,
    color: COLORS.text,
  },
});
