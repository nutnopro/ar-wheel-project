import React, {useState, useEffect, useCallback} from 'react';
import {
  View,
  Text,
  FlatList,
  Image,
  StyleSheet,
  TouchableOpacity,
  Dimensions,
  ActivityIndicator,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {useTheme} from '../../context/ThemeContext';
import api from '../../services/api';
import {WheelModel} from '../../utils/types';

const {width} = Dimensions.get('window');
const COLUMN_WIDTH = width / 2 - 24;

import Header from '../../components/Header';

const FavoritesScreen = () => {
  const navigation = useNavigation<any>();
  const {theme} = useTheme();

  const [favorites, setFavorites] = useState<WheelModel[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchFavorites = useCallback(async () => {
    try {
      setLoading(true);
      // ใช้ /models ไปก่อน — เปลี่ยนเป็น /favorites เมื่อ backend พร้อม
      const response = await api.get('/models');
      setFavorites(response.data);
    } catch (error) {
      console.error('Fetch favorites error:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchFavorites();
  }, [fetchFavorites]);

  const renderItem = ({item}: {item: WheelModel}) => (
    <TouchableOpacity
      activeOpacity={0.9}
      style={[styles.card, {backgroundColor: theme.card}]}
      onPress={() => navigation.navigate('ProductDetail', {item})}>
      <View style={styles.imageContainer}>
        <Image
          source={
            item.images?.[0]
              ? {uri: item.images[0]}
              : require('../../assets/cube')
          }
          style={styles.image}
          resizeMode="contain"
        />
      </View>
      <View style={styles.cardContent}>
        <Text
          style={[styles.cardTitle, {color: theme.text}]}
          numberOfLines={1}>
          {item.name || item.id}
        </Text>
        <Text style={styles.cardPrice}>
          ${Number(item.price)?.toLocaleString() || '0'}
        </Text>
        <Text style={styles.cardCategory}>{item.brand}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.container, {backgroundColor: theme.background}]}>
      <Header title="Favorites" />
      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#2563EB" />
        </View>
      ) : (
        <FlatList
          data={favorites}
          renderItem={renderItem}
          keyExtractor={item => item.id}
          numColumns={2}
          columnWrapperStyle={styles.row}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={{color: theme.subText}}>No favorites yet.</Text>
            </View>
          }
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1},
  loadingContainer: {flex: 1, justifyContent: 'center', alignItems: 'center'},
  listContent: {padding: 16},
  row: {justifyContent: 'space-between'},
  card: {
    width: COLUMN_WIDTH,
    borderRadius: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
    overflow: 'hidden',
  },
  imageContainer: {
    height: 130,
    backgroundColor: '#F1F5F9',
    justifyContent: 'center',
    alignItems: 'center',
  },
  image: {width: '80%', height: '80%'},
  cardContent: {padding: 12},
  cardTitle: {fontSize: 14, fontWeight: '600', marginBottom: 4},
  cardPrice: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#2563EB',
    marginBottom: 2,
  },
  cardCategory: {fontSize: 11, color: '#94A3B8'},
  emptyContainer: {alignItems: 'center', marginTop: 50},
});

export default FavoritesScreen;
