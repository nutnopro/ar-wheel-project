// src/screens/HomeScreen.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  SafeAreaView,
  StyleSheet,
  TouchableOpacity,
  Image,
} from 'react-native';
import { useTheme } from '../contexts/ThemeContext';
import SearchBar from '../components/SearchBar';
import WheelCard from '../components/WheelCard';
import { useNavigation } from '@react-navigation/native';

const MODELS = [
  {
    id: 1,
    name: 'Sport Classic',
    brand: 'Racing Pro',
    price: 8900,
    size: 15,
    material: 'Alloy',
    color: '#f3f4f6',
  },
  {
    id: 2,
    name: 'Urban Style',
    brand: 'City Drive',
    price: 7500,
    size: 15,
    material: 'Steel',
    color: '#e5e7eb',
  },
  {
    id: 3,
    name: 'Performance Pro',
    brand: 'Racing Elite',
    price: 10500,
    size: 16,
    material: 'Alloy',
    color: '#cbd5e1',
  },
];

export default function HomeScreen() {
  const { colors } = useTheme();
  const navigation = useNavigation();
  const [q, setQ] = useState('');

  const filtered = MODELS.filter(m =>
    m.name.toLowerCase().includes(q.toLowerCase()),
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={{ padding: 16 }}>
        <Text
          style={[styles.header, { color: colors.text, fontWeight: '900' }]}
        >
          ARWheel
        </Text>
        <SearchBar value={q} onChangeText={setQ} onSubmitEditing={() => {}} />
      </View>

      <FlatList
        data={filtered}
        keyExtractor={i => String(i.id)}
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 120 }}
        renderItem={({ item }) => (
          <WheelCard
            wheel={item as any}
            onPress={() => navigation.navigate('ModelDetail', { model: item })}
          />
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { fontSize: 28, marginBottom: 12 },
});
