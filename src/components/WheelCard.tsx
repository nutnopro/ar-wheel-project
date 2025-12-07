// src/components/WheelCard.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import Ionicons from '@react-native-vector-icons/Ionicons';

type Wheel = {
  id: number;
  name: string;
  brand?: string;
  price?: number | string;
  size?: number;
  material?: string;
  pattern?: string;
  color?: string;
  weight?: string;
};

type Props = {
  wheel: Wheel;
  onPress?: () => void;
  onFavorite?: (id: number) => void;
  isFavorite?: boolean;
  showPrice?: boolean;
  compact?: boolean;
};

export default function WheelCard({
  wheel,
  onPress,
  onFavorite,
  isFavorite,
  showPrice = true,
  compact = false,
}: Props) {
  return (
    <TouchableOpacity
      style={[styles.card, compact && styles.compactCard]}
      onPress={onPress}
    >
      <View style={styles.imageContainer}>
        <View
          style={[
            styles.wheelImage,
            { backgroundColor: wheel.color || '#cbd5e1' },
          ]}
        >
          <View style={styles.wheelRim} />
          {wheel.pattern && (
            <Text style={styles.patternText}>{wheel.pattern}</Text>
          )}
        </View>

        {onFavorite && (
          <TouchableOpacity
            style={styles.favoriteButton}
            onPress={() => onFavorite(wheel.id)}
          >
            <Ionicons
              name={isFavorite ? 'heart' : 'heart-outline'}
              size={16}
              color={isFavorite ? '#ff4444' : '#999'}
            />
          </TouchableOpacity>
        )}
      </View>

      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={1}>
          {wheel.name}
        </Text>
        {wheel.brand && (
          <Text style={styles.brand} numberOfLines={1}>
            {wheel.brand}
          </Text>
        )}
        {showPrice && <Text style={styles.price}>฿{wheel.price}</Text>}
        {!compact && (
          <View style={styles.specs}>
            {wheel.material && (
              <Text style={styles.spec}>{wheel.material}</Text>
            )}
            {wheel.weight && <Text style={styles.spec}>{wheel.weight}</Text>}
          </View>
        )}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: 'white',
    borderRadius: 15,
    padding: 15,
    marginBottom: 15,
    elevation: 3,
  },
  compactCard: { padding: 10, marginBottom: 10 },
  imageContainer: {
    position: 'relative',
    alignItems: 'center',
    marginBottom: 10,
  },
  wheelImage: {
    width: 80,
    height: 80,
    borderRadius: 40,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#ddd',
  },
  wheelRim: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#333',
    position: 'absolute',
  },
  patternText: {
    position: 'absolute',
    bottom: -15,
    fontSize: 8,
    color: '#666',
    textAlign: 'center',
  },
  favoriteButton: {
    position: 'absolute',
    top: -5,
    right: -5,
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 4,
    elevation: 2,
  },
  info: { alignItems: 'center' },
  name: { fontSize: 14, fontWeight: 'bold', color: '#333' },
  brand: { fontSize: 12, color: '#666' },
  price: { fontSize: 16, fontWeight: 'bold', color: '#667eea' },
  specs: { flexDirection: 'row', justifyContent: 'center', flexWrap: 'wrap' },
  spec: {
    fontSize: 10,
    color: '#999',
    backgroundColor: '#f0f0f0',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 8,
    marginHorizontal: 2,
    marginVertical: 1,
  },
});
