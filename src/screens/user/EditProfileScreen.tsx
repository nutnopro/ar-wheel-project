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
  Platform,
  TouchableWithoutFeedback,
  Keyboard,
} from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-aware-scroll-view';
import { useNavigation } from '@react-navigation/native';
import DateTimePicker from '@react-native-community/datetimepicker';
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
  const [gender, setGender] = useState('');
  const [dob, setDob] = useState<Date | null>(null);
  const [showDatePicker, setShowDatePicker] = useState(false);

  // Address fields
  const [houseNumber, setHouseNumber] = useState('');
  const [street, setStreet] = useState('');
  const [subdistrict, setSubdistrict] = useState('');
  const [district, setDistrict] = useState('');
  const [stateOrProvince, setStateOrProvince] = useState('');
  const [country, setCountry] = useState('');
  const [postcode, setPostcode] = useState('');

  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  // โหลดข้อมูลเดิมมาใส่ใน Input เมื่อเข้าหน้านี้
  useEffect(() => {
    if (userData) {
      setName(userData.displayName || userData.name || '');
      setEmail(userData.email || '');
      setPhone(userData.phoneNumber || userData.phone || '');
      setGender(userData.gender || '');
      if (userData.dateOfBirth) {
        const parsedDate = new Date(userData.dateOfBirth);
        if (!isNaN(parsedDate.getTime())) {
          setDob(parsedDate);
        } else {
          setDob(null);
        }
      } else {
        setDob(null);
      }
      setHouseNumber(userData.address?.houseNumber || '');
      setStreet(userData.address?.street || '');
      setSubdistrict(userData.address?.subdistrict || '');
      setDistrict(userData.address?.district || '');
      setStateOrProvince(userData.address?.stateOrProvince || '');
      setCountry(userData.address?.country || '');
      setPostcode(userData.address?.postcode || '');

      setAvatarUri(userData.profileImageUrl || userData.avatar || userData.profileImg || null);
    }
  }, [userData]);

  const handlePickImage = async () => {
    try {
      const [result] = await pick({
        presentationStyle: 'fullScreen',
        type: [types.images],
      });
      if (result) {
        // ใช้ uri โดยตรงแทน fileCopyUri สำหรับความเข้ากันได้ที่ดีขึ้น
        const imageUri = result.uri;
        setAvatarUri(imageUri);
        setAvatarFile(result);
        console.log('Image selected:', imageUri);
      }
    } catch (err: any) {
      if (!(isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED)) {
        console.error('Image picker error:', err);
        Alert.alert('Error', 'Failed to pick image. Please try again.');
      }
    }
  };

	const handleSave = async () => {
    if (!userData?.id && !userData?.uid) {
      Alert.alert('Error', 'User ID not found');
      return;
    }

    setLoading(true);
    try {
      const userId = userData.id || userData.uid;

      let latestProfileImg = userData.profileImg || userData.profileImageUrl;
      
      // อัปโหลดรูปภาพใหม่ถ้ามีการเลือกรูป
      if (avatarFile) {
        console.log('Uploading profile image...');
        const fileData = {
          uri: avatarFile.uri,
          type: avatarFile.type || 'image/jpeg',
          name: avatarFile.name || `avatar_${new Date().getTime()}.jpg`,
        };
        
        const uploadRes = await authService.uploadProfileImage(userId, fileData);
        console.log('Upload response:', uploadRes);
        
        if (uploadRes && (uploadRes.profileImg || uploadRes.profileImageUrl)) {
          latestProfileImg = uploadRes.profileImg || uploadRes.profileImageUrl;
          console.log('New profile image URL:', latestProfileImg);
        }
      }
      
      // เพิ่ม timestamp เพื่อป้องกัน caching
      const timestamp = new Date().getTime();
      const cleanUrl = latestProfileImg ? latestProfileImg.split(/[?&]t=/)[0] : latestProfileImg;
      const finalImgWithTimestamp = cleanUrl ? `${cleanUrl}${cleanUrl.includes('?') ? '&' : '?'}t=${timestamp}` : null;

      const payload = {
        displayName: name.trim() || null,
        email: email.trim() || null,
        phoneNumber: phone.trim() || null,
        gender: gender.trim() || null,
        dateOfBirth: dob ? dob.toISOString().split('T')[0] : null,
        profileImg: finalImgWithTimestamp,
        address: {
          houseNumber: houseNumber.trim() || null,
          street: street.trim() || null,
          subdistrict: subdistrict.trim() || null,
          district: district.trim() || null,
          stateOrProvince: stateOrProvince.trim() || null,
          country: country.trim() || null,
          postcode: postcode.trim() || null,
        }
      };

      console.log('Updating profile with payload:', payload);
      const updatedInfo = await authService.updateProfile(userId, payload);
      console.log('Profile update response:', updatedInfo);

      const actualUser = updatedInfo?.user ? updatedInfo.user : updatedInfo;
      
      // อัปเดต state ใน AuthContext
      updateProfile({
        ...userData,
        ...payload,
        profileImg: finalImgWithTimestamp
      });
      
      Alert.alert('Success', 'Profile updated successfully', [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (err: any) {
      console.error('Update Profile Error:', err);
      let msg = err.response?.data?.message || 'Failed to update profile';
      if (Array.isArray(msg)) msg = msg.join('\n');
      Alert.alert('Error', typeof msg === 'object' ? JSON.stringify(msg) : msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Header title="Edit Profile" />
      <KeyboardAwareScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.content}
        enableOnAndroid={true}
        extraScrollHeight={20}
        keyboardShouldPersistTaps="handled"
      >
        {/* Avatar Edit Section */}
        <View style={styles.avatarContainer}>
        <Image 
          key={avatarUri}
          source={{ 
          uri: avatarUri || 'https://via.placeholder.com/150',
          cache: 'reload'
          }} 
          style={styles.avatar} 
        />
          <TouchableOpacity style={styles.cameraButton} onPress={handlePickImage} disabled={loading}>
            <Icon name="camera" size={20} color="#fff" />
          </TouchableOpacity>
        </View>

        {/* Input Fields */}
        <View style={styles.form}>
          <Text style={[styles.label, { color: theme.text }]}>Full Name</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="account-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={name}
              onChangeText={setName}
              placeholder="Enter your full name"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Email Address</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="email-outline" size={20} color={theme.subText} style={styles.inputIcon} />
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

          <Text style={[styles.label, { color: theme.text }]}>Phone Number</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="phone-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={phone}
              onChangeText={setPhone}
              placeholder="Enter phone number"
              placeholderTextColor={theme.subText}
              keyboardType="phone-pad"
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Gender</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="gender-male-female" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={gender}
              onChangeText={setGender}
              placeholder="Enter gender (e.g. male, female)"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Date of Birth</Text>
          <View style={styles.datePickerContainer}>
            <TouchableOpacity
              style={[
                styles.inputContainer,
                { backgroundColor: theme.card, borderColor: theme.border, marginBottom: 0 }
              ]}
              onPress={() => setShowDatePicker(true)}
            >
              <Icon name="calendar" size={20} color={theme.subText} style={styles.inputIcon} />
              <Text style={[styles.input, { color: dob ? theme.text : theme.subText, lineHeight: 22 }]}>
                {dob ? dob.toISOString().split('T')[0] : 'YYYY-MM-DD'}
              </Text>
            </TouchableOpacity>
            
            {showDatePicker && (
              <DateTimePicker
                value={dob || new Date()}
                mode="date"
                display="default"
                maximumDate={new Date()}
                onChange={(event: any, selectedDate?: Date) => {
                  setShowDatePicker(false);
                  if (selectedDate) setDob(selectedDate);
                }}
              />
            )}
          </View>

          <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 10 }]}>Address</Text>

          <Text style={[styles.label, { color: theme.text }]}>House Number</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="home-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={houseNumber}
              onChangeText={setHouseNumber}
              placeholder="House mapping number"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Street</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="road-variant" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={street}
              onChangeText={setStreet}
              placeholder="Street name"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Subdistrict</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="map-marker-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={subdistrict}
              onChangeText={setSubdistrict}
              placeholder="Subdistrict"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>District</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="map-marker-radius-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={district}
              onChangeText={setDistrict}
              placeholder="District"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>State / Province</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="city" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={stateOrProvince}
              onChangeText={setStateOrProvince}
              placeholder="State or Province"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Country</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="earth" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={country}
              onChangeText={setCountry}
              placeholder="Country"
              placeholderTextColor={theme.subText}
            />
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Postcode</Text>
          <View style={[styles.inputContainer, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Icon name="mailbox-open-outline" size={20} color={theme.subText} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, { color: theme.text }]}
              value={postcode}
              onChangeText={setPostcode}
              placeholder="Postcode / Zip"
              placeholderTextColor={theme.subText}
              keyboardType="numeric"
            />
          </View>
        </View>

        {/* Save Button */}
        <TouchableOpacity style={styles.saveButton} onPress={handleSave} disabled={loading}>
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.saveButtonText}>Update Profile</Text>
          )}
        </TouchableOpacity>
      </KeyboardAwareScrollView>
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
  datePickerContainer: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 15,
  },
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
