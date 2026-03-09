import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  SafeAreaView,
  StatusBar,
  Platform,
  NativeModules,
  Alert,
  ActivityIndicator,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { setSelectedModel, storage } from '../../utils/storage';
import { resolveModelPath } from '../../services/modelCacheService';
import { WheelModel } from '../../utils/types';
import { useAuth } from '../../context/AuthContext';
import { favoritesService } from '../../services/favoritesService';
import { useTheme } from '../../context/ThemeContext';
import { productService } from '../../services/productService';

const { ARLauncher } = NativeModules;

const ProductDetailScreen = () => {
  const { theme } = useTheme();
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const item: WheelModel = route.params.item;
  
  const { userRole, userData } = useAuth();
  const [isFavorited, setIsFavorited] = useState(false);
  const [favLoading, setFavLoading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

useEffect(() => {
  const checkFavoriteStatus = async () => {
    const uid = userData?.uid || userData?.id;
    if (!uid || userRole !== 'user') return;

    try {
      const response = await favoritesService.getAll(uid); 
      
      const favs = response.data; 
      
      const found = favs.some((fav: any) => fav.id === item.id);
      setIsFavorited(found);
    } catch (err) {
      console.error('Check favorite error:', err);
    }
  };

  checkFavoriteStatus();
}, [userData, item.id, userRole]);
  const handleTryOnAR = async () => {
    try {
      setIsDownloading(true);
      const allModelsResponse = await productService.getAll(); 
      const allModels: WheelModel[] = allModelsResponse.data || allModelsResponse;

      const modelDataList = await Promise.all(
        allModels.map(async (m) => {
          const targetUrl = Platform.OS === 'ios' ? m.iosModelUrl : m.androidModelUrl;
          
          const localPath = await resolveModelPath({ ...m, modelUrl: targetUrl });
          
          return {
            id: m.id,
            path: localPath,
            name: m.name || m.id,
            brand: m.brand,
            price: String(m.price),
            imageUrl: m.images?.[0] || '',
          };
        })
      );

      const currentTargetUrl = Platform.OS === 'ios' ? item.iosModelUrl : item.androidModelUrl;
      const currentLocalPath = await resolveModelPath({ ...item, modelUrl: currentTargetUrl });

      setSelectedModel({
        id: item.id,
        name: item.name,
        price: String(item.price),
        brand: item.brand,
        modelUrl: currentTargetUrl,
        localPath: currentLocalPath,
        imageUrl: item.images?.[0] ?? '',
      });

      if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
        const sizeStr = storage?.getString('@ar_marker_size') || '15';
        const markerSize = parseFloat(sizeStr) || 15.0;
        const pathsJsonString = JSON.stringify(modelDataList);
        console.log('🚀 Launching AR with All Wheels:', modelDataList.length, 'items');
        await ARLauncher.openARActivity(currentLocalPath, pathsJsonString, markerSize);
      } else {
        Alert.alert('AR', 'AR Launcher is not available on this device');
      }
    } catch (err: any) {
      console.error('❌ Failed to open AR Activity:', err);
      Alert.alert('Error', 'Failed to prepare models: ' + (err.message || 'Unknown error'));
    } finally {
      setIsDownloading(false);
    }
  };

  const SpecItem = ({ label, value, icon }: { label: string, value?: string, icon: string }) => {
    if (!value) return null;
    return (
      <View style={styles.specBox}>
        <Icon name={icon} size={20} color="#2563EB" />
        <View style={{ marginLeft: 8 }}>
          <Text style={styles.specLabel}>{label}</Text>
          <Text style={styles.specValue}>{value}</Text>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <StatusBar barStyle="dark-content" translucent backgroundColor="transparent" />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Image Section */}
        <View style={[styles.imageWrapper, { backgroundColor: theme.card }]}>
          {item.images?.[0] ? (
            <Image source={{ uri: item.images[0] }} style={styles.image} resizeMode="contain" />
          ) : (
            <Icon name="cube-outline" size={60} color="#9CA3AF" />
          )}
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtnOverlay}>
            <Icon name="chevron-left" size={28} color="#2563EB" />
          </TouchableOpacity>
        </View>

        <View style={styles.detailsContainer}>
          {/* Header Info */}
          <Text style={[styles.brandText, { color: '#2563EB' }]}>{item.brand || 'Premium Wheel'}</Text>
          <Text style={[styles.name, { color: theme.text }]}>{item.name}</Text>
          <Text style={styles.price}>฿{Number(item.price)?.toLocaleString()}</Text>

          {/* Categories / Tags */}
          {item.categories && item.categories.length > 0 && (
            <View style={styles.tagContainer}>
              {item.categories.map((cat, index) => (
                <View key={index} style={styles.tag}>
                  <Text style={styles.tagText}>{cat}</Text>
                </View>
              ))}
            </View>
          )}

          <View style={styles.divider} />

          {/* Specifications Grid */}
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Specifications</Text>
          <View style={styles.specGrid}>
            <SpecItem label="Size" value={item.size} icon="diameter-variant" />
            <SpecItem label="Width" value={item.width} icon="arrow-expand-horizontal" />
            <SpecItem label="Offset" value={item.offset} icon="ray-start-arrow" />
            <SpecItem label="PCD" value={item.pcd} icon="dots-circle" />
          </View>

          <View style={styles.divider} />

          {/* Description */}
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Description</Text>
          <Text style={[styles.description, { color: theme.subText }]}>
            {item.description || "No description provided for this model."}
          </Text>
        </View>
      </ScrollView>

      {/* Footer Actions */}
      <View style={[styles.footer, { backgroundColor: theme.card, borderTopColor: theme.border }]}>
        <TouchableOpacity 
          style={[styles.arButton, isDownloading && { opacity: 0.7 }]} 
          activeOpacity={0.8} 
          onPress={handleTryOnAR}
          disabled={isDownloading}
        >
          {isDownloading ? <ActivityIndicator color="#fff" /> : (
            <>
              <Icon name="cube-scan" size={24} color="#fff" style={{ marginRight: 8 }} />
              <Text style={styles.arButtonText}>Try on AR</Text>
            </>
          )}
        </TouchableOpacity>

        {userRole === 'user' && (
          <TouchableOpacity
            style={[styles.favButton, { borderColor: isFavorited ? '#EF4444' : '#2563EB' }]}
            onPress={async () => {
                const uid = userData?.uid || userData?.id;
                if (favLoading || !uid) return;
                setFavLoading(true);
                try {
                  if (isFavorited) {
                    await favoritesService.remove(uid, item.id);
                    setIsFavorited(false);
                  } else {
                    await favoritesService.add(uid, {
                      id: item.id,
                      name: item.name,
                      price: item.price,
                      images: item.images?.[0] || '',
                    });
                    setIsFavorited(true);
                  }
                } finally {
                  setFavLoading(false);
                }
            }}>
            {favLoading ? (
              <ActivityIndicator size="small" color="#2563EB" />
            ) : (
              <Icon name={isFavorited ? 'heart' : 'heart-outline'} size={28} color={isFavorited ? '#EF4444' : '#2563EB'} />
            )}
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  scrollContent: { paddingBottom: 120 },
  imageWrapper: {
    height: 350,
    margin: 16,
    borderRadius: 32,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
  },
  image: { width: '85%', height: '85%' },
  backBtnOverlay: {
    position: 'absolute',
    top: 20,
    left: 20,
    width: 45,
    height: 45,
    borderRadius: 22.5,
    backgroundColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 5,
  },
  detailsContainer: { paddingHorizontal: 24 },
  brandText: { fontSize: 14, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 4 },
  name: { fontSize: 28, fontWeight: 'bold', marginBottom: 8 },
  price: { fontSize: 24, fontWeight: '800', color: '#2563EB', marginBottom: 16 },
  
  tagContainer: { flexDirection: 'row', flexWrap: 'wrap', marginBottom: 16 },
  tag: { backgroundColor: '#EFF6FF', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, marginRight: 8, marginBottom: 8 },
  tagText: { color: '#2563EB', fontSize: 12, fontWeight: '600' },
  
  divider: { height: 1, backgroundColor: '#E2E8F0', marginVertical: 20 },
  sectionTitle: { fontSize: 18, fontWeight: '700', marginBottom: 16 },
  
  specGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  specBox: {
    width: '48%',
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F8FAFC',
    padding: 12,
    borderRadius: 12,
    marginBottom: 12,
  },
  specLabel: { fontSize: 11, color: '#94A3B8', fontWeight: '500' },
  specValue: { fontSize: 14, color: '#1E293B', fontWeight: '700' },
  
  description: { fontSize: 15, lineHeight: 24 },

  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: 'row',
    padding: 20,
    paddingBottom: Platform.OS === 'ios' ? 34 : 20,
    borderTopWidth: 1,
    alignItems: 'center',
  },
  arButton: {
    flex: 1,
    backgroundColor: '#2563EB',
    flexDirection: 'row',
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
    elevation: 8,
    shadowColor: '#2563EB',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  arButtonText: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
  favButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 2,
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default ProductDetailScreen;
