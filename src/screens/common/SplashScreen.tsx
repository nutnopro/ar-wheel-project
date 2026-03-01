import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '../../navigation/AppNavigator';
import { COLORS } from '../../constants/colors'; // ตรวจสอบ path colors ให้ถูกนะครับ

type SplashScreenProp = NativeStackNavigationProp<RootStackParamList, 'Splash'>;

const SplashScreen = () => {
  const navigation = useNavigation<SplashScreenProp>();

  useEffect(() => {
    // ตั้งเวลา 3 วินาที แล้วเปลี่ยนไปหน้า SignIn
    const timer = setTimeout(() => {
      navigation.replace('SignIn'); // ใช้ replace เพื่อไม่ให้กด back กลับมาหน้า splash ได้
    }, 3000);

    return () => clearTimeout(timer); // เคลียร์ timer ถ้า component ถูกปิดก่อน
  }, [navigation]);

  return (
    <View style={styles.container}>
      {/* วงกลม Logo ล้อรถ */}
      <View style={styles.logoCircle}>
        <Icon name="steering" size={60} color={COLORS.primary} />
      </View>
      
      {/* ชื่อแอป */}
      <Text style={styles.appName}>Wheel AR</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#2563EB', // สีน้ำเงินตามแบบ (หรือใช้ COLORS.primary)
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoCircle: {
    width: 120,
    height: 120,
    backgroundColor: 'white',
    borderRadius: 60, // ครึ่งหนึ่งของ width เพื่อให้เป็นวงกลม
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    // เงาเล็กน้อยเพื่อความสวยงาม
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  appName: {
    fontSize: 28,
    color: 'white',
    fontWeight: 'bold',
    marginTop: 10,
  },
});

export default SplashScreen;