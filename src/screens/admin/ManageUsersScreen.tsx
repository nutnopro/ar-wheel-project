import React, { useState } from 'react';
import { View, Text, Alert } from 'react-native';
import ManageLayout from '../../components/layouts/ManageLayout';
import UniversalCard from '../../components/UniversalCard';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

interface User {
  id: number;
  username: string;
  email: string;
  role: 'Admin' | 'User' | 'Store';
}

export default function ManageUsersScreen({ navigation }: any) {
  const [searchText, setSearchText] = useState('');

  const users: User[] = [
    { id: 1, username: 'AdminJohn', email: 'john@admin.com', role: 'Admin' },
    { id: 2, username: 'User001', email: 'user@test.com', role: 'User' },
  ];

  return (
    <ManageLayout<User>
      title="Manage Users"
      onBackPress={() => navigation.goBack()}
      data={users}
      searchText={searchText}
      onSearchChange={setSearchText}
      addButtonTitle="Add User"
      onAddPress={() => navigation.navigate('AddEditUser')} // สร้างหน้า AddEditUser เพิ่มทีหลัง
      renderItem={({ item }) => (
        <UniversalCard
          variant="list"
          title={item.username}
          subtitle={item.email}
          price={item.role} // เอา Role มาโชว์ตรงตำแหน่ง Price
          onPress={() => {}}
          renderAction={() => (
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Ionicons name="pencil" size={20} color={COLORS.primary} />
              <Ionicons name="ban" size={20} color={COLORS.error} />
            </View>
          )}
        />
      )}
    />
  );
}
