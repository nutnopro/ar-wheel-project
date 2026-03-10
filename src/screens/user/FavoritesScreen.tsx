import React, {useState, useCallback} from 'react';
import {
  View,
  Text,
  FlatList,
  Image,
  StyleSheet,
  TouchableOpacity,
  Dimensions,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import {useTheme} from '../../context/ThemeContext';
import {useAuth} from '../../context/AuthContext';
import {favoritesService} from '../../services/favoritesService';
import Header from '../../components/Header';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';

const {width} = Dimensions.get('window');
const COLUMN_WIDTH = width / 2 - 24;

const FavoritesScreen = () => {
  const navigation = useNavigation<any>();
  const {theme} = useTheme();
  const {userData} = useAuth();

  const [favorites, setFavorites] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchFavorites = useCallback(async () => {
    const uid = userData?.id || userData?.uid;
    if (!uid) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const response = await favoritesService.getAll(uid);
      setFavorites(response.data);
    } catch (error) {
      console.error('Fetch favorites error:', error);
      // Alert.alert('Error', 'Could not load favorites');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [userData]);

  useFocusEffect(
    useCallback(() => {
      fetchFavorites();
    }, [fetchFavorites])
  );

  const handleRemoveFavorite = async (modelId: string) => {
    const uid = userData?.id || userData?.uid;
    if (!uid) {
      Alert.alert('Error', 'User ID not found');
      return;
    }
    
    try {
      await favoritesService.remove(uid, modelId);
      // อัปเดต UI ทันทีโดยไม่ต้องรอโหลดใหม่ทั้งหมด
      setFavorites(prev => prev.filter(item => item.id !== modelId));
    } catch (error) {
      console.error('Remove favorite error:', error);
      Alert.alert('Error', 'Failed to remove favorite');
    }
  };

  const renderItem = ({item}: {item: any}) => {
    const imageSource = typeof item.images === 'string' 
      ? item.images 
      : (Array.isArray(item.images) ? item.images[0] : null);

    return (
      <TouchableOpacity
        activeOpacity={0.9}
        style={[styles.card, {backgroundColor: theme.card}]}
        onPress={() => navigation.navigate('ProductDetail', { item: { ...item, images: [imageSource] } })}>
        <View style={styles.imageContainer}>
          {imageSource ? (
            <Image
              source={{ uri: imageSource }}
              style={styles.image}
              resizeMode="contain"
            />
          ) : (
            <MaterialCommunityIcons name="cube-outline" size={40} color="#9CA3AF" />
          )}
          
          {/* ปุ่มลบออกจาก Favorite ทันที */}
          <TouchableOpacity 
            style={styles.removeBadge}
            onPress={() => handleRemoveFavorite(item.id)}>
            <MaterialCommunityIcons name="heart" size={18} color="#EF4444" />
          </TouchableOpacity>
        </View>
        <View style={styles.cardContent}>
          <Text style={[styles.cardTitle, {color: theme.text}]} numberOfLines={1}>
            {item.name}
          </Text>
          <Text style={styles.cardPrice}>
            ฿{Number(item.price)?.toLocaleString() || '0'}
          </Text>
          {item.brand && <Text style={styles.cardCategory}>{item.brand}</Text>}
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={[styles.container, {backgroundColor: theme.background}]}>
      <Header title="My Favorites" />
      {loading && !refreshing ? (
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
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                fetchFavorites();
              }}
              tintColor="#2563EB"
            />
          }
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <MaterialCommunityIcons name="heart-off-outline" size={80} color={theme.subText} />
              <Text style={[styles.emptyText, {color: theme.subText}]}>
                No favorites found
              </Text>
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
  listContent: {padding: 16, paddingBottom: 100},
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
  },
  imageContainer: {
    height: 130,
    backgroundColor: '#F1F5F9',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative'
  },
  image: {width: '80%', height: '80%'},
  removeBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: '#fff',
    padding: 5,
    borderRadius: 15,
    elevation: 3,
  },
  cardContent: {padding: 12},
  cardTitle: {fontSize: 14, fontWeight: '600', marginBottom: 4},
  cardPrice: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#2563EB',
    marginBottom: 2,
  },
  cardCategory: {fontSize: 11, color: '#94A3B8'},
  emptyContainer: {alignItems: 'center', marginTop: 100},
  emptyText: {fontSize: 16, marginTop: 15, fontWeight: '500'},
});

export default FavoritesScreen;
