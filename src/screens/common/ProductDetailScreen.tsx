// src/screens/user/ProductDetailScreen.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  Image,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  NativeModules,
  Alert,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

const { ARLauncher } = NativeModules;

export default function ProductDetailScreen({ route, navigation }: any) {
  const { product } = route.params;
  const [isFav, setIsFav] = useState(false);

  const openAR = () => {
    if (ARLauncher && ARLauncher.openARActivity) {
      ARLauncher.openARActivity();
    } else {
      Alert.alert('Unavailable', 'AR Module not found');
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#fff' }}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color={COLORS.text} />
        </TouchableOpacity>
      </View>

      <ScrollView>
        <Image
          source={{ uri: 'https://via.placeholder.com/600' }}
          style={styles.image}
        />
        <View style={styles.info}>
          <Text style={styles.name}>{product.name}</Text>
          <Text style={styles.brand}>{product.brand}</Text>
          <Text style={styles.price}>฿{product.price.toLocaleString()}</Text>
          <Text style={styles.desc}>Description of the wheel...</Text>
        </View>
      </ScrollView>

      {/* Buttons */}
      <View style={styles.footer}>
        <TouchableOpacity
          style={[
            styles.btn,
            { backgroundColor: isFav ? COLORS.error : '#eee' },
          ]}
          onPress={() => setIsFav(!isFav)}
        >
          <Ionicons
            name={isFav ? 'heart' : 'heart-outline'}
            size={24}
            color={isFav ? '#fff' : COLORS.text}
          />
          <Text
            style={[styles.btnText, { color: isFav ? '#fff' : COLORS.text }]}
          >
            Favorite
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.btn, { backgroundColor: COLORS.primary, flex: 2 }]}
          onPress={openAR}
        >
          <Ionicons name="cube-outline" size={24} color="#fff" />
          <Text style={[styles.btnText, { color: '#fff' }]}>View in AR</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    position: 'absolute',
    top: 40,
    left: 20,
    zIndex: 10,
    backgroundColor: '#fff',
    padding: 8,
    borderRadius: 20,
  },
  image: { width: '100%', height: 350, backgroundColor: '#f0f0f0' },
  info: { padding: 20 },
  name: { fontSize: 24, fontWeight: 'bold' },
  brand: { color: COLORS.textDim, marginBottom: 8 },
  price: {
    fontSize: 20,
    color: COLORS.primary,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  desc: { color: COLORS.text, lineHeight: 22 },
  footer: {
    padding: 16,
    borderTopWidth: 1,
    borderColor: '#eee',
    flexDirection: 'row',
    gap: 12,
  },
  btn: {
    flex: 1,
    flexDirection: 'row',
    height: 50,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
  },
  btnText: { fontWeight: '600', fontSize: 16 },
});
