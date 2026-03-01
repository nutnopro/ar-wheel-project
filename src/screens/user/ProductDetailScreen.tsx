import React from 'react';
import { View, Text, Image, StyleSheet, TouchableOpacity, ScrollView, SafeAreaView, StatusBar, Platform } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation, useRoute } from '@react-navigation/native';

const ProductDetailScreen = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const { item } = route.params; // รับข้อมูลสินค้า

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar
        barStyle="dark-content"
        translucent
        backgroundColor="transparent"
      />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* ส่วนแสดงรูปโมเดลล้อ + ปุ่ม Back ซ้ายบนซ้อนทับเหมือนดีไซน์ */}
        <View style={styles.imageWrapper}>
          <Image source={{ uri: item.image }} style={styles.image} resizeMode="contain" />

          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backBtnOverlay}
            activeOpacity={0.8}
          >
            <Icon name="chevron-left" size={28} color="#2563EB" />
          </TouchableOpacity>
        </View>

        <View style={styles.detailsContainer}>
          <Text style={styles.name}>{item.name}</Text>
          <Text style={styles.price}>${item.price.toLocaleString()}</Text>
          
          <Text style={styles.description}>
            The legendary {item.name}. High performance forged wheel designed for racing and street use. 
            Lightweight, durable, and stylish.
          </Text>
        </View>
      </ScrollView>

      {/* --- Footer Buttons --- */}
      <View style={styles.footer}>
        <TouchableOpacity 
          style={styles.arButton} 
          activeOpacity={0.8}
          onPress={() => {
            navigation.navigate('MainApp', { 
              screen: 'AR', 
              params: { item: item } 
            });
          }}
        >
          <Icon name="cube-scan" size={24} color="#fff" style={{ marginRight: 8 }} />
          <Text style={styles.arButtonText}>Try on AR</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.favButton}>
          <Icon name="heart-outline" size={28} color="#2563EB" />
        </TouchableOpacity>
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
  price: { fontSize: 20, fontWeight: '600', color: '#2563EB', marginBottom: 16 },
  description: { fontSize: 14, color: '#64748B', lineHeight: 22 },
  
  footer: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    flexDirection: 'row', padding: 20, backgroundColor: '#fff',
    borderTopWidth: 1, borderTopColor: '#E2E8F0', alignItems: 'center'
  },
  arButton: {
    flex: 1, backgroundColor: '#2563EB', flexDirection: 'row',
    height: 56, borderRadius: 28, justifyContent: 'center', alignItems: 'center',
    marginRight: 16, elevation: 4
  },
  arButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  favButton: {
    width: 56, height: 56, borderRadius: 28, borderWidth: 1,
    borderColor: '#2563EB', justifyContent: 'center', alignItems: 'center'
  }
});

export default ProductDetailScreen;