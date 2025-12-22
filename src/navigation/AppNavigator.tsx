// src/navigation/AppNavigator.tsx
import React from 'react';
import { View, NativeModules, Alert } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { COLORS } from '../theme/colors';

// Commons Screens
import SplashScreen from '../screens/auth/SplashScreen';
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';
import ForgotPasswordScreen from '../screens/auth/ForgotPasswordScreen';
import HomeScreen from '../screens/common/HomeScreen';
import ProductDetailScreen from '../screens/common/ProductDetailScreen';
import ProfileScreen from '../screens/common/ProfileScreen';
import EditProfileScreen from '../screens/common/EditProfileScreen';

// User Screens
import FavoritesScreen from '../screens/user/FavoritesScreen';

// Store Screens
import StoreModelsScreen from '../screens/store/StoreModelsScreen';
import StoreAddEditModelScreen from '../screens/store/StoreAddEditModelScreen';
import StoreStatisticsScreen from '../screens/store/StoreStatisticsScreen';

// Admin Screens
import SystemManagementScreen from '../screens/admin/SystemManagementScreen';
import ManageUsersScreen from '../screens/admin/ManageUsersScreen';
import ManageStoresScreen from '../screens/admin/ManageStoresScreen';
import AdminModelsScreen from '../screens/admin/AdminModelsScreen';
import AdminAddEditModelScreen from '../screens/admin/AdminAddEditModelScreen';
import ManageCategoriesScreen from '../screens/admin/ManageCategoriesScreen';
import LogsScreen from '../screens/admin/LogsScreen';
import SystemStatisticsScreen from '../screens/admin/SystemStatisticsScreen';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();
const { ARLauncher } = NativeModules;

// Placeholder สำหรับปุ่ม AR
const ARPlaceholder = () => (
  <View style={{ flex: 1, backgroundColor: '#000' }} />
);

function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: COLORS.primary,
        tabBarInactiveTintColor: COLORS.textDim,
        tabBarStyle: { height: 60, paddingBottom: 10, paddingTop: 10 },
        tabBarIcon: ({ color, size }) => {
          let iconName = 'home-outline';
          if (route.name === 'Home') iconName = 'home-outline';
          else if (route.name === 'AR') iconName = 'cube-outline';
          else if (route.name === 'Profile') iconName = 'person-outline';
          return <Ionicons name={iconName as any} size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen
        name="AR"
        component={ARPlaceholder}
        listeners={{
          tabPress: e => {
            e.preventDefault();
            if (ARLauncher && ARLauncher.openARActivity) {
              ARLauncher.openARActivity();
            } else {
              Alert.alert('AR Error', 'AR Module not found');
            }
          },
        }}
      />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {/* --- Auth Flow --- */}
      <Stack.Screen name="Splash" component={SplashScreen} />
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
      <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />

      {/* --- Main Flow (User) --- */}
      <Stack.Screen name="MainTabs" component={MainTabs} />
      <Stack.Screen name="ProductDetail" component={ProductDetailScreen} />
      <Stack.Screen name="Favorites" component={FavoritesScreen} />
      <Stack.Screen name="EditProfile" component={EditProfileScreen} />

      {/* --- Store Flow --- */}
      <Stack.Screen name="StoreModels" component={StoreModelsScreen} />
      <Stack.Screen
        name="StoreAddEditModel"
        component={StoreAddEditModelScreen}
      />
      <Stack.Screen name="StoreStatistics" component={StoreStatisticsScreen} />

      {/* --- Admin Flow --- */}
      <Stack.Screen
        name="SystemManagement"
        component={SystemManagementScreen}
      />
      <Stack.Screen name="AdminStatistics" component={SystemStatisticsScreen} />

      {/* Admin: Management List Group */}
      <Stack.Screen name="ManageUsers" component={ManageUsersScreen} />
      <Stack.Screen name="ManageStores" component={ManageStoresScreen} />
      <Stack.Screen
        name="ManageCategories"
        component={ManageCategoriesScreen}
      />

      {/* Admin: Model Management */}
      <Stack.Screen name="AdminModels" component={AdminModelsScreen} />
      <Stack.Screen
        name="AdminAddEditModel"
        component={AdminAddEditModelScreen}
      />

      <Stack.Screen name="Logs" component={LogsScreen} />
    </Stack.Navigator>
  );
}
