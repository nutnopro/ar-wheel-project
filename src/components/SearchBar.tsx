// src/components/SearchBar.tsx
import React from 'react';
import { View, TextInput, StyleSheet } from 'react-native';
import Ionicons from '@react-native-vector-icons/Ionicons';
import { useTheme } from '../contexts/ThemeContext';

type Props = {
  value?: string;
  onChangeText?: (t: string) => void;
  onSubmitEditing?: () => void;
};

export default function SearchBar({
  value,
  onChangeText,
  onSubmitEditing,
}: Props) {
  const { colors } = useTheme();
  return (
    <View
      style={[
        styles.wrap,
        { backgroundColor: colors.card, borderColor: colors.border },
      ]}
    >
      <Ionicons name="search" size={18} color={colors.textDim} />
      <TextInput
        style={[styles.input, { color: colors.text }]}
        value={value}
        onChangeText={onChangeText}
        placeholder="ค้นหา โมเดลหรือแบรนด์..."
        placeholderTextColor={colors.textDim}
        returnKeyType="search"
        onSubmitEditing={onSubmitEditing}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    height: 44,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    alignItems: 'center',
    flexDirection: 'row',
  },
  input: { flex: 1, paddingHorizontal: 8 },
});
