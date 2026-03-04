import React, {useState, useEffect, useCallback} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  FlatList,
  Dimensions,
  Modal,
  NativeModules,
  Platform,
  ActivityIndicator,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import api from '../../services/api';

const {ARLauncher} = NativeModules;

const { width } = Dimensions.get('window');
const COLUMN_WIDTH = (width - 48) / 2; // คำนวณความกว้างการ์ด

const COLORS = {
  primary: '#2563EB',
  white: '#FFFFFF',
  grayBg: '#F3F4F6',
  textDark: '#1F2937',
  textLight: '#9CA3AF',
  border: '#E5E7EB',
  overlay: 'rgba(37, 99, 235, 0.2)',
};

// ลบ MOCK_DATA — ใช้ API แทน

export function HomeScreen() {
  const [isFilterVisible, setFilterVisible] = useState(false);
  const [minPrice, setMinPrice] = useState('0');
  const [maxPrice, setMaxPrice] = useState('999');
  const [products, setProducts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchProducts = useCallback(async () => {
    try {
      setLoading(true);
      const response = await api.get('/models');
      setProducts(response.data);
    } catch (error) {
      console.error('Fetch products error:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const renderItem = ({ item }: any) => (
    <View style={styles.cardContainer}>
      {/* ส่วนรูปภาพ (Placeholder) */}
      <View style={styles.cardImagePlaceholder}>
        {/* ✅ แก้ไข: เปลี่ยนสี Text เป็นสีดำ (#000000) และตัวหนา */}
        <Text style={{ color: '#000000', fontWeight: 'bold', opacity: 0.6 }}>
          thumbnail
        </Text>
      </View>

      {/* ส่วนเนื้อหา */}
      <View style={styles.cardContent}>
        <Text style={styles.cardTitle} numberOfLines={1}>
          {item.name}
        </Text>
        <Text style={styles.cardPrice}>{item.price}</Text>
        <Text style={styles.cardCategory}>category: {item.category}</Text>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      {/* --- Header & Search --- */}
      <View style={styles.headerContainer}>
        <Text style={styles.appTitle}>App Name</Text>
        <View style={styles.searchRow}>
          <View style={styles.searchBar}>
            <Icon name="magnify" size={24} color={COLORS.primary} />
            <TextInput
              style={styles.searchInput}
              placeholder="search"
              placeholderTextColor={COLORS.textLight}
            />
          </View>
          {/* Filter Button */}
          <TouchableOpacity
            style={styles.filterBtn}
            onPress={() => setFilterVisible(true)}
          >
            <Icon name="tune-variant" size={24} color={COLORS.primary} />
          </TouchableOpacity>
        </View>

        {/* Categories Tabs */}
        <View style={{ marginTop: 15, height: 40 }}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {['All', 'Sport', 'Luxury', 'Minimal', 'Classic'].map(
              (cat, index) => (
                <TouchableOpacity key={index} style={{ marginRight: 20 }}>
                  <Text
                    style={[
                      styles.categoryText,
                      index === 0 && {
                        color: COLORS.textDark,
                        fontWeight: 'bold',
                      },
                    ]}
                  >
                    {cat}
                  </Text>
                </TouchableOpacity>
              ),
            )}
          </ScrollView>
        </View>
      </View>

      {/* --- Product Grid --- */}
      {loading ? (
        <View style={styles.centerScreen}>
          <ActivityIndicator size="large" color={COLORS.primary} />
        </View>
      ) : (
      <FlatList
        data={products}
        renderItem={renderItem}
        keyExtractor={item => item.id}
        numColumns={2}
        columnWrapperStyle={{
          justifyContent: 'space-between',
          paddingHorizontal: 20,
        }}
        contentContainerStyle={{ paddingBottom: 100, paddingTop: 10 }}
        showsVerticalScrollIndicator={false}
      />
      )}

      {/* --- Filter Modal (Popup) --- */}
      <Modal
        animationType="fade"
        transparent={true}
        visible={isFilterVisible}
        onRequestClose={() => setFilterVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Filter Options</Text>

            <View style={styles.filterSection}>
              {/* Category */}
              <TouchableOpacity style={styles.filterRow}>
                <Text style={styles.filterLabel}>Category</Text>
                <Icon name="chevron-right" size={24} color={COLORS.textDark} />
              </TouchableOpacity>
              <View style={styles.divider} />

              {/* Size */}
              <TouchableOpacity style={styles.filterRow}>
                <Text style={styles.filterLabel}>Size</Text>
                <Icon name="chevron-right" size={24} color={COLORS.textDark} />
              </TouchableOpacity>
              <View style={styles.divider} />

              {/* Price Range */}
              <View style={{ marginTop: 15 }}>
                <Text style={[styles.filterLabel, { marginBottom: 10 }]}>
                  Price Range
                </Text>
                <View style={styles.priceRow}>
                  <View style={styles.priceInputContainer}>
                    <Text style={styles.currency}>$</Text>
                    <TextInput
                      style={styles.priceInput}
                      value={minPrice}
                      onChangeText={setMinPrice}
                      keyboardType="numeric"
                    />
                  </View>
                  <Text style={{ fontSize: 20, marginHorizontal: 10 }}>-</Text>
                  <View style={styles.priceInputContainer}>
                    <Text style={styles.currency}>$</Text>
                    <TextInput
                      style={styles.priceInput}
                      value={maxPrice}
                      onChangeText={setMaxPrice}
                      keyboardType="numeric"
                    />
                  </View>
                </View>
              </View>
            </View>

            {/* Buttons Action */}
            <View style={styles.modalButtonRow}>
              <TouchableOpacity
                style={styles.btnOutline}
                onPress={() => setFilterVisible(false)}
              >
                <Text style={styles.btnTextOutline}>Clear All</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.btnFilled}
                onPress={() => setFilterVisible(false)}
              >
                <Text style={styles.btnTextFilled}>Apply</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

export function ArScreen({navigation}: any) {
  const openAR = useCallback(async () => {
    try {
      if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
        await ARLauncher.openARActivity('');
        navigation.goBack();
      } else {
        Alert.alert('AR', 'AR Launcher is not available on this device');
      }
    } catch (err) {
      console.error('❌ Failed to open AR Activity:', err);
    }
  }, [navigation]);

  useEffect(() => {
    openAR();
  }, [openAR]);

  return (
    <View style={styles.centerScreen}>
      <ActivityIndicator size="large" color="#2563EB" />
      <TouchableOpacity onPress={openAR}></TouchableOpacity>
    </View>
  );
}

export function ProfileScreen({ navigation }: any) {
  return (
    <View style={styles.centerScreen}>
      <Text style={{ fontSize: 20, marginBottom: 20, color: COLORS.textDark }}>
        My Profile
      </Text>
      <TouchableOpacity
        style={styles.logoutButton}
        onPress={() => navigation.replace('SignIn')}
      >
        <Text style={{ color: 'white', fontWeight: 'bold' }}>Logout</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.white },
  centerScreen: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: COLORS.white,
  },

  // Header
  headerContainer: {
    padding: 20,
    paddingBottom: 10,
    backgroundColor: COLORS.white,
  },
  appTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    color: COLORS.primary,
    marginBottom: 15,
  },
  searchRow: { flexDirection: 'row', alignItems: 'center' },
  searchBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: COLORS.primary,
    borderRadius: 10,
    paddingHorizontal: 10,
    height: 45,
  },
  searchInput: {
    flex: 1,
    marginLeft: 10,
    fontSize: 16,
    color: COLORS.textDark,
  },
  filterBtn: {
    marginLeft: 10,
    padding: 10,
    borderWidth: 1,
    borderColor: COLORS.primary,
    borderRadius: 10,
  },
  categoryText: { fontSize: 16, color: COLORS.textLight },

  // Card Styles
  cardContainer: {
    width: COLUMN_WIDTH,
    backgroundColor: COLORS.white,
    borderRadius: 12,
    marginBottom: 15,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  cardImagePlaceholder: {
    height: 140,
    backgroundColor: '#9CA3AF', // ใช้สีเทาเดิม แต่เปลี่ยนสี Text ด้านในแทน
    borderTopLeftRadius: 12,
    borderTopRightRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  cardContent: { padding: 10 },
  cardTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: COLORS.textDark,
    marginBottom: 4,
  },
  cardPrice: { fontSize: 14, fontWeight: 'bold', color: COLORS.primary },
  cardCategory: { fontSize: 10, color: COLORS.textLight, marginTop: 2 },

  logoutButton: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    backgroundColor: '#EF4444',
    borderRadius: 8,
  },

  // --- Modal Styles ---
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    width: width * 0.9,
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 20,
    elevation: 5,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1E3A8A',
    textAlign: 'center',
    marginBottom: 20,
  },
  filterSection: { marginBottom: 20 },
  filterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 15,
  },
  filterLabel: { fontSize: 16, color: COLORS.textDark },
  divider: { height: 1, backgroundColor: '#E5E7EB' },

  // Price Inputs
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  priceInputContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: COLORS.textDark,
    borderRadius: 8,
    paddingHorizontal: 10,
    height: 45,
  },
  currency: { fontSize: 16, color: COLORS.textDark, marginRight: 5 },
  priceInput: { flex: 1, fontSize: 16, color: COLORS.textDark },

  // Modal Buttons
  modalButtonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  btnOutline: {
    flex: 1,
    marginRight: 10,
    paddingVertical: 12,
    borderRadius: 25,
    borderWidth: 1,
    borderColor: COLORS.primary,
    alignItems: 'center',
  },
  btnFilled: {
    flex: 1,
    marginLeft: 10,
    paddingVertical: 12,
    borderRadius: 25,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
  },
  btnTextOutline: { color: COLORS.primary, fontWeight: 'bold', fontSize: 16 },
  btnTextFilled: { color: 'white', fontWeight: 'bold', fontSize: 16 },
});
