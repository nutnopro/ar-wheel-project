import React, { useState } from 'react';
import { Alert, View, Text } from 'react-native';
import ManageLayout from '../../components/layouts/ManageLayout';
import UniversalCard from '../../components/UniversalCard';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

// 1. กำหนด Type ของข้อมูลในหน้านี้
interface Model {
  id: number;
  name: string;
  brand: string;
  price: number;
  img?: string;
}

export default function ManageModelsScreen({ navigation }: any) {
  const [searchText, setSearchText] = useState('');

  // Mock Data
  const models: Model[] = [
    { id: 1, name: 'TE37 Racing', brand: 'Rays', price: 8500 },
    { id: 2, name: 'CE28 Forged', brand: 'Volk', price: 12000 },
  ];

  // Logic ลบ
  const handleDelete = (id: number) => {
    Alert.alert('Delete', 'Are you sure?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => console.log('Deleted', id),
      },
    ]);
  };

  return (
    <ManageLayout<Model>
      title="Manage Models"
      onBackPress={() => navigation.goBack()}
      data={models} // ส่งข้อมูลเข้าไป
      searchText={searchText}
      onSearchChange={setSearchText}
      addButtonTitle="Add New Model"
      onAddPress={() => navigation.navigate('AddEditModel')}
      // ตัวกรอง (Filter Content)
      filterContent={
        <View>
          <Text style={{ fontWeight: 'bold', marginBottom: 8 }}>Brands</Text>
          <Text>☐ Rays</Text>
          <Text>☐ Enkei</Text>
        </View>
      }
      onFilterApply={() => {}}
      // *** หัวใจสำคัญ: แปลงข้อมูล Model เป็น Card ***
      renderItem={({ item }) => (
        <UniversalCard
          variant="list"
          title={item.name}
          subtitle={item.brand}
          price={item.price} // มีราคา
          imageUrl={item.img}
          onPress={() => navigation.navigate('AddEditModel', { model: item })} // กดการ์ดเพื่อ Edit
          // ปุ่ม Action ด้านหลัง (Edit & Delete)
          renderAction={() => (
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Ionicons
                name="pencil"
                size={20}
                color={COLORS.primary}
                onPress={() =>
                  navigation.navigate('AddEditModel', { model: item })
                }
              />
              <Ionicons
                name="trash"
                size={20}
                color={COLORS.error}
                onPress={() => handleDelete(item.id)}
              />
            </View>
          )}
        />
      )}
    />
  );
}
