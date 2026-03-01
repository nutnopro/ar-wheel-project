import React from 'react';
import { View, Text, TextInput, StyleSheet, TouchableOpacity } from 'react-native';
import { Controller } from 'react-hook-form';
import { COLORS } from '../constants/colors';
import Icon from 'react-native-vector-icons/Ionicons'; // ตรวจสอบว่าลง library นี้แล้ว

interface CustomInputProps {
  control: any;
  name: string;
  placeholder: string;
  label?: string;
  secureTextEntry?: boolean;
  rules?: object;
  rightIcon?: string;
  onRightIconPress?: () => void;
}

const CustomInput = ({
  control,
  name,
  placeholder,
  label,
  secureTextEntry,
  rules = {},
  rightIcon,
  onRightIconPress,
}: CustomInputProps) => {
  return (
    <Controller
      control={control}
      name={name}
      rules={rules}
      render={({ field: { value, onChange, onBlur }, fieldState: { error } }) => (
        <View style={styles.container}>
          {label && <Text style={styles.label}>{label}</Text>}
          <View style={[styles.inputContainer, error && styles.inputError]}>
            <TextInput
              value={value}
              onChangeText={onChange}
              onBlur={onBlur}
              placeholder={placeholder}
              placeholderTextColor="#BDBDBD"
              style={styles.input}
              secureTextEntry={secureTextEntry}
            />
            {rightIcon && (
              <TouchableOpacity onPress={onRightIconPress} style={styles.icon}>
                <Icon name={rightIcon} size={20} color={COLORS.textSecondary} />
              </TouchableOpacity>
            )}
          </View>
          {error && <Text style={styles.errorText}>{error.message || 'Error'}</Text>}
        </View>
      )}
    />
  );
};

const styles = StyleSheet.create({
  container: { marginBottom: 15, width: '100%' },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: COLORS.text,
    marginBottom: 8,
  },
  inputContainer: {
    backgroundColor: COLORS.inputBackground,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 15,
    height: 50,
  },
  input: { flex: 1, color: COLORS.text },
  inputError: { borderWidth: 1, borderColor: COLORS.error },
  errorText: { color: COLORS.error, fontSize: 12, marginTop: 4 },
  icon: { marginLeft: 10 },
});

export default CustomInput;