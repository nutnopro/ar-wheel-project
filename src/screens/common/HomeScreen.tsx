// src/screens/user/HomeScreen.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  SafeAreaView,
  TouchableOpacity,
  Modal,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import SearchBar from '../../components/SearchBar';
import WheelCard from '../../components/UniversalCard';
import CustomButton from '../../components/CustomButton';
import { COLORS } from '../../theme/colors';

// Mock Data
const DATA = [
  { id: 1, name: 'Sport Zero', brand: 'Racing', price: 8500, img: null },
  { id: 2, name: 'Drift King', brand: 'Sideways', price: 12000, img: null },
  { id: 3, name: 'Offroad X', brand: 'Muddy', price: 9500, img: null },
];

export default function HomeScreen({ navigation }: any) {
  const [filterVisible, setFilterVisible] = useState(false);

  return (
    <SafeAreaView style={styles.safe}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Home</Text>
        <TouchableOpacity onPress={() => navigation.navigate('Favorites')}>
          <Ionicons name="heart-outline" size={28} color={COLORS.text} />
        </TouchableOpacity>
      </View>

      {/* Search & Filter Row */}
      <View style={styles.searchRow}>
        <View style={{ flex: 1, marginRight: 8 }}>
          <SearchBar placeholder="Search..." />
        </View>
        <TouchableOpacity
          style={styles.filterBtn}
          onPress={() => setFilterVisible(true)}
        >
          <Ionicons name="options-outline" size={24} color="#fff" />
        </TouchableOpacity>
      </View>

      {/* Product List (2 Columns) */}
      <FlatList
        data={DATA}
        keyExtractor={item => item.id.toString()}
        numColumns={2}
        contentContainerStyle={{ padding: 8 }}
        renderItem={({ item }) => (
          <View style={{ flex: 1, padding: 8 }}>
            <WheelCard
              wheel={item}
              onPress={() =>
                navigation.navigate('ProductDetail', { product: item })
              }
            />
          </View>
        )}
      />

      {/* Filter Modal */}
      <Modal visible={filterVisible} animationType="slide">
        <SafeAreaView style={{ flex: 1 }}>
          <View style={styles.modalHeader}>
            <TouchableOpacity onPress={() => setFilterVisible(false)}>
              <Ionicons name="arrow-back" size={24} color={COLORS.text} />
            </TouchableOpacity>
            <Text style={styles.modalTitle}>Filter</Text>
            <View style={{ width: 24 }} />
          </View>
          <View style={{ padding: 20 }}>
            <Text>Filter Options here...</Text>
            {/* ใส่ UI Filter ต่างๆ ตรงนี้ */}
          </View>
          <View style={{ padding: 20, marginTop: 'auto' }}>
            <CustomButton
              title="Apply Filter"
              onPress={() => setFilterVisible(false)}
            />
          </View>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.background },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  title: { fontSize: 28, fontWeight: '900', color: COLORS.text },
  searchRow: { flexDirection: 'row', paddingHorizontal: 16, marginBottom: 10 },
  filterBtn: {
    width: 48,
    height: 48,
    backgroundColor: COLORS.primary,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
    borderColor: '#eee',
  },
  modalTitle: { fontSize: 18, fontWeight: 'bold' },
});
