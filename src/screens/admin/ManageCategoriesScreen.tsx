import React from 'react';
import ManageLayout from '../../components/layouts/ManageLayout';
import UniversalCard from '../../components/UniversalCard';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

interface Category {
  id: number;
  name: string;
  count: number;
}

export default function ManageCategoriesScreen({ navigation }: any) {
  const categories: Category[] = [
    { id: 1, name: 'Forged Wheels', count: 120 },
    { id: 2, name: 'Cast Wheels', count: 300 },
  ];

  return (
    <ManageLayout<Category>
      title="Manage Categories"
      onBackPress={() => navigation.goBack()}
      data={categories}
      addButtonTitle="Add Category"
      onAddPress={() => {}}
      renderItem={({ item }) => (
        <UniversalCard
          variant="list"
          title={item.name}
          subtitle={`${item.count} items`} // Custom ข้อความ
          // ไม่ส่ง price, ไม่ส่ง imageUrl
          onPress={() => {}}
          // ใช้ปุ่ม Icon ธรรมดา
          actionIcon="chevron-forward"
        />
      )}
    />
  );
}
