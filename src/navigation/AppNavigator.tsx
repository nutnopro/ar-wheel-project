// src/navigation/AppNavigator.tsx
import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import HomeScreen from '../screens/HomeScreen';
import ARScreen from '../screens/ARScreen';
import ProfileScreen from '../screens/ProfileScreen';
import ModelDetailScreen from '../screens/ModelDetailScreen';
import CustomTabBar from '../components/CustomTabBar';
import BackButton from '../components/BackButton';
import { useTheme } from '../contexts/ThemeContext';

const Tab = createBottomTabNavigator();
const RootStack = createNativeStackNavigator();
const ProfileStack = createNativeStackNavigator();

function ProfileStackNav() {
  const { colors } = useTheme();
  return (
    <ProfileStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.primary },
        headerTintColor: '#fff',
      }}
    >
      <ProfileStack.Screen
        name="ProfileHome"
        component={ProfileScreen}
        options={{ title: 'โปรไฟล์' }}
      />
    </ProfileStack.Navigator>
  );
}

function MainTabs() {
  const { colors } = useTheme();
  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.primary },
        headerTintColor: '#fff',
      }}
      tabBar={props => <CustomTabBar {...props} />}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{ title: 'ARWheel' }}
      />
      <Tab.Screen name="AR" component={ARScreen} options={{ title: 'AR' }} />
      <Tab.Screen
        name="Profile"
        component={ProfileStackNav}
        options={{ headerShown: false }}
      />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <RootStack.Navigator screenOptions={{ headerShown: false }}>
      <RootStack.Screen name="MainTabs" component={MainTabs} />
      <RootStack.Screen name="ModelDetail" component={ModelDetailScreen} />
    </RootStack.Navigator>
  );
}
