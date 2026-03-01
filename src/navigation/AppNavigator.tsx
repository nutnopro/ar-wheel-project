// src/navigation/AppNavigator.tsx
import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

import { ThemeProvider, useTheme } from '../context/ThemeContext';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { LanguageProvider, useLanguage } from '../context/LanguageContext';

// User Screens
import SplashScreen from '../screens/common/SplashScreen';
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';
import ForgotPasswordScreen from '../screens/auth/ForgotPasswordScreen';
import HomeScreen from '../screens/user/HomeScreen';
import ProductDetailScreen from '../screens/user/ProductDetailScreen';
import ArScreen from '../screens/user/ArScreen';
import ProfileScreen from '../screens/user/ProfileScreen';
import FavoritesScreen from '../screens/user/FavoritesScreen';
import EditProfileScreen from '../screens/user/EditProfileScreen';
import ChangePasswordScreen from '../screens/user/ChangePasswordScreen';
import LanguageScreen from '../screens/user/LanguageScreen';
import ARPreferencesScreen from '../screens/user/ARPreferencesScreen';

// Admin Screens
import ManageUsersScreen from '../screens/admin/ManageUsersScreen';
import ManageStoresScreen from '../screens/admin/ManageStoresScreen';
import ManageCategoriesScreen from '../screens/admin/ManageCategoriesScreen';
import ManageModelsScreen from '../screens/admin/ManageModelsScreen';
import SystemLogsScreen from '../screens/admin/SystemLogsScreen';
import AdminDashboardScreen from '../screens/admin/AdminDashboardScreen';

export type RootStackParamList = {
  Splash: undefined;
  SignIn: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  MainApp: undefined;
  ProductDetail: { item: any };
  Favorites: undefined;
  EditProfile: undefined;
  ChangePassword: undefined;
  Language: undefined;
  ARPreferences: undefined;

  // Admin
  AdminDashboard: undefined;
  ManageUsers: undefined;
  ManageStores: undefined;
  ManageModels: undefined;
  ManageCategories: undefined;
  SystemLogs: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator();

// --- Main Tab Navigator ---
function MainTabNavigator() {
  const { theme } = useTheme();
  const { t } = useLanguage();

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarShowLabel: false,
        tabBarStyle: [
          styles.tabBarContainer,
          { backgroundColor: theme.card, shadowColor: theme.text },
        ],
      }}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <View style={styles.iconContainer}>
              <Icon
                name={focused ? 'home' : 'home-outline'}
                size={28}
                color={focused ? theme.icon : theme.subText}
              />
              <Text
                style={[
                  styles.label,
                  { color: focused ? theme.icon : theme.subText },
                ]}
              >
                {t.tab_home}
              </Text>
            </View>
          ),
        }}
      />
      <Tab.Screen
        name="AR"
        component={ArScreen}
        options={{
          tabBarStyle: { display: 'none' },
          tabBarIcon: () => (
            <View style={styles.arButtonWrapper}>
              <View style={styles.diamondShape}>
                <View style={{ transform: [{ rotate: '-45deg' }] }}>
                  <Icon name="cube-scan" size={30} color="white" />
                </View>
              </View>
              <Text style={styles.arLabel}>{t.tab_ar}</Text>
            </View>
          ),
        }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <View style={styles.iconContainer}>
              <Icon
                name={focused ? 'account' : 'account-outline'}
                size={28}
                color={focused ? theme.icon : theme.subText}
              />
              <Text
                style={[
                  styles.label,
                  { color: focused ? theme.icon : theme.subText },
                ]}
              >
                {t.tab_profile}
              </Text>
            </View>
          ),
        }}
      />
    </Tab.Navigator>
  );
}

// --- App Navigation Wrapper ---
const AppNavigationWrapper = () => {
  const { userRole } = useAuth();
  const { t } = useLanguage();
  const { theme } = useTheme();

  const renderBackButton = (navigation: any) => (
    <TouchableOpacity
      onPress={() => navigation.goBack()}
      style={{ paddingRight: 15, paddingVertical: 5 }}
    >
      <Icon name="arrow-left" size={24} color={theme.text} />
    </TouchableOpacity>
  );

  const adminSubPageOptions = ({ navigation, route }: any) => ({
    headerShown: true,
    title: route.name.replace(/([A-Z])/g, ' $1').trim(),
    headerStyle: { backgroundColor: theme.card },
    headerTintColor: theme.text,
    headerTitleStyle: { fontWeight: 'bold' as const },
    headerLeft: () => renderBackButton(navigation),
    headerShadowVisible: false,
  });

  return (
    <Stack.Navigator
      initialRouteName="Splash"
      screenOptions={{ headerShown: false }}
    >
      {userRole === null ? (
        <>
          <Stack.Screen name="Splash" component={SplashScreen} />
          <Stack.Screen name="SignIn" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen
            name="ForgotPassword"
            component={ForgotPasswordScreen}
          />
        </>
      ) : (
        <>
          <Stack.Screen name="MainApp" component={MainTabNavigator} />

          {/* User Screens */}
          <Stack.Screen name="ProductDetail" component={ProductDetailScreen} />
          <Stack.Screen name="Favorites" component={FavoritesScreen} />
          <Stack.Screen name="EditProfile" component={EditProfileScreen} />
          <Stack.Screen
            name="ChangePassword"
            component={ChangePasswordScreen}
          />
          <Stack.Screen name="Language" component={LanguageScreen} />
          <Stack.Screen name="ARPreferences" component={ARPreferencesScreen} />

          {/* Admin Screens: แสดงเฉพาะเมื่อ role === 'admin' */}
          {userRole === 'admin' && (
            <>
              <Stack.Screen
                name="AdminDashboard"
                component={AdminDashboardScreen}
                options={adminSubPageOptions}
              />
              <Stack.Screen
                name="ManageUsers"
                component={ManageUsersScreen}
                options={adminSubPageOptions}
              />
              <Stack.Screen
                name="ManageStores"
                component={ManageStoresScreen}
                options={adminSubPageOptions}
              />
              <Stack.Screen
                name="ManageCategories"
                component={ManageCategoriesScreen}
                options={adminSubPageOptions}
              />
              <Stack.Screen
                name="ManageModels"
                component={ManageModelsScreen}
                options={adminSubPageOptions}
              />
              <Stack.Screen
                name="SystemLogs"
                component={SystemLogsScreen}
                options={adminSubPageOptions}
              />
            </>
          )}
        </>
      )}
    </Stack.Navigator>
  );
};

export default function AppNavigator() {
  return (
    <AuthProvider>
      <ThemeProvider>
        <LanguageProvider>
          <NavigationContainer>
            <AppNavigationWrapper />
          </NavigationContainer>
        </LanguageProvider>
      </ThemeProvider>
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  tabBarContainer: {
    position: 'absolute',
    bottom: 20,
    left: 20,
    right: 20,
    height: 70,
    borderRadius: 35,
    borderTopWidth: 0,
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 5,
  },
  iconContainer: { alignItems: 'center', justifyContent: 'center', top: 0 },
  label: { fontSize: 10, fontWeight: '600', marginTop: 4 },
  arButtonWrapper: { alignItems: 'center', justifyContent: 'center', top: -20 },
  diamondShape: {
    width: 56,
    height: 56,
    backgroundColor: '#2563EB',
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    transform: [{ rotate: '45deg' }],
    shadowColor: '#2563EB',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 5,
    elevation: 8,
    borderWidth: 3,
    borderColor: '#FFFFFF',
  },
  arLabel: {
    color: '#2563EB',
    marginTop: 10,
    fontWeight: 'bold',
    fontSize: 11,
  },
});
