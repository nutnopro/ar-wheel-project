// src/screens/ModelDetailScreen.tsx
import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Image,
} from 'react-native';
import { useTheme } from '../contexts/ThemeContext';

export default function ModelDetailScreen({ route }: any) {
  const { colors } = useTheme();
  const model = route?.params?.model || {
    name: 'Model',
    price: 0,
    brand: 'Brand',
    img: '',
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.bg }]}>
      <Image
        source={{ uri: model.img || 'https://via.placeholder.com/600x360' }}
        style={{ width: '100%', height: 240 }}
      />
      <View style={{ padding: 16 }}>
        <Text style={[styles.title, { color: colors.text }]}>{model.name}</Text>
        <Text style={{ color: colors.textDim, marginTop: 6 }}>
          {model.brand}
        </Text>
        <Text style={[styles.price, { color: colors.primary }]}>
          ฿{model.price}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  title: { fontSize: 22, fontWeight: '900' },
  price: { fontSize: 20, fontWeight: '900', marginTop: 12 },
});
