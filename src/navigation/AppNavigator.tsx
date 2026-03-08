// src/navigation/AppNavigator.tsx
import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  NativeModules,
  Alert,
} from 'react-native';
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
import ProfileScreen from '../screens/user/ProfileScreen';
import FavoritesScreen from '../screens/user/FavoritesScreen';
import EditProfileScreen from '../screens/user/EditProfileScreen';
import ChangePasswordScreen from '../screens/user/ChangePasswordScreen';
import LanguageScreen from '../screens/user/LanguageScreen';
import ARPreferencesScreen from '../screens/user/ARPreferencesScreen';

// Admin Screens
import ManageUsersScreen from '../screens/admin/ManageUsersScreen';
import ManageCategoriesScreen from '../screens/admin/ManageCategoriesScreen';
import ManageModelsScreen from '../screens/admin/ManageModelsScreen';
import ManageAddModelScreen from '../screens/admin/ManageAddModelScreen';
import SystemLogsScreen from '../screens/admin/SystemLogsScreen';
import AdminDashboardScreen from '../screens/admin/AdminDashboardScreen';
import SystemStatsScreen from '../screens/admin/SystemStatsScreen';
import { resolveModelPath } from '../services/modelCacheService';
import { getSelectedModel, getModelPaths } from '../utils/storage';

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

  // Admin / Store
  AdminDashboard: undefined;
  ManageUsers: undefined;
  ManageModels: undefined;
  ManageAddModel: undefined;
  ManageCategories: undefined;
  SystemLogs: undefined;
  SystemStats: undefined;
};

const {ARLauncher} = NativeModules;

// AR tab screen — placeholder
const ArScreenPlaceholder = () => {
  return (
    <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
      <Text>Opening AR...</Text>
    </View>
  );
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
        component={ArScreenPlaceholder}
        listeners={({navigation}) => ({
          tabPress: (e: any) => {
            e.preventDefault();
            (async () => {
              try {
                if (ARLauncher && typeof ARLauncher.openARActivity === 'function') {
                  const savedModel = getSelectedModel();
                  const savedPaths = getModelPaths();
                  const initialPath = savedModel?.localPath || '';
                  
                  import('../utils/storage').then(({ storage }) => {
                    const sizeStr = storage?.getString('@ar_marker_size') || '15';
                    const markerSize = parseFloat(sizeStr) || 15.0;
                    ARLauncher.openARActivity(initialPath, JSON.stringify(savedPaths && savedPaths.length > 0 ? savedPaths : []), markerSize);
                  });
                } else {
                  Alert.alert('AR', 'AR Launcher is not available on this device');
                }
              } catch (err) {
                console.error('❌ Failed to open AR Activity:', err);
              }
            })();
          },
        })}
        options={{
          tabBarStyle: {display: 'none'},
          tabBarIcon: () => (
            <View style={styles.arButtonWrapper}>
              <View style={styles.diamondShape}>
                <View style={{transform: [{rotate: '-45deg'}]}}>
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
  const { userRole, isAppReady } = useAuth();
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

  const subPageOptions = ({ navigation, route }: any) => ({
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
      {!isAppReady ? (
        <Stack.Screen name="Splash" component={SplashScreen} />
      ) : userRole === null ? (
        <>
          <Stack.Screen name="SignIn" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
        </>
      ) : (
        <>
          {/* Main app — accessible by ALL roles including visitor */}
          <Stack.Screen name="MainApp" component={MainTabNavigator} />

          {/* Shared screens */}
          <Stack.Screen name="ProductDetail" component={ProductDetailScreen} />
          <Stack.Screen name="Language" component={LanguageScreen} />
          <Stack.Screen name="ARPreferences" component={ARPreferencesScreen} />

          {/* Logged-in user screens (user, store, admin) */}
          {userRole !== 'visitor' && (
            <>
              <Stack.Screen name="EditProfile" component={EditProfileScreen} />
              <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
            </>
          )}

          {/* User-only: Favorites */}
          {userRole === 'user' && (
            <Stack.Screen name="Favorites" component={FavoritesScreen} />
          )}

          {/* Store: Manage own models + statistics */}
          {(userRole === 'store' || userRole === 'admin') && (
            <>
              <Stack.Screen
                name="ManageModels"
                component={ManageModelsScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen
                name="ManageAddModel"
                component={ManageAddModelScreen}
                options={{ headerShown: false }}
              />
            </>
          )}

          {/* Admin: Full system management */}
          {userRole === 'admin' && (
            <>
              <Stack.Screen
                name="AdminDashboard"
                component={AdminDashboardScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen
                name="ManageUsers"
                component={ManageUsersScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen
                name="ManageCategories"
                component={ManageCategoriesScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen
                name="SystemLogs"
                component={SystemLogsScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen
                name="SystemStats"
                component={SystemStatsScreen}
                options={{ headerShown: false }}
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
    paddingBottom: 0,
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
