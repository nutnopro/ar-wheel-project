import React, { useState, useEffect, useCallback } from 'react';
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
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { WheelModel } from '../../utils/types';
import api from '../../services/api';
import { useTheme } from '../../context/ThemeContext';
import { useLanguage } from '../../context/LanguageContext';
import { useAuth } from '../../context/AuthContext';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';

const { width } = Dimensions.get('window');
const COLUMN_WIDTH = width / 2 - 24;

const HomeScreen = () => {
  const navigation = useNavigation<any>();
  const { theme, isDarkMode } = useTheme();
  const { t } = useLanguage();
  const { categories: storedCategories } = useAuth();

  const [wheels, setWheels] = useState<WheelModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [lastVisibleId, setLastVisibleId] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [isFilterVisible, setFilterVisible] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');

  // Build category list from stored categories
  const categoryList = [
    { id: 'All', name: 'All' },
    ...(storedCategories || []).filter((c: any) => c.isActive !== false),
  ];

  const fetchWheels = useCallback(async (isRefresh = false) => {
    try {
      if (isRefresh) {
        setLoading(true);
        setLastVisibleId(null);
        setHasMore(true);
      }

      const params: any = {};
      if (searchQuery) params.searchTerm = searchQuery;
      if (selectedCategory !== 'All') params.categoryId = selectedCategory;
      if (minPrice) params.minPrice = Number(minPrice);
      if (maxPrice) params.maxPrice = Number(maxPrice);
      if (!isRefresh && lastVisibleId) params.lastVisibleId = lastVisibleId;

      // Add OS filter
      params.os = Platform.OS;

      const response = await api.get('/models', { params });
      const data: WheelModel[] = response.data;

      if (isRefresh) {
        setWheels(data);
      } else {
        setWheels(prev => [...prev, ...data]);
      }

      // Track last visible ID for pagination
      if (data.length > 0) {
        setLastVisibleId(data[data.length - 1].id);
      }
      if (data.length < 10) {
        setHasMore(false);
      }
    } catch (error) {
      console.error('Fetch wheels error:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
      setLoadingMore(false);
    }
  }, [searchQuery, selectedCategory, minPrice, maxPrice, lastVisibleId]);

  // Initial load
  useEffect(() => {
    fetchWheels(true);
  }, []);

  // Refresh
  const onRefresh = () => {
    setRefreshing(true);
    fetchWheels(true);
  };

  // Load more (lazy loading)
  const onEndReached = () => {
    if (!loadingMore && hasMore && !loading) {
      setLoadingMore(true);
      fetchWheels(false);
    }
  };

  // Apply filters
  const handleApplyFilter = () => {
    setFilterVisible(false);
    fetchWheels(true);
  };

  const handleResetFilter = () => {
    setSelectedCategory('All');
    setMinPrice('');
    setMaxPrice('');
  };

  // Search with debounce
  const handleSearchSubmit = () => {
    fetchWheels(true);
  };

  const renderItem = ({ item }: { item: WheelModel }) => (
    <TouchableOpacity
      activeOpacity={0.9}
      style={[styles.card, { backgroundColor: theme.card }]}
      onPress={() => navigation.navigate('ProductDetail', { item })}
    >
      <View
        style={[
          styles.imageContainer,
          { backgroundColor: isDarkMode ? '#334155' : '#fff' },
        ]}
      >
        {item.images?.[0] ? (
          <Image
            source={{ uri: item.images[0] }}
            style={styles.image}
            resizeMode="contain"
          />
        ) : (
          <MaterialCommunityIcons name="cube-outline" size={40} color="#9CA3AF" />
        )}
      </View>
      <View style={styles.cardContent}>
        <Text
          style={[styles.cardTitle, { color: theme.text }]}
          numberOfLines={1}
        >
          {item.name}
        </Text>
        <Text style={styles.cardPrice}>
          ฿{Number(item.price)?.toLocaleString()}
        </Text>
        <Text style={styles.cardCategory}>{item.brand}</Text>
      </View>
    </TouchableOpacity>
  );

  const renderFooter = () => {
    if (!loadingMore) return null;
    return (
      <View style={{ paddingVertical: 20 }}>
        <ActivityIndicator size="small" color="#2563EB" />
      </View>
    );
  };

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
              onSubmitEditing={handleSearchSubmit}
              returnKeyType="search"
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
            {categoryList.map((cat: any) => (
              <TouchableOpacity
                key={cat.id}
                onPress={() => {
                  setSelectedCategory(cat.id === 'All' ? 'All' : cat.id);
                  // Auto refresh when category changes
                  setTimeout(() => fetchWheels(true), 100);
                }}
                style={[
                  styles.quickCatPill,
                  { backgroundColor: isDarkMode ? '#334155' : '#F1F5F9' },
                  (selectedCategory === cat.id || (selectedCategory === 'All' && cat.id === 'All')) && styles.quickCatPillActive,
                ]}
              >
                <Text
                  style={[
                    styles.quickCatText,
                    (selectedCategory === cat.id || (selectedCategory === 'All' && cat.id === 'All')) && styles.quickCatTextActive,
                  ]}
                >
                  {cat.name}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      </View>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color="#2563EB" />
        </View>
      ) : (
        <FlatList
          data={wheels}
          renderItem={renderItem}
          keyExtractor={item => item.id.toString()}
          numColumns={2}
          columnWrapperStyle={styles.row}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
          onRefresh={onRefresh}
          refreshing={refreshing}
          onEndReached={onEndReached}
          onEndReachedThreshold={0.5}
          ListFooterComponent={renderFooter}
          ListEmptyComponent={
            <Text
              style={{
                textAlign: 'center',
                marginTop: 50,
                color: theme.subText,
              }}
            >
              No wheels found.
            </Text>
          }
        />
      )}

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
            <Text style={[styles.filterTitle, { color: theme.text }]}>
              Filter
            </Text>

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
                    {selectedCategory === 'All'
                      ? 'All'
                      : categoryList.find((c: any) => c.id === selectedCategory)?.name || selectedCategory}
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
                    style={[styles.priceInput, { color: theme.text }]}
                    placeholder="Min ฿"
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
                    style={[styles.priceInput, { color: theme.text }]}
                    placeholder="Max ฿"
                    placeholderTextColor={theme.subText}
                  />
                </View>
              </View>
            </View>

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
                <Text style={styles.clearSolidText}>Apply</Text>
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
  filterLabel: { fontSize: 16, fontWeight: '500' },
  filterRowRight: { flexDirection: 'row', alignItems: 'center' },
  filterValue: { fontSize: 14, marginRight: 6 },
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
  priceInput: { fontSize: 14 },
  priceSeparator: { marginHorizontal: 12, fontSize: 16, fontWeight: '600' },
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
  clearOutlineText: { fontSize: 15, fontWeight: '600' },
  clearSolidButton: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 10,
  },
  clearSolidText: { fontSize: 15, fontWeight: '700', color: '#fff' },
});

export default HomeScreen;
