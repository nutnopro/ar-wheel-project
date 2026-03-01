import React, { useState, useEffect } from 'react';
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
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { MOCK_WHEELS } from '../../data/mockData'; // ตรวจสอบ path นี้ว่าถูกต้อง

const ArScreen = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();

  // 1. รับค่า item ที่ส่งมาจากหน้า ProductDetail (ถ้ามี)
  const incomingItem = route.params?.item;

  // 2. State สำหรับโมเดลที่กำลังโชว์
  const [currentModel, setCurrentModel] = useState(
    incomingItem || MOCK_WHEELS[0],
  );

  // อัปเดต state ถ้ามีการส่งค่าใหม่เข้ามา
  useEffect(() => {
    if (incomingItem) {
      setCurrentModel(incomingItem);
    }
  }, [incomingItem]);

  const handleBack = () => {
    // ถ้าเข้ามาจาก ProductDetail ให้ย้อนกลับได้
    if (navigation.canGoBack()) {
      navigation.goBack();
    } else {
      // ถ้ากดจาก Tab Bar ให้กลับไปหน้า Home
      navigation.navigate('Home');
    }
  };

  const renderModelItem = ({ item }: { item: any }) => {
    const isActive = currentModel?.id === item.id;
    return (
      <TouchableOpacity
        onPress={() => setCurrentModel(item)}
        activeOpacity={0.8}
        style={styles.modelItem}
      >
        {/* ใช้ Array styles เพื่อเปลี่ยนสีขอบเมื่อถูกเลือก */}
        <View
          style={[
            styles.modelImageContainer,
            isActive && styles.modelImageContainerActive,
          ]}
        >
          <Image
            source={{ uri: item.image }}
            style={styles.modelImage}
            resizeMode="contain"
          />
        </View>
        <Text style={styles.modelName} numberOfLines={1}>
          {item.name}
        </Text>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.container}>
      {/* ทำให้ Status Bar โปร่งใสเพื่อให้กล้องเต็มจอ */}
      <StatusBar
        barStyle="light-content"
        backgroundColor="transparent"
        translucent
      />

      {/* --- ส่วนแสดงผลกล้อง (Mockup) --- */}
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.cameraText}>view of camera</Text>
        {/* แสดงชื่อรุ่นที่กำลังลองอยู่กลางจอ */}
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
          <FlatList
            data={MOCK_WHEELS}
            horizontal
            showsHorizontalScrollIndicator={false}
            renderItem={renderModelItem}
            keyExtractor={item => item.id}
            contentContainerStyle={styles.carouselContent}
          />
        </View>

        {/* ปุ่ม Shutter & Menu */}
        <View style={styles.controlsRow}>
          <View style={styles.sideControl} />

          <TouchableOpacity
            style={styles.shutterButtonOuter}
            activeOpacity={0.7}
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
    backgroundColor: '#F8F9FA', // สีเทาอ่อนจำลองกล้อง
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
    backgroundColor: 'rgba(255,255,255,0.8)', // เพิ่มพื้นหลังให้ปุ่มมองเห็นชัด
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
    borderColor: '#2563EB', // สีขอบสีฟ้าเมื่อเลือก
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
