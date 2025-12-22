// src/components/CustomButton.tsx
import React from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { COLORS } from '../theme/colors';

interface Props {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'outline' | 'danger';
  loading?: boolean;
}

export default function CustomButton({
  title,
  onPress,
  variant = 'primary',
  loading,
}: Props) {
  const bg =
    variant === 'primary'
      ? COLORS.primary
      : variant === 'danger'
      ? COLORS.error
      : 'transparent';
  const text = variant === 'outline' ? COLORS.primary : '#fff';
  const border = variant === 'outline' ? COLORS.primary : 'transparent';

  return (
    <TouchableOpacity
      style={[
        styles.btn,
        {
          backgroundColor: bg,
          borderColor: border,
          borderWidth: variant === 'outline' ? 1 : 0,
        },
      ]}
      onPress={onPress}
      disabled={loading}
    >
      {loading ? (
        <ActivityIndicator color={text} />
      ) : (
        <Text style={[styles.txt, { color: text }]}>{title}</Text>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  btn: {
    height: 50,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
    elevation: 2,
  },
  txt: { fontSize: 16, fontWeight: '600' },
});
