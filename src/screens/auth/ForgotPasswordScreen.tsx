import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-aware-scroll-view';
import { useNavigation } from '@react-navigation/native';
import { useForm } from 'react-hook-form';
import CustomInput from '../../components/CustomInput';
import { COLORS } from '../../constants/colors';
import Icon from 'react-native-vector-icons/Ionicons';
import { authService } from '../../services/authService';

const ForgotPasswordScreen = () => {
  const navigation = useNavigation();
  const [loading, setLoading] = useState(false);
  const { control, handleSubmit } = useForm();

  const onSubmit = async (data: any) => {
    try {
      setLoading(true);
      await authService.forgotPassword(data.email);
      
      Alert.alert(
        'Email Sent',
        'Please check your inbox. We have sent a link to reset your password.',
        [{ text: 'OK', onPress: () => navigation.goBack() }]
      );
    } catch (error: any) {
      console.log('Forgot Password Error:', error?.response?.data || error.message);
      
      let errorMessage = 'Failed to send reset email. Please try again.';
      const backendMessage = error?.response?.data?.message;
      
      if (backendMessage) {
        errorMessage = Array.isArray(backendMessage) 
          ? backendMessage.join('\n') 
          : String(backendMessage);
      } else if (error?.message) {
        errorMessage = String(error.message);
      }

      Alert.alert('Error', errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAwareScrollView
      style={{ flex: 1 }}
      contentContainerStyle={styles.container}
      enableOnAndroid={true}
      extraScrollHeight={20}
      keyboardShouldPersistTaps="handled"
    >
      <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton} disabled={loading}>
        <Icon name="chevron-back" size={30} color={COLORS.primary} />
      </TouchableOpacity>

      <Text style={styles.title}>Forget Password</Text>
      <Text style={styles.subtitle}>Enter your email address to receive a reset link</Text>

      <CustomInput
        name="email"
        placeholder="Enter your email"
        control={control}
        rules={{
          required: 'Email is required',
          pattern: { 
            value: /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/, 
            message: 'Invalid email format' 
          },
        }}
      />

      <TouchableOpacity
        style={[styles.button, loading && { opacity: 0.7 }]}
        onPress={handleSubmit(onSubmit)}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color={COLORS.white} />
        ) : (
          <Text style={styles.buttonText}>Send Reset Link</Text>
        )}
      </TouchableOpacity>
    </KeyboardAwareScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    backgroundColor: COLORS.white,
    paddingTop: 50,
  },
  backButton: { marginBottom: 20 },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: COLORS.primary,
    textAlign: 'center',
    marginBottom: 10,
  },
  subtitle: { textAlign: 'center', marginBottom: 30, color: COLORS.text, paddingHorizontal: 10 },
  button: {
    backgroundColor: COLORS.primary,
    padding: 15,
    borderRadius: 30,
    alignItems: 'center',
    marginTop: 20,
  },
  buttonText: { color: COLORS.white, fontWeight: 'bold', fontSize: 16 },
});

export default ForgotPasswordScreen;
