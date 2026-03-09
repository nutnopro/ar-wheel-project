import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
  Platform,
} from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-aware-scroll-view';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { authService } from '../../services/authService';

import Header from '../../components/Header';

const PasswordInput = ({
  label,
  value,
  onChangeText,
  showPass,
  toggleShowPass,
  placeholder,
  theme,
}: any) => (
  <View style={styles.inputGroup}>
    <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
    <View
      style={[
        styles.inputContainer,
        { backgroundColor: theme.card, borderColor: theme.border },
      ]}
    >
      <Icon
        name="lock-outline"
        size={20}
        color={theme.subText}
        style={styles.inputIcon}
      />

      <TextInput
        style={[styles.input, { color: theme.text }]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.subText}
        secureTextEntry={!showPass} // ถ้า showPass=false ให้ซ่อนรหัส
        autoCapitalize="none"
      />

      {/* ปุ่มดวงตา */}
      <TouchableOpacity onPress={toggleShowPass} style={styles.eyeButton}>
        <Icon
          name={showPass ? 'eye-off-outline' : 'eye-outline'}
          size={22}
          color={theme.subText}
        />
      </TouchableOpacity>
    </View>
  </View>
);

const ChangePasswordScreen = () => {

  const navigation = useNavigation();
  const { theme } = useTheme();
  const { isLoading, userData } = useAuth(); // หรือสร้าง loading state เองในหน้านี้ก็ได้

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // State สำหรับควบคุมการมองเห็นรหัสผ่าน (แยกกันแต่ละช่อง)
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);

  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      Alert.alert('Error', 'Please fill in all fields');
      return;
    }

    if (newPassword !== confirmPassword) {
      Alert.alert('Error', 'New passwords do not match');
      return;
    }

    if (newPassword.length < 6) {
      Alert.alert('Error', 'Password must be at least 6 characters');
      return;
    }

    if (!userData?.email) {
      Alert.alert('Error', 'User email not found. Please log in again.');
      return;
    }

    setSaving(true);
    
    try {
      await authService.changePassword(userData.email, currentPassword, newPassword);
      Alert.alert('Success', 'Password changed successfully', [
        { text: 'OK', onPress: () => setTimeout(() => navigation.goBack(), 100) },
      ]);
    } catch (error: any) {
       console.error('Change Password Error:', error);
       let msg = error?.response?.data?.message || 'Failed to change password. Please check your current password.';
       if (Array.isArray(msg)) {
         msg = msg.join('\n');
       } else if (typeof msg === 'object') {
         msg = JSON.stringify(msg);
       }
       Alert.alert('Error', msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <KeyboardAwareScrollView
      style={[styles.container, { backgroundColor: theme.background }]}
      contentContainerStyle={styles.content}
      enableOnAndroid={true}
      extraScrollHeight={20}
      keyboardShouldPersistTaps="handled"
    >
      <Header title="Change Password" />
        <View style={styles.headerIconContainer}>
          <View style={styles.iconCircle}>
            <Icon name="lock-reset" size={40} color="#2563EB" />
          </View>
          <Text style={[styles.headerTitle, { color: theme.text }]}>
            Create New Password
          </Text>
          <Text style={styles.headerSubtitle}>
            Your new password must be different from previous used passwords.
          </Text>
        </View>

        <View style={styles.form}>
          <PasswordInput
            label="Current Password"
            value={currentPassword}
            onChangeText={setCurrentPassword}
            showPass={showCurrentPass}
            toggleShowPass={() => setShowCurrentPass(!showCurrentPass)}
            placeholder="Enter current password"
            theme={theme}
          />

          <PasswordInput
            label="New Password"
            value={newPassword}
            onChangeText={setNewPassword}
            showPass={showNewPass}
            toggleShowPass={() => setShowNewPass(!showNewPass)}
            placeholder="Enter new password"
            theme={theme}
          />

          <PasswordInput
            label="Confirm New Password"
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            showPass={showConfirmPass}
            toggleShowPass={() => setShowConfirmPass(!showConfirmPass)}
            placeholder="Confirm new password"
            theme={theme}
          />
        </View>

        <TouchableOpacity
          style={styles.saveButton}
          onPress={handleSave}
          disabled={saving}
        >
          {saving ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.saveButtonText}>Reset Password</Text>
          )}
        </TouchableOpacity>
    </KeyboardAwareScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 24 },

  headerIconContainer: {
    alignItems: 'center',
    marginBottom: 30,
    marginTop: 10,
  },
  iconCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#EFF6FF',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  headerTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 8 },
  headerSubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    paddingHorizontal: 20,
    lineHeight: 20,
  },

  form: { marginBottom: 20 },
  inputGroup: { marginBottom: 20 },
  label: { fontSize: 14, fontWeight: '600', marginBottom: 8, marginLeft: 4 },

  // Style ของ Input ที่มี icon ซ้ายขวา
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    height: 50,
  },
  inputIcon: { marginRight: 10 },
  input: { flex: 1, fontSize: 16 },
  eyeButton: { padding: 5 }, // พื้นที่กดปุ่มตา

  saveButton: {
    backgroundColor: '#2563EB',
    height: 50,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 10,
    shadowColor: '#2563EB',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 5,
  },
  saveButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
});

export default ChangePasswordScreen;
