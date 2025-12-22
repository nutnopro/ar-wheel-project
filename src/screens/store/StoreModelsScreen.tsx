import React, { useState } from 'react';
import { View, Alert } from 'react-native';
import ManageLayout from '../../components/layouts/ManageLayout';
import UniversalCard from '../../components/UniversalCard';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

// ... interface Model ...

export default function StoreModelsScreen({ navigation }: any) {
  const [searchText, setSearchText] = useState('');

  // Mock Data (เฉพาะของร้านนี้)
  const myModels = [
    { id: 1, name: 'TE37 Racing', price: 8500, status: 'Active' },
    { id: 2, name: 'CE28 Forged', price: 12000, status: 'Pending' },
  ];

  return (
    <ManageLayout
      title="สต็อกสินค้าของฉัน" // ชื่อหัวข้อต่างกัน
      onBackPress={() => navigation.goBack()}
      data={myModels}
      searchText={searchText}
      onSearchChange={setSearchText}
      // ร้านค้าต้องมีปุ่ม Add
      addButtonTitle="ลงขายสินค้าใหม่"
      onAddPress={() => navigation.navigate('AddEditModel')}
      renderItem={({ item }: any) => (
        <UniversalCard
          variant="list"
          title={item.name}
          subtitle={`Status: ${item.status}`}
          price={item.price}
          // Action: แก้ไข หรือ ลบ
          renderAction={() => (
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Ionicons name="pencil" size={20} color={COLORS.primary} />
              <Ionicons name="trash" size={20} color={COLORS.error} />
            </View>
          )}
          onPress={function (): void {
            throw new Error('Function not implemented.');
          }}
        />
      )}
    />
  );
}
