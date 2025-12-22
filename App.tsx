import React from 'react';
import { StatusBar, LogBox } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import AppNavigator from './src/navigation/AppNavigator'; // ดึง Navigator ที่เรารวมทุกหน้าไว้มาใช้
import { COLORS } from './src/theme/colors';

// ปิด Warning รบกวนตาสีเหลือง (Optional: ใช้เฉพาะตอน Dev)
LogBox.ignoreLogs(['ViewPropTypes will be removed']);

const App = () => {
  return (
    // 1. SafeAreaProvider: ช่วยจัดการพื้นที่ขอบจอ (ติ่งหน้าจอ, มุมโค้ง) ของมือถือรุ่นใหม่ๆ
    <SafeAreaProvider>
      {/* 2. NavigationContainer: ตัวจัดการ State ของการเปลี่ยนหน้าทั้งหมด */}
      <NavigationContainer>
        {/* 3. StatusBar: ตั้งค่าแถบสถานะด้านบนให้กลืนไปกับแอป (Icon สีเข้ม, พื้นหลังสีเดียวกับแอป) */}
        <StatusBar
          barStyle="dark-content"
          backgroundColor={COLORS.background}
          translucent={false} // หรือ true ถ้าอยากให้ภาพเต็มจอสุดๆ
        />

        {/* 4. AppNavigator: ตัวเชื่อมโยงหน้าทั้ง 17 หน้าเข้าด้วยกัน */}
        <AppNavigator />
      </NavigationContainer>
    </SafeAreaProvider>
  );
};

export default App;
