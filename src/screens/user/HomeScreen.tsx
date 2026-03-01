import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  Image,
  StyleSheet,
  TouchableOpacity,
  Dimensions,
  TextInput,
  Platform,
  StatusBar,
  Modal,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { MOCK_WHEELS, Wheel } from '../../data/mockData';

// [NEW] Import Contexts
import { useTheme } from '../../context/ThemeContext';
import { useLanguage } from '../../context/LanguageContext';

const { width } = Dimensions.get('window');
const COLUMN_WIDTH = width / 2 - 24;

const HomeScreen = () => {
  const navigation = useNavigation<any>();

  // เรียกใช้ Hooks
  const { theme, isDarkMode } = useTheme();
  const { t } = useLanguage();

  const [searchQuery, setSearchQuery] = useState('');
  const [isFilterVisible, setFilterVisible] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [selectedBrand, setSelectedBrand] = useState('All');
  const [selectedSize, setSelectedSize] = useState('All');
  const [minPrice, setMinPrice] = useState('0');
  const [maxPrice, setMaxPrice] = useState('999');

  const categories = [
    'All',
    'Sport',
    'Luxury',
    'Minimal',
    'Classic',
    'Off-road',
  ];
  const brands = ['All', 'BBS', 'Vossen', 'Rays', 'Enkei', 'HRE', 'OZ Racing'];
  const sizes = ['All', '17"', '18"', '19"', '20"', '21"', '22"'];

  const handleApplyFilter = () => {
    // ตอนนี้เป็นเพียง UI mock ตามดีไซน์
    // สามารถเพิ่ม logic filter จริงภายหลังได้
    setFilterVisible(false);
  };

  const handleResetFilter = () => {
    setSelectedCategory('All');
    setSelectedBrand('All');
    setSelectedSize('All');
    setMinPrice('0');
    setMaxPrice('999');
  };

  const renderItem = ({ item }: { item: Wheel }) => (
    <TouchableOpacity
      activeOpacity={0.9}
      style={[styles.card, { backgroundColor: theme.card }]} // ใช้สีจากการ์ด theme
      onPress={() => navigation.navigate('ProductDetail', { item })}
    >
      <View
        style={[
          styles.imageContainer,
          { backgroundColor: isDarkMode ? '#334155' : '#fff' },
        ]}
      >
        <Image
          source={{ uri: item.image }}
          style={styles.image}
          resizeMode="contain"
        />
      </View>
      <View style={styles.cardContent}>
        <Text
          style={[styles.cardTitle, { color: theme.text }]}
          numberOfLines={1}
        >
          {item.name}
        </Text>
        <Text style={styles.cardPrice}>${item.price.toLocaleString()}</Text>
        <Text style={styles.cardCategory}>{item.brand}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor="transparent"
        translucent
      />

      {/* Header */}
      <View style={[styles.headerWrapper, { backgroundColor: theme.card }]}>
        <View style={styles.headerTop}>
          <Text style={[styles.appName, { color: theme.text }]}>
            {t.app_name}
          </Text>
          <TouchableOpacity>
            <Icon name="bell-outline" size={24} color={theme.text} />
          </TouchableOpacity>
        </View>

        <View style={styles.searchRow}>
          <View
            style={[
              styles.searchBar,
              { backgroundColor: isDarkMode ? '#334155' : '#F1F5F9' },
            ]}
          >
            <Icon
              name="magnify"
              size={24}
              color="#2563EB"
              style={{ marginRight: 8 }}
            />
            <TextInput
              placeholder={t.search_placeholder}
              placeholderTextColor={theme.subText}
              style={[styles.searchInput, { color: theme.text }]}
              value={searchQuery}
              onChangeText={setSearchQuery}
            />
          </View>
          <TouchableOpacity
            style={[
              styles.filterBtn,
              { backgroundColor: theme.card, borderColor: theme.border },
            ]}
            onPress={() => setFilterVisible(true)}
          >
            <Icon name="tune-variant" size={24} color="#2563EB" />
          </TouchableOpacity>
        </View>

        <View style={styles.quickCategoryContainer}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {categories.slice(0, 5).map(cat => (
              <TouchableOpacity
                key={cat}
                onPress={() => setSelectedCategory(cat)}
                style={[
                  styles.quickCatPill,
                  { backgroundColor: isDarkMode ? '#334155' : '#F1F5F9' },
                  selectedCategory === cat && styles.quickCatPillActive,
                ]}
              >
                <Text
                  style={[
                    styles.quickCatText,
                    selectedCategory === cat && styles.quickCatTextActive,
                  ]}
                >
                  {cat}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      </View>

      {/* Product List */}
      <FlatList
        data={MOCK_WHEELS}
        renderItem={renderItem}
        keyExtractor={item => item.id}
        numColumns={2}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      />

      {/* Filter Modal */}
      <Modal
        animationType="fade"
        transparent
        visible={isFilterVisible}
        onRequestClose={() => setFilterVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View
            style={[
              styles.modalContainer,
              { backgroundColor: theme.background },
            ]}
          >
            {/* ชื่อหัวข้อใหญ่ตรงกลางเหมือนดีไซน์ตัวอย่าง */}
            <Text style={[styles.filterTitle, { color: theme.text }]}>
              Filter
            </Text>

            {/* การ์ดด้านในสำหรับตัวเลือก Category / Brand / Size / Price */}
            <View style={[styles.filterCard, { backgroundColor: theme.card }]}>
              {/* Category */}
              <TouchableOpacity
                style={[styles.filterRow, { borderBottomColor: theme.border }]}
                activeOpacity={0.7}
              >
                <Text style={[styles.filterLabel, { color: theme.text }]}>
                  Category
                </Text>
                <View style={styles.filterRowRight}>
                  <Text style={[styles.filterValue, { color: theme.subText }]}>
                    {selectedCategory}
                  </Text>
                  <Icon name="chevron-right" size={20} color={theme.subText} />
                </View>
              </TouchableOpacity>

              {/* Brand */}
              <TouchableOpacity
                style={[styles.filterRow, { borderBottomColor: theme.border }]}
                activeOpacity={0.7}
              >
                <Text style={[styles.filterLabel, { color: theme.text }]}>
                  Brand
                </Text>
                <View style={styles.filterRowRight}>
                  <Text style={[styles.filterValue, { color: theme.subText }]}>
                    {selectedBrand}
                  </Text>
                  <Icon name="chevron-right" size={20} color={theme.subText} />
                </View>
              </TouchableOpacity>

              {/* Size */}
              <TouchableOpacity
                style={[styles.filterRow, { borderBottomColor: theme.border }]}
                activeOpacity={0.7}
              >
                <Text style={[styles.filterLabel, { color: theme.text }]}>
                  Size
                </Text>
                <View style={styles.filterRowRight}>
                  <Text style={[styles.filterValue, { color: theme.subText }]}>
                    {selectedSize}
                  </Text>
                  <Icon name="chevron-right" size={20} color={theme.subText} />
                </View>
              </TouchableOpacity>

              {/* Price Range */}
              <View style={styles.priceRow}>
                <View
                  style={[
                    styles.priceInputWrapper,
                    { borderColor: theme.border },
                  ]}
                >
                  <TextInput
                    value={minPrice}
                    onChangeText={setMinPrice}
                    keyboardType="numeric"
                    style={styles.priceInput}
                    placeholder="$ 0"
                    placeholderTextColor={theme.subText}
                  />
                </View>
                <Text style={[styles.priceSeparator, { color: theme.subText }]}>
                  -
                </Text>
                <View
                  style={[
                    styles.priceInputWrapper,
                    { borderColor: theme.border },
                  ]}
                >
                  <TextInput
                    value={maxPrice}
                    onChangeText={setMaxPrice}
                    keyboardType="numeric"
                    style={styles.priceInput}
                    placeholder="$ 999"
                    placeholderTextColor={theme.subText}
                  />
                </View>
              </View>
            </View>

            {/* ปุ่มด้านล่างสองข้าง "Clear All" */}
            <View style={styles.modalButtonsRow}>
              <TouchableOpacity
                style={[styles.clearOutlineButton, { borderColor: '#2563EB' }]}
                onPress={handleResetFilter}
                activeOpacity={0.8}
              >
                <Text style={[styles.clearOutlineText, { color: '#2563EB' }]}>
                  Clear All
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[
                  styles.clearSolidButton,
                  { backgroundColor: '#2563EB' },
                ]}
                onPress={handleApplyFilter}
                activeOpacity={0.8}
              >
                <Text style={styles.clearSolidText}>Clear All</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  headerWrapper: {
    paddingHorizontal: 20,
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight || 24 : 44,
    paddingBottom: 15,
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 3,
    zIndex: 10,
  },
  headerTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 15,
  },
  appName: { fontSize: 24, fontWeight: '800' },
  searchRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 15 },
  searchBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 48,
    marginRight: 10,
  },
  searchInput: { flex: 1, fontSize: 16 },
  filterBtn: {
    width: 48,
    height: 48,
    borderRadius: 12,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  quickCategoryContainer: { marginTop: 5 },
  quickCatPill: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    marginRight: 10,
  },
  quickCatPillActive: { backgroundColor: '#2563EB' },
  quickCatText: { fontSize: 14, color: '#94A3B8', fontWeight: '500' },
  quickCatTextActive: { color: '#fff' },
  listContent: { paddingHorizontal: 16, paddingTop: 20, paddingBottom: 100 },
  row: { justifyContent: 'space-between' },
  card: {
    width: COLUMN_WIDTH,
    borderRadius: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
    overflow: 'hidden',
  },
  imageContainer: {
    height: 130,
    justifyContent: 'center',
    alignItems: 'center',
  },
  image: { width: '80%', height: '80%' },
  cardContent: { padding: 12 },
  cardTitle: { fontSize: 14, fontWeight: '600', marginBottom: 4 },
  cardPrice: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#2563EB',
    marginBottom: 2,
  },
  cardCategory: { fontSize: 11, color: '#94A3B8' },

  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContainer: {
    width: '88%',
    borderRadius: 24,
    paddingHorizontal: 24,
    paddingVertical: 28,
  },
  filterTitle: {
    fontSize: 22,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 20,
  },
  filterCard: {
    borderRadius: 24,
    paddingHorizontal: 20,
    paddingVertical: 20,
  },
  filterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  filterLabel: {
    fontSize: 16,
    fontWeight: '500',
  },
  filterRowRight: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  filterValue: {
    fontSize: 14,
    marginRight: 6,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: 16,
  },
  priceInputWrapper: {
    flex: 1,
    height: 44,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    justifyContent: 'center',
  },
  priceInput: {
    fontSize: 14,
  },
  priceSeparator: {
    marginHorizontal: 12,
    fontSize: 16,
    fontWeight: '600',
  },
  modalButtonsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 24,
  },
  clearOutlineButton: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
    backgroundColor: '#E5EDFF',
  },
  clearOutlineText: {
    fontSize: 15,
    fontWeight: '600',
  },
  clearSolidButton: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 10,
  },
  clearSolidText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#fff',
  },
});

export default HomeScreen;
