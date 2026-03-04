import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  FlatList,
  Image,
  Platform,
  StatusBar,
  NativeModules,
  ActivityIndicator,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation, useRoute } from '@react-navigation/native';
import api from '../../services/api';
import { setSelectedModel, setModelPaths } from '../../utils/storage';
import { resolveModelPath } from '../../services/modelCacheService';
import { WheelModel } from '../../utils/types';

const { ARLauncher } = NativeModules;

const ArScreen = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();

  // 1. รับค่า item ที่ส่งมาจากหน้า ProductDetail (ถ้ามี)
  const incomingItem: WheelModel | undefined = route.params?.item;

  // 2. State สำหรับข้อมูลจาก API
  const [wheels, setWheels] = useState<WheelModel[]>([]);
  const [loading, setLoading] = useState(true);

  // 3. State สำหรับโมเดลที่กำลังโชว์
  const [currentModel, setCurrentModel] = useState<WheelModel | null>(
    incomingItem || null,
  );

  // ดึงข้อมูลจาก API
  const fetchWheels = useCallback(async () => {
    try {
      setLoading(true);
      const response = await api.get('/models');
      const data: WheelModel[] = response.data;
      setWheels(data);
      // ถ้าไม่มี incomingItem ให้ใช้ตัวแรกจาก API
      if (!currentModel && data.length > 0) {
        setCurrentModel(data[0]);
      }
    } catch (error) {
      console.error('Fetch wheels error:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchWheels();
  }, [fetchWheels]);

  // อัปเดต state ถ้ามีการส่งค่าใหม่เข้ามา
  useEffect(() => {
    if (incomingItem) {
      setCurrentModel(incomingItem);
    }
  }, [incomingItem]);

  // เลือกโมเดล → บันทึกลง MMKV + อัปเดต state
  const handleSelectModel = (item: WheelModel) => {
    setCurrentModel(item);
    setSelectedModel({
      id: item.id,
      name: item.name,
      price: item.price,
      brand: item.brand,
      modelUrl: item.modelUrl,
      imageUrl: item.images?.[0] ?? '',
    });
  };

  const handleBack = () => {
    if (navigation.canGoBack()) {
      navigation.goBack();
    } else {
      navigation.navigate('Home');
    }
  };

  // เปิด AR native พร้อม localPath (หรือ modelUrl ถ้า resolve ไม่ได้)
  const handleOpenAR = async () => {
    if (!currentModel) return;

    try {
      // resolve localPath ของโมเดลที่เลือก (download + cache ถ้ายังไม่มี)
      const localPath = await resolveModelPath(currentModel);

      // resolve paths ของโมเดลทั้งหมดใน list สำหรับ carousel ใน AR native
      const allPaths = await Promise.all(wheels.map(w => resolveModelPath(w)));
      setModelPaths(allPaths);

      setSelectedModel({
        id: currentModel.id,
        name: currentModel.name,
        price: currentModel.price,
        brand: currentModel.brand,
        modelUrl: currentModel.modelUrl,
        localPath,
        imageUrl: currentModel.images?.[0] ?? '',
      });

      if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
        await ARLauncher.openARActivity(localPath, JSON.stringify(allPaths));
      } else {
        Alert.alert('AR', 'AR Launcher is not available on this device');
      }
    } catch (err) {
      console.error('❌ Failed to open AR Activity:', err);
      Alert.alert('Error', 'Failed to prepare model for AR');
    }
  };

  const renderModelItem = ({ item }: { item: WheelModel }) => {
    const isActive = currentModel?.id === item.id;
    return (
      <TouchableOpacity
        onPress={() => handleSelectModel(item)}
        activeOpacity={0.8}
        style={styles.modelItem}
      >
        <View
          style={[
            styles.modelImageContainer,
            isActive && styles.modelImageContainerActive,
          ]}
        >
          <Image
            source={
              item.images?.[0]
                ? { uri: item.images[0] }
                : require('../../assets/cube.png')
            }
            style={styles.modelImage}
            resizeMode="contain"
          />
        </View>
        <Text style={styles.modelName} numberOfLines={1}>
          {item.name || item.id}
        </Text>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.container}>
      <StatusBar
        barStyle="light-content"
        backgroundColor="transparent"
        translucent
      />

      {/* --- ส่วนแสดงผลกล้อง (Mockup) --- */}
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.cameraText}>view of camera</Text>
        <Text style={styles.previewText}>{currentModel?.name}</Text>
      </View>

      {/* --- ปุ่มย้อนกลับ (Header) --- */}
      <SafeAreaView style={styles.headerArea}>
        <TouchableOpacity style={styles.backButton} onPress={handleBack}>
          <Icon name="chevron-left" size={40} color="#2563EB" />
        </TouchableOpacity>
      </SafeAreaView>

      {/* --- ส่วนควบคุมด้านล่าง (Footer) --- */}
      <View style={styles.footerArea}>
        {/* Carousel เลือกโมเดล */}
        <View style={styles.carouselContainer}>
          {loading ? (
            <View
              style={{
                flex: 1,
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <ActivityIndicator size="small" color="#2563EB" />
            </View>
          ) : (
            <FlatList
              data={wheels}
              horizontal
              showsHorizontalScrollIndicator={false}
              renderItem={renderModelItem}
              keyExtractor={item => item.id}
              contentContainerStyle={styles.carouselContent}
            />
          )}
        </View>

        {/* ปุ่ม Shutter & Menu */}
        <View style={styles.controlsRow}>
          <View style={styles.sideControl} />

          <TouchableOpacity
            style={styles.shutterButtonOuter}
            activeOpacity={0.7}
            onPress={handleOpenAR}
          >
            <View style={styles.shutterButtonInner} />
          </TouchableOpacity>

          <TouchableOpacity style={[styles.sideControl, styles.menuButton]}>
            <Icon name="dots-grid" size={24} color="#fff" />
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  cameraPlaceholder: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#F8F9FA',
    justifyContent: 'center',
    alignItems: 'center',
  },
  cameraText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1E293B',
    marginBottom: 10,
  },
  previewText: {
    fontSize: 16,
    color: '#64748B',
  },
  headerArea: {
    position: 'absolute',
    top: 0,
    left: 0,
    zIndex: 10,
    paddingTop: Platform.OS === 'android' ? 30 : 0,
  },
  backButton: {
    marginLeft: 20,
    marginTop: 10,
    width: 44,
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.8)',
    borderRadius: 22,
  },
  footerArea: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    zIndex: 10,
    paddingBottom: 40,
  },
  carouselContainer: {
    marginBottom: 25,
    height: 110,
  },
  carouselContent: {
    paddingHorizontal: 20,
    alignItems: 'center',
  },
  modelItem: {
    width: 85,
    height: 100,
    marginRight: 12,
    alignItems: 'center',
    justifyContent: 'flex-start',
  },
  modelImageContainer: {
    width: 70,
    height: 70,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  modelImageContainerActive: {
    borderColor: '#2563EB',
    backgroundColor: '#fff',
  },
  modelImage: {
    width: 50,
    height: 50,
  },
  modelName: {
    fontSize: 11,
    color: '#1E293B',
    textAlign: 'center',
    fontWeight: '600',
    backgroundColor: 'rgba(255,255,255,0.8)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    overflow: 'hidden',
  },
  controlsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 50,
  },
  sideControl: {
    width: 50,
    height: 50,
  },
  shutterButtonOuter: {
    width: 72,
    height: 72,
    borderRadius: 36,
    borderWidth: 4,
    borderColor: 'rgba(200,200,200,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  shutterButtonInner: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#fff',
    elevation: 5,
  },
  menuButton: {
    backgroundColor: 'rgba(0,0,0,0.4)',
    borderRadius: 25,
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default ArScreen;
