import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useForm, Controller } from 'react-hook-form';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '../../navigation/AppNavigator';
import DateTimePicker from '@react-native-community/datetimepicker';

import CustomInput from '../../components/CustomInput';
import { COLORS } from '../../constants/colors';

// ✅ 1. Import Service เพื่อใช้ยิง API
import { authService } from '../../services/authService';

type RegisterScreenProp = NativeStackNavigationProp<
  RootStackParamList,
  'Register'
>;

const RegisterScreen = () => {
  const navigation = useNavigation<RegisterScreenProp>();
  const [showPassword, setShowPassword] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState(false);

  // เพิ่ม loading state เผื่อตอนเน็ตช้าหรือ Server ประมวลผล
  const [isLoading, setIsLoading] = useState(false);

  const { control, handleSubmit } = useForm();

  // ✅ 2. ฟังก์ชันสมัครสมาชิกที่เชื่อมต่อ Backend แล้ว
  const onRegisterPressed = async (data: any) => {
    if (isLoading) return; // ป้องกันการกดรัวๆ

    setIsLoading(true);
    try {
      console.log('📝 Raw Form Data:', data);

      // 🛠️ แปลงข้อมูล (Mapping) ให้ตรงกับที่ Backend ต้องการ
      // Form เราใช้ชื่อ 'dob' แต่ Backend น่าจะรอรับ 'dateOfBirth'
      const newUser = {
        displayName: data.displayName,
        password: data.password,
        email: data.email,
        phoneNumber: data.phoneNumber,
        dateOfBirth: data.dob.toISOString().split('T')[0], // format to YYYY-MM-DD
      };

      console.log('🚀 Sending Registration to Backend:', newUser);

      // ยิง API ไปที่ NestJS
      await authService.register(newUser);

      // ถ้าไม่มี Error เด้ง แสดงว่าสมัครสำเร็จ
      Alert.alert('Success', 'Account created successfully!', [
        { text: 'OK', onPress: () => navigation.navigate('SignIn') },
      ]);
    } catch (error: any) {
      console.error('Register Error:', error);

      // ดึงข้อความ Error จาก Backend มาแสดง (ถ้ามี)
      const errorMessage =
        error.response?.data?.message ||
        'Registration failed. Please try again.';
      Alert.alert('Registration Failed', errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const onSignInPressed = () => {
    navigation.navigate('SignIn');
  };

  return (
    <ScrollView
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.scrollContainer}
    >
      <View style={styles.container}>
        {/* --- ปุ่ม Back --- */}
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Icon name="chevron-left" size={40} color={COLORS.primary} />
        </TouchableOpacity>

        {/* โลโก้ล้อรถให้ตรงกับ Splash/Login */}
        <View style={styles.logoWrapper}>
          <View style={styles.logoCircle}>
            <Icon name="steering" size={40} color={COLORS.primary} />
          </View>
        </View>

        <Text style={styles.title}>New Account</Text>

        <CustomInput
          name="displayName"
          label="Display Name"
          placeholder="Display Name"
          control={control}
          rules={{ required: 'Display Name is required' }}
        />

        <CustomInput
          name="password"
          label="Password"
          placeholder="Password"
          control={control}
          secureTextEntry={!showPassword}
          rightIcon={showPassword ? 'eye-off' : 'eye'}
          onRightIconPress={() => setShowPassword(!showPassword)}
          rules={{
            required: 'Password is required',
            minLength: {
              value: 6,
              message: 'Password must be at least 6 characters',
            },
          }}
        />

        <CustomInput
          name="email"
          label="Email"
          placeholder="Email"
          control={control}
          rules={{
            required: 'Email is required',
            pattern: { value: /\S+@\S+\.\S+/, message: 'Invalid email format' },
          }}
        />

        <CustomInput
          name="phoneNumber"
          label="Phone Number"
          placeholder="Phone Number"
          control={control}
          rules={{ required: 'Phone Number is required' }}
        />

        <Controller
          name="dob"
          control={control}
          rules={{ required: 'Date of Birth is required' }}
          render={({ field: { onChange, value }, fieldState: { error } }) => (
            <View style={styles.datePickerContainer}>
              <Text style={styles.dateLabel}>Date Of Birth</Text>
              <TouchableOpacity
                style={[styles.dateButton, error && styles.dateButtonError]}
                onPress={() => setShowDatePicker(true)}
              >
                <Text style={[styles.dateText, !value && { color: '#999' }]}>
                  {value ? value.toLocaleDateString() : 'Select Date of Birth'}
                </Text>
                <Icon name="calendar" size={20} color={COLORS.primary} />
              </TouchableOpacity>
              
              {showDatePicker && (
                <DateTimePicker
                  value={value || new Date()}
                  mode="date"
                  display="default"
                  maximumDate={new Date()}
                  onChange={(event: any, selectedDate?: Date) => {
                    setShowDatePicker(false);
                    if (selectedDate) onChange(selectedDate);
                  }}
                />
              )}
              {error && <Text style={styles.errorText}>{error.message}</Text>}
            </View>
          )}
        />

        {/* ปุ่ม Sign Up */}
        <TouchableOpacity
          style={[styles.button, isLoading && { opacity: 0.7 }]}
          onPress={handleSubmit(onRegisterPressed)}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>
            {isLoading ? 'Signing up...' : 'Sign Up'}
          </Text>
        </TouchableOpacity>

        <View style={styles.footer}>
          <Text style={styles.footerText}>Already have an account? </Text>
          <TouchableOpacity onPress={onSignInPressed}>
            <Text style={styles.linkText}>Sign in</Text>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollContainer: { flexGrow: 1, backgroundColor: COLORS.white },
  container: { flex: 1, padding: 24, paddingTop: 20 },
  logoWrapper: {
    alignItems: 'center',
    marginBottom: 10,
  },
  logoCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#EFF6FF',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  backButton: {
    alignSelf: 'flex-start',
    marginLeft: -10,
    marginBottom: 10,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: COLORS.primary,
    textAlign: 'center',
    marginBottom: 30,
  },
  button: {
    backgroundColor: COLORS.primary,
    padding: 15,
    borderRadius: 30,
    alignItems: 'center',
    marginTop: 10,
    marginBottom: 20,
  },
  buttonText: { color: COLORS.white, fontWeight: 'bold', fontSize: 16 },
  footer: { flexDirection: 'row', justifyContent: 'center' },
  footerText: { color: '#888', fontSize: 12 },
  linkText: { color: COLORS.primary, fontWeight: 'bold', fontSize: 12 },
  datePickerContainer: {
    marginBottom: 15,
  },
  dateLabel: {
    fontSize: 14,
    color: '#333',
    fontWeight: '600',
    marginBottom: 6,
  },
  dateButton: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    paddingHorizontal: 15,
    paddingVertical: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#F5F5F5',
  },
  dateButtonError: {
    borderColor: 'red',
  },
  dateText: {
    fontSize: 14,
    color: '#333',
  },
  errorText: {
    color: 'red',
    fontSize: 12,
    marginTop: 4,
    marginLeft: 4,
  },
});

export default RegisterScreen;
