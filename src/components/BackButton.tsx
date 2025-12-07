// src/components/BackButton.tsx
import React from 'react';
import { TouchableOpacity } from 'react-native';
import Ionicons from '@react-native-vector-icons/Ionicons';
import { useNavigation } from '@react-navigation/native';

export default function BackButton({ tint }: { tint?: string }) {
  const navigation = useNavigation();
  if (!navigation.canGoBack()) return null;

  return (
    <TouchableOpacity
      onPress={() => navigation.goBack()}
      style={{ paddingHorizontal: 12 }}
      hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
    >
      <Ionicons name="chevron-back" size={24} color={tint || '#fff'} />
    </TouchableOpacity>
  );
}
