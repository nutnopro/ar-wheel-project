import React, { useState } from 'react';
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

const { ARLauncher } = NativeModules;

const ProductDetailScreen = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const item: WheelModel = route.params.item;
  const { userRole, userData } = useAuth();
  const [isFavorited, setIsFavorited] = useState(false);
  const [favLoading, setFavLoading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  // เปิด AR native พร้อม localPath
  const handleTryOnAR = async () => {
    try {
      setIsDownloading(true);

      const targetUrl = Platform.OS === 'ios' ? item.iosModelUrl : item.androidModelUrl;
      const localPath = await resolveModelPath({ ...item, modelUrl: targetUrl });

      setSelectedModel({
        id: item.id,
        name: item.name,
        price: String(item.price),
        brand: item.brand,
        modelUrl: targetUrl,
        localPath,
        imageUrl: item.images?.[0] ?? '',
      });
      const modelDataList = [
        { path: localPath, name: item.name || item.id },
      ];

      if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
        const sizeStr = storage?.getString('@ar_marker_size') || '15';
        const markerSize = parseFloat(sizeStr) || 15.0;
        const pathsJsonString = JSON.stringify(modelDataList);
        console.log('🚀 Launching AR:', { localPath, pathsJsonString, markerSize });
        await ARLauncher.openARActivity(localPath, pathsJsonString, markerSize);
      } else {
        Alert.alert('AR', 'AR Launcher is not available on this device');
      }
    } catch (err: any) {
      console.error('❌ Failed to open AR Activity:', err);
      Alert.alert('Error', 'Failed to open AR: ' + (err.message || 'Unknown error'));
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" translucent backgroundColor="transparent" />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* ส่วนแสดงรูปโมเดลล้อ + ปุ่ม Back ซ้ายบนซ้อนทับเหมือนดีไซน์ */}
        <View style={styles.imageWrapper}>
          {item.images?.[0] ? (
            <Image source={{ uri: item.images[0] }} style={styles.image} resizeMode="contain" />
          ) : (
            <Icon name="cube-outline" size={40} color="#9CA3AF" />
          )}
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtnOverlay} activeOpacity={0.8}>
            <Icon name="chevron-left" size={28} color="#2563EB" />
          </TouchableOpacity>
        </View>

        <View style={styles.detailsContainer}>
          <Text style={styles.name}>{item.name || item.id}</Text>
          <Text style={styles.price}>฿{Number(item.price)?.toLocaleString() || '0'}</Text>
          <Text style={styles.meta}>
            {[item.brand, item.size && `Size: ${item.size}`, item.width && `Width: ${item.width}`]
              .filter(Boolean)
              .join('  ·  ')}
          </Text>

          <Text style={styles.description}>
            {item.description ||
              `The legendary ${item.name || 'wheel'}. High performance forged wheel designed for racing and street use. Lightweight, durable, and stylish.`}
          </Text>
        </View>
      </ScrollView>

      {/* --- Footer Buttons --- */}
      <View style={styles.footer}>
        <TouchableOpacity 
          style={[styles.arButton, isDownloading && { opacity: 0.7 }]} 
          activeOpacity={0.8} 
          onPress={handleTryOnAR}
          disabled={isDownloading}
        >
          {isDownloading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <>
              <Icon name="cube-scan" size={24} color="#fff" style={{ marginRight: 8 }} />
              <Text style={styles.arButtonText}>Try on AR</Text>
            </>
          )}
        </TouchableOpacity>

        {userRole === 'user' && (
          <TouchableOpacity
            style={styles.favButton}
            onPress={async () => {
              if (favLoading || !userData?.uid) return;
              setFavLoading(true);
              try {
                if (isFavorited) {
                  await favoritesService.remove(userData.uid, item.id);
                  setIsFavorited(false);
                } else {
                  await favoritesService.add(userData.uid, {
                    id: item.id,
                    name: item.name,
                    price: item.price,
                    images: item.images?.[0] || '',
                  });
                  setIsFavorited(true);
                }
              } catch (err) {
                console.error('Fav error:', err);
              } finally {
                setFavLoading(false);
              }
            }}>
            <Icon name={isFavorited ? 'heart' : 'heart-outline'} size={28} color="#2563EB" />
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA' },
  scrollContent: { paddingBottom: 120 },
  imageWrapper: {
    height: 320,
    margin: 20,
    backgroundColor: '#E2E8F0',
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    overflow: 'hidden',
  },
  image: { width: '80%', height: '80%' },
  backBtnOverlay: {
    position: 'absolute',
    top: Platform.OS === 'android' ? 24 : 16,
    left: 16,
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.9)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  detailsContainer: { paddingHorizontal: 24 },
  name: { fontSize: 24, fontWeight: 'bold', color: '#1E293B', marginBottom: 8 },
  price: {
    fontSize: 20,
    fontWeight: '600',
    color: '#2563EB',
    marginBottom: 6,
  },
  meta: { fontSize: 13, color: '#94A3B8', marginBottom: 16 },
  description: { fontSize: 14, color: '#64748B', lineHeight: 22 },

  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: 'row',
    padding: 20,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#E2E8F0',
    alignItems: 'center',
  },
  arButton: {
    flex: 1,
    backgroundColor: '#2563EB',
    flexDirection: 'row',
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
    elevation: 4,
  },
  arButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  favButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: '#2563EB',
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default ProductDetailScreen;
