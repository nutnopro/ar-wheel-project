// src/screens/admin/SystemManagementScreen.tsx
import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

const MenuCard = ({ title, onPress }: any) => (
  <TouchableOpacity style={styles.card} onPress={onPress}>
    <Text style={styles.cardText}>{title}</Text>
    <Ionicons name="chevron-forward" size={24} color={COLORS.textDim} />
  </TouchableOpacity>
);

export default function SystemManagementScreen({ navigation }: any) {
  // Reuse ManageListScreen for all these
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: COLORS.background }}>
      <View style={{ padding: 16, flexDirection: 'row', alignItems: 'center' }}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} />
        </TouchableOpacity>
        <Text style={{ fontSize: 20, fontWeight: 'bold', marginLeft: 16 }}>
          System Management
        </Text>
      </View>

      <View style={{ padding: 16 }}>
        <MenuCard
          title="Manage Users"
          onPress={() => navigation.navigate('ManageList', { type: 'Users' })}
        />
        <MenuCard
          title="Manage Stores"
          onPress={() => navigation.navigate('ManageList', { type: 'Stores' })}
        />
        <MenuCard
          title="Manage Models"
          onPress={() => navigation.navigate('ManageList', { type: 'Models' })}
        />
        <MenuCard
          title="Manage Categories"
          onPress={() =>
            navigation.navigate('ManageList', { type: 'Categories' })
          }
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 12,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardText: { fontSize: 16, fontWeight: '600' },
});
