import React from 'react';
import { View, Text } from 'react-native';
import ManageLayout from '../../components/layouts/ManageLayout';
import UniversalCard from '../../components/UniversalCard';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

interface Store {
  id: number;
  name: string;
  location: string;
  status: 'Active' | 'Pending';
}

export default function ManageStoresScreen({ navigation }: any) {
  const stores: Store[] = [
    { id: 1, name: 'Wheel Master', location: 'Bangkok', status: 'Active' },
    { id: 2, name: 'Racing Zone', location: 'Chonburi', status: 'Pending' },
  ];

  return (
    <ManageLayout<Store>
      title="Manage Stores"
      onBackPress={() => navigation.goBack()}
      data={stores}
      addButtonTitle="Approve Store" // เปลี่ยนชื่อปุ่มได้ตามบริบท
      onAddPress={() => {}}
      renderItem={({ item }) => (
        <UniversalCard
          variant="list"
          title={item.name}
          subtitle={item.location}
          // Custom Price เป็น Status แทน
          price={item.status}
          onPress={() => {}}
          renderAction={() => (
            <Ionicons
              name="storefront-outline"
              size={20}
              color={COLORS.secondary}
            />
          )}
        />
      )}
    />
  );
}
