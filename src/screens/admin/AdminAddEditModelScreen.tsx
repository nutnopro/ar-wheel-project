import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  SafeAreaView,
  ScrollView,
  Alert,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import AddEditModelForm from './../../components/forms/AddEditModel';
import { COLORS } from '../../theme/colors';

export default function AdminAddEditModelScreen({ navigation, route }: any) {
  const { model } = route.params || {};

  // State (Admin มี isBanned เพิ่มมา)
  const [name, setName] = useState(model?.name || '');
  const [brand, setBrand] = useState(model?.brand || '');
  const [price, setPrice] = useState(model?.price?.toString() || '');
  const [isBanned, setIsBanned] = useState(model?.status === 'Banned');

  const handleAdminSave = () => {
    console.log('Admin Updating...', { name, price, isBanned });
    // TODO: Call API Admin update
    navigation.goBack();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#fff' }}>
      {/* Header Admin (อาจจะใช้สีแดงให้รู้ว่าเป็น Admin) */}
      <View style={[styles.header, { backgroundColor: '#FFF5F5' }]}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={28} color={COLORS.error} />
        </TouchableOpacity>
        <Text style={[styles.title, { color: COLORS.error }]}>Admin Edit</Text>
        <TouchableOpacity onPress={handleAdminSave}>
          <Text style={[styles.saveBtn, { color: COLORS.error }]}>Update</Text>
        </TouchableOpacity>
      </View>

      <ScrollView>
        <AddEditModelForm
          name={name}
          onNameChange={setName}
          brand={brand}
          onBrandChange={setBrand}
          price={price}
          onPriceChange={setPrice}
          // เปิดโหมด Admin
          isAdmin={true}
          isBanned={isBanned}
          onBannedChange={setIsBanned}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = {
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f1f1',
  },
  title: { fontSize: 18, fontWeight: 'bold' },
  saveBtn: { fontSize: 16, fontWeight: 'bold' },
};
