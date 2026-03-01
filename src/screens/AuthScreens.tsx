import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView, StatusBar, Alert } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

const COLORS = {
  primary: '#2563EB', secondary: '#1E40AF', white: '#FFFFFF', grayBg: '#F3F4F6',
  textDark: '#1F2937', textLight: '#6B7280', iconColor: '#9CA3AF'
};

// 1. Splash Screen
export function SplashScreen({ navigation }: any) {
  useEffect(() => { setTimeout(() => { navigation.replace('SignIn'); }, 2000); }, []);
  return (
    <View style={styles.splashContainer}>
      <StatusBar hidden />
      <View style={styles.logoCircle}><Icon name="car-traction-control" size={60} color={COLORS.primary} /></View>
      <Text style={styles.splashAppName}>ArWheel</Text>
    </View>
  );
}

// 2. Sign In Screen
export function SignInScreen({ navigation }: any) {
  const [showPassword, setShowPassword] = useState(false);
  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" hidden={false} backgroundColor="#fff" />
      <View style={styles.contentPadding}>
        <Text style={styles.headerTitle}>Sign In</Text>
        <Text style={styles.subHeader}>Welcome Back!</Text>
        <Text style={styles.label}>Username or Email</Text>
        <View style={styles.inputContainer}>
          <Icon name="account-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} />
          <TextInput style={styles.input} placeholder="Username or Email" placeholderTextColor={COLORS.iconColor}/>
        </View>
        <Text style={styles.label}>Password</Text>
        <View style={styles.inputContainer}>
          <Icon name="lock-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} />
          <TextInput style={styles.input} placeholder="Password" placeholderTextColor={COLORS.iconColor} secureTextEntry={!showPassword}/>
          <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
            <Icon name={showPassword ? "eye-outline" : "eye-off-outline"} size={22} color={COLORS.iconColor} />
          </TouchableOpacity>
        </View>
        <TouchableOpacity style={{ alignSelf: 'flex-end', marginTop: 5 }} onPress={() => navigation.navigate('ForgetPassword')}>
          <Text style={styles.linkTextSmall}>Forget password?</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.primaryButton} onPress={() => navigation.replace('MainApp')}>
          <Text style={styles.primaryButtonText}>Sign In</Text>
        </TouchableOpacity>
        <Text style={{ textAlign: 'center', marginVertical: 15, color: '#aaa' }}>or</Text>
        <TouchableOpacity style={styles.outlineButton} onPress={() => navigation.replace('MainApp')}>
          <Text style={styles.outlineButtonText}>Continue As Visitor</Text>
        </TouchableOpacity>
        <View style={styles.footerRow}>
          <Text style={{ color: COLORS.textLight, fontSize: 13 }}>Don't have an account? </Text>
          <TouchableOpacity onPress={() => navigation.navigate('SignUp')}><Text style={styles.linkTextBold}>Sign Up</Text></TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

// 3. Sign Up Screen
export function SignUpScreen({ navigation }: any) {
    const [showPassword, setShowPassword] = useState(false);
    return (
      <View style={styles.container}>
        <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}><Icon name="chevron-left" size={35} color={COLORS.primary} /></TouchableOpacity>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <Text style={styles.headerTitle}>New Account</Text>
          <Text style={styles.label}>Username</Text>
          <View style={styles.inputContainer}><Icon name="account-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} /><TextInput style={styles.input} placeholder="Username" placeholderTextColor={COLORS.iconColor}/></View>
          <Text style={styles.label}>Password</Text>
          <View style={styles.inputContainer}><Icon name="lock-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} /><TextInput style={styles.input} placeholder="Password" placeholderTextColor={COLORS.iconColor} secureTextEntry={!showPassword}/><TouchableOpacity onPress={() => setShowPassword(!showPassword)}><Icon name={showPassword ? "eye-outline" : "eye-off-outline"} size={22} color={COLORS.iconColor} /></TouchableOpacity></View>
          <Text style={styles.label}>Email</Text>
          <View style={styles.inputContainer}><Icon name="email-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} /><TextInput style={styles.input} placeholder="Email" placeholderTextColor={COLORS.iconColor}/></View>
          <Text style={styles.label}>Phone Number</Text>
          <View style={styles.inputContainer}><Icon name="phone-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} /><TextInput style={styles.input} placeholder="Phone Number" keyboardType="phone-pad" placeholderTextColor={COLORS.iconColor}/></View>
          <TouchableOpacity style={[styles.primaryButton, { marginTop: 25 }]}><Text style={styles.primaryButtonText}>Sign Up</Text></TouchableOpacity>
          <View style={styles.footerRow}><Text style={{ color: COLORS.textLight, fontSize: 13 }}>Already have an account? </Text><TouchableOpacity onPress={() => navigation.goBack()}><Text style={styles.linkTextBold}>Sign in</Text></TouchableOpacity></View>
        </ScrollView>
      </View>
    );
}

// 4. Forget Password Screen
export function ForgetPasswordScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  return (
    <View style={styles.container}>
       <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}><Icon name="chevron-left" size={35} color={COLORS.primary} /></TouchableOpacity>
      <View style={styles.contentPadding}>
        <Text style={styles.headerTitle}>Forget Password</Text>
        <Text style={[styles.subHeader, { textAlign: 'center', marginBottom: 20, color: COLORS.textLight, fontWeight: 'normal' }]}>Please enter your email address.</Text>
        <View style={styles.inputContainer}><Icon name="email-outline" size={20} color={COLORS.iconColor} style={styles.inputIcon} /><TextInput style={styles.input} placeholder="Email" placeholderTextColor={COLORS.iconColor} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none"/></View>
        <TouchableOpacity style={[styles.primaryButton, { width: '100%', alignSelf: 'center', marginTop: 25 }]} onPress={() => Alert.alert('Sent', 'Check your email', [{text:'OK', onPress:()=>navigation.navigate('SignIn')}])}><Text style={styles.primaryButtonText}>Send Reset Link</Text></TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  splashContainer: { flex: 1, backgroundColor: COLORS.primary, justifyContent: 'center', alignItems: 'center' },
  logoCircle: { width: 120, height: 120, backgroundColor: COLORS.white, borderRadius: 60, justifyContent: 'center', alignItems: 'center', marginBottom: 20 },
  splashAppName: { fontSize: 28, color: COLORS.white, fontWeight: 'bold', letterSpacing: 1 },
  container: { flex: 1, backgroundColor: COLORS.white },
  contentPadding: { paddingHorizontal: 25, paddingTop: 40 },
  scrollContent: { paddingHorizontal: 25, paddingBottom: 40, paddingTop: 10 },
  headerTitle: { fontSize: 28, fontWeight: 'bold', color: COLORS.primary, textAlign: 'center', marginBottom: 5, marginTop: 20 },
  subHeader: { fontSize: 16, color: COLORS.textLight, textAlign: 'center', marginBottom: 25 },
  label: { fontSize: 14, color: COLORS.textDark, fontWeight: '600', marginBottom: 8, marginTop: 15, marginLeft: 4 },
  inputContainer: { backgroundColor: COLORS.grayBg, borderRadius: 12, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, height: 50 },
  inputIcon: { marginRight: 10 },
  input: { flex: 1, fontSize: 15, color: COLORS.textDark, height: '100%' },
  primaryButton: { backgroundColor: COLORS.primary, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginTop: 20, elevation: 5 },
  primaryButtonText: { color: COLORS.white, fontSize: 16, fontWeight: 'bold' },
  outlineButton: { backgroundColor: COLORS.white, borderWidth: 1.5, borderColor: COLORS.primary, borderRadius: 12, paddingVertical: 12, alignItems: 'center' },
  outlineButtonText: { color: COLORS.primary, fontSize: 15, fontWeight: '600' },
  linkTextSmall: { color: COLORS.primary, fontSize: 13, fontWeight: '600' },
  linkTextBold: { color: COLORS.primary, fontSize: 13, fontWeight: 'bold', textDecorationLine: 'underline' },
  footerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: 25 },
  backButton: { position: 'absolute', top: 40, left: 20, zIndex: 10, padding: 5 },
});