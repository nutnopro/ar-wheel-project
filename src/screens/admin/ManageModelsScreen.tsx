import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { productService } from '../../services/productService'; // เรียก Service
import { useFocusEffect } from '@react-navigation/native'; // เพื่อให้โหลดใหม่ทุกครั้งที่เข้าหน้า

import Header from '../../components/Header';

const ManageModelsScreen = () => {
  const { theme } = useTheme();
  const [models, setModels] = useState<any[]>([]); // เก็บข้อมูลจริง
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // ฟังก์ชันดึงข้อมูลจาก Backend
  const fetchModels = async () => {
    try {
      const response = await productService.getAll();
      setModels(response.data); // เอาข้อมูลใส่ State
    } catch (error) {
      console.error(error);
      Alert.alert('Error', 'ไม่สามารถโหลดข้อมูลได้');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  // โหลดข้อมูลเมื่อเข้าหน้านี้
  useFocusEffect(
    useCallback(() => {
      fetchModels();
    }, []),
  );

  // ฟังก์ชัน Refresh (ดึงหน้าจอลงเพื่อโหลดใหม่)
  const onRefresh = () => {
    setRefreshing(true);
    fetchModels();
  };

  // ฟังก์ชันลบข้อมูล
  const handleDelete = (id: string) => {
    Alert.alert(
      'Confirm Delete',
      'Are you sure you want to delete this model?',
      [
        { text: 'Cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              // 1. ยิง API ลบ
              await productService.delete(id);
              // 2. อัปเดตหน้าจอโดยกรองเอาตัวที่ลบออก
              setModels(prev => prev.filter(m => m.id !== id)); // หรือ m._id แล้วแต่ database
              Alert.alert('Success', 'Model deleted successfully');
            } catch (error) {
              Alert.alert('Error', 'Failed to delete model');
            }
          },
        },
      ],
    );
  };

  if (loading) {
    return (
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          alignItems: 'center',
          backgroundColor: theme.background,
        }}
      >
        <ActivityIndicator size="large" color="#F59E0B" />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="Manage Models" />
      <FlatList
        data={models}
        // ⚠️ เช็คว่า Backend ส่ง id หรือ _id มา
        keyExtractor={item => item.id || item._id}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={['#F59E0B']}
          />
        }
        contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
        ListEmptyComponent={
          <Text
            style={{ color: theme.subText, textAlign: 'center', marginTop: 50 }}
          >
            No models found.
          </Text>
        }
        renderItem={({ item }) => {
          // Mapping ข้อมูล: ตรวจสอบชื่อ field กับ Backend ว่าตรงกันไหม (เช่น name หรือ title, price)
          return (
            <View style={[styles.card, { backgroundColor: theme.card }]}>
              <View style={[styles.iconBox, { backgroundColor: '#FFFBEB' }]}>
                <Icon name="car-wheel" size={24} color="#F59E0B" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.title, { color: theme.text }]}>
                  {item.name || item.modelName}
                </Text>
                <Text style={{ color: theme.subText }}>
                  {item.price ? `$${item.price}` : 'No Price'}
                </Text>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => Alert.alert('Edit', `Edit ID: ${item.id}`)}
                  style={styles.actionBtn}
                >
                  <Icon name="pencil-outline" size={24} color="#3B82F6" />
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleDelete(item.id || item._id)}
                  style={styles.actionBtn}
                >
                  <Icon name="trash-can-outline" size={24} color="#EF4444" />
                </TouchableOpacity>
              </View>
            </View>
          );
        }}
      />
      <View
        style={[
          styles.footer,
          { backgroundColor: theme.card, borderTopColor: theme.border },
        ]}
      >
        <TouchableOpacity
          style={[styles.addButton, { backgroundColor: '#F59E0B' }]}
          onPress={() => Alert.alert('Add', 'Feature coming soon')}
        >
          <Icon name="plus" size={24} color="#fff" />
          <Text style={styles.addText}>Add Model</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    marginBottom: 12,
    borderRadius: 12,
    elevation: 2,
  },
  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  title: { fontSize: 16, fontWeight: '600' },
  actions: { flexDirection: 'row' },
  actionBtn: { padding: 8 },
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 20,
    paddingBottom: 40,
    borderTopWidth: 1,
  },
  addButton: {
    backgroundColor: '#2563EB',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
  },
  addText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 8 },
});

export default ManageModelsScreen;
