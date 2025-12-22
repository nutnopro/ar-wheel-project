import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
} from 'react-native';
import CustomInput from '../../components/CustomInput';
import CustomButton from '../../components/CustomButton';
import { COLORS } from '../../theme/colors';

export default function LoginScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = () => {
    // Logic Login ที่นี่
    // ถ้าเป็น Admin -> navigation.replace('AdminTabs');
    navigation.replace('MainTabs');
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <Text style={styles.title}>ยินดีต้อนรับกลับ</Text>
        <Text style={styles.sub}>กรุณาเข้าสู่ระบบเพื่อใช้งาน</Text>

        <CustomInput
          label="อีเมล"
          value={email}
          onChangeText={setEmail}
          placeholder="example@email.com"
        />
        <CustomInput
          label="รหัสผ่าน"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder="••••••"
        />

        <TouchableOpacity
          onPress={() => navigation.navigate('ForgotPassword')}
          style={{ alignSelf: 'flex-end', marginBottom: 24 }}
        >
          <Text style={{ color: COLORS.primary }}>ลืมรหัสผ่าน?</Text>
        </TouchableOpacity>

        <CustomButton title="เข้าสู่ระบบ" onPress={handleLogin} />

        <View style={styles.footer}>
          <Text style={{ color: COLORS.textDim }}>ยังไม่มีบัญชี? </Text>
          <TouchableOpacity onPress={() => navigation.navigate('Register')}>
            <Text style={{ color: COLORS.primary, fontWeight: 'bold' }}>
              สมัครสมาชิก
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.background },
  content: { flex: 1, padding: 24, justifyContent: 'center' },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: COLORS.text,
    marginBottom: 8,
  },
  sub: { fontSize: 16, color: COLORS.textDim, marginBottom: 32 },
  footer: { flexDirection: 'row', justifyContent: 'center', marginTop: 16 },
});

// (RegisterScreen และ ForgotPasswordScreen เขียนคล้าย LoginScreen แต่เปลี่ยน Input)
