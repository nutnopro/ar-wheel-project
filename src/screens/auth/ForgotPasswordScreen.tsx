import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useForm } from 'react-hook-form';
import CustomInput from '../../components/CustomInput';
import { COLORS } from '../../constants/colors';
import Icon from 'react-native-vector-icons/Ionicons';

const ForgotPasswordScreen = () => {
  const navigation = useNavigation();
  const [step, setStep] = useState(1); // 1 = Email, 2 = New Password
  const { control, handleSubmit, watch } = useForm();
  
  const pwd = watch('newPassword');

  const onSubmit = (data: any) => {
    if (step === 1) {
      console.log('Send Email to:', data.email);
      // TODO: Call API to send reset email
      setStep(2);
    } else {
      console.log('Reset Password Data:', data);
      // TODO: Call API to reset password
      navigation.goBack();
    }
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
        <Icon name="chevron-back" size={30} color={COLORS.primary} />
      </TouchableOpacity>

      <Text style={styles.title}>Forget Password</Text>

      {step === 1 ? (
        <>
          <Text style={styles.subtitle}>Enter email address</Text>
          <CustomInput
            name="email"
            placeholder="Email"
            control={control}
            rules={{ required: 'Email is required', pattern: { value: /\S+@\S+\.\S+/, message: 'Invalid email' } }}
          />
          <TouchableOpacity style={styles.button} onPress={handleSubmit(onSubmit)}>
            <Text style={styles.buttonText}>Send</Text>
          </TouchableOpacity>
        </>
      ) : (
        <>
          <CustomInput
            name="newPassword"
            label="New Password"
            placeholder="New Password"
            control={control}
            rules={{ required: 'Required', minLength: { value: 6, message: 'Min 6 chars' } }}
          />
          <CustomInput
            name="confirmPassword"
            label="Confirm Password"
            placeholder="Confirm Password"
            control={control}
            rules={{ 
              validate: (value: string) => value === pwd || 'Passwords do not match' 
            }}
          />
          <TouchableOpacity style={styles.button} onPress={handleSubmit(onSubmit)}>
            <Text style={styles.buttonText}>Submit</Text>
          </TouchableOpacity>
        </>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: COLORS.white, paddingTop: 50 },
  backButton: { marginBottom: 20 },
  title: { fontSize: 24, fontWeight: 'bold', color: COLORS.primary, textAlign: 'center', marginBottom: 40 },
  subtitle: { textAlign: 'center', marginBottom: 20, color: COLORS.text },
  button: { backgroundColor: COLORS.primary, padding: 15, borderRadius: 30, alignItems: 'center', marginTop: 20 },
  buttonText: { color: COLORS.white, fontWeight: 'bold', fontSize: 16 },
});

export default ForgotPasswordScreen;