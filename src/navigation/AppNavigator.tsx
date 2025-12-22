// src/navigation/AppNavigator.tsx
import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { COLORS } from '../theme/colors';

import SplashScreen from '../screens/auth/SplashScreen';
import LoginScreen from '../screens/auth/LoginScreen';
import HomeScreen from '../screens/user/HomeScreen';
import ProductDetailScreen from '../screens/user/ProductDetailScreen';
import ARNativeScreen from '../screens/user/ARScreen';
import ProfileScreen from '../screens/user/ProfileScreen';
import StoreDashboard from '../screens/admin/SystemDashboard';
import FavoritesScreen from '../screens/user/FavoritesScreen';
import Ionicons from '@react-native-vector-icons/ionicons';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

function UserTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: COLORS.primary,
        tabBarInactiveTintColor: COLORS.textDim,
        tabBarStyle: { height: 60, paddingBottom: 10, paddingTop: 10 },
        tabBarIcon: ({ color, size }) => {
          let iconName: React.ComponentProps<typeof Ionicons>['name'] = 'home';
          if (route.name === 'Home') iconName = 'home';
          else if (route.name === 'Favorites') iconName = 'heart';
          else if (route.name === 'Profile') iconName = 'person';
          return <Ionicons name={iconName} size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Favorites" component={FavoritesScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {/* 1. Splash & Auth */}
      <Stack.Screen name="Splash" component={SplashScreen} />
      <Stack.Screen name="Login" component={LoginScreen} />
      {/* (Register, ForgotPass...) */}
      {/* 2. Main App (Tabs) */}
      <Stack.Screen name="MainTabs" component={UserTabs} />
      {/* 3. Detail & AR Screens (ซ่อน TabBar) */}
      <Stack.Screen
        name="ProductDetail"
        component={ProductDetailScreen}
        options={{ headerShown: true, title: '' }}
      />
      <Stack.Screen name="ARNative" component={ARNativeScreen} />
      {/* 4. Settings & Profile Edits */}
      <Stack.Screen name="EditProfile" component={ProfileScreen} />{' '}
      {/* Placeholder */}
      <Stack.Screen name="Settings" component={ProfileScreen} />{' '}
      {/* Placeholder */}
      {/* 5. Admin Flow */}
      <Stack.Screen name="AdminDashboard" component={StoreDashboard} />
    </Stack.Navigator>
  );
}
