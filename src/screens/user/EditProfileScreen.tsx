import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Image,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { pick, types, isErrorWithCode, errorCodes } from '@react-native-documents/picker';
import { authService } from '../../services/authService';

import Header from '../../components/Header';

const EditProfileScreen = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { userData, updateProfile } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  // โหลดข้อมูลเดิมมาใส่ใน Input เมื่อเข้าหน้านี้
  useEffect(() => {
    if (userData) {
      setName(userData.name || '');
      setEmail(userData.email || '');
      setPhone(userData.phone || '');
    }
  }, [userData]);

  const handlePickImage = async () => {
    try {
      const [result] = await pick({
        presentationStyle: 'fullScreen',
        copyTo: 'cachesDirectory',
        type: [types.images],
      });
      if (result) {
        setAvatarUri((result as any).fileCopyUri || result.uri);
        setAvatarFile(result);
      }
    } catch (err: any) {
      if (!(isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED)) {
        Alert.alert('Error', 'Failed to pick image');
      }
    }
  };

  const handleSave = async () => {
    if (!name.trim()) {
      Alert.alert('Error', 'Name cannot be empty');
      return;
    }
    if (!userData?.id) {
      Alert.alert('Error', 'User ID not found');
      return;
    }

    setLoading(true);
    try {
      if (avatarFile) {
         await authService.uploadProfileImage(userData.id, {
           uri: (avatarFile as any).fileCopyUri || avatarFile.uri,
           type: avatarFile.type || 'image/jpeg',
           name: avatarFile.name || 'avatar.jpg',
         });
      }

      const updatedInfo = await authService.updateProfile(userData.id, {
        displayName: name,
        email,
        phoneNumber: phone,
      });

      // อัปเดตข้อมูลใน Context
      updateProfile({ 
        name, 
        email, 
        phone, 
        avatar: updatedInfo.avatar || avatarUri || userData?.avatar 
      }); 
      
      Alert.alert('Success', 'Profile updated successfully', [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (err: any) {
      console.error('Update Profile Error:', err);
      Alert.alert('Error', err.response?.data?.message || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Header title="Edit Profile" />
      <ScrollView contentContainerStyle={styles.content}>
        {/* Avatar Edit Section */}
        <View style={styles.avatarContainer}>
          <Image source={{ uri: avatarUri || userData?.avatar || 'https://via.placeholder.com/150' }} style={styles.avatar} />
          <TouchableOpacity style={styles.cameraButton} onPress={handlePickImage} disabled={loading}>
            <Icon name="camera" size={20} color="#fff" />
          </TouchableOpacity>
        </View>

        {/* Input Fields */}
        <View style={styles.form}>
          <Text style={[styles.label, { color: theme.text }]}>Full Name</Text>
          <View
            style={[
              styles.inputContainer,
              { backgroundColor: theme.card, borderColor: theme.border },
            ]}
          >
            <Icon
              name="account-outline"
              size={20}
              color={theme.subText}
              style={styles.inputIcon}
            />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={name}
              onChangeText={setName}
              placeholder="Enter your name"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>
            Email Address
          </Text>
          <View
            style={[
              styles.inputContainer,
              { backgroundColor: theme.card, borderColor: theme.border },
            ]}
          >
            <Icon
              name="email-outline"
              size={20}
              color={theme.subText}
              style={styles.inputIcon}
            />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={email}
              onChangeText={setEmail}
              placeholder="Enter your email"
              placeholderTextColor={theme.subText}
              keyboardType="email-address"
              autoCapitalize="none"
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>
            Phone Number
          </Text>
          <View
            style={[
              styles.inputContainer,
              { backgroundColor: theme.card, borderColor: theme.border },
            ]}
          >
            <Icon
              name="phone-outline"
              size={20}
              color={theme.subText}
              style={styles.inputIcon}
            />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={phone}
              onChangeText={setPhone}
              placeholder="Enter phone number"
              placeholderTextColor={theme.subText}
              keyboardType="phone-pad"
            />
          </View>
        </View>

        {/* Save Button */}
        <TouchableOpacity
          style={styles.saveButton}
          onPress={handleSave}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.saveButtonText}>Update Profile</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20 },
  avatarContainer: { alignItems: 'center', marginBottom: 30, marginTop: 10 },
  avatar: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 4,
    borderColor: '#fff',
  },
  cameraButton: {
    position: 'absolute',
    bottom: 0,
    right: '33%',
    backgroundColor: '#2563EB',
    padding: 8,
    borderRadius: 20,
    borderWidth: 3,
    borderColor: '#fff',
  },
  form: { marginBottom: 20 },
  label: { fontSize: 14, fontWeight: '600', marginBottom: 8, marginLeft: 4 },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 20,
    paddingHorizontal: 12,
    height: 50,
  },
  inputIcon: { marginRight: 10 },
  input: { flex: 1, fontSize: 16 },
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

export default EditProfileScreen;
