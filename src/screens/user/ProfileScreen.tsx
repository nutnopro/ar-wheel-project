import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Image,
  TouchableOpacity,
  ScrollView,
  Switch,
  SafeAreaView,
  StatusBar,
  Alert,
  Platform,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';

const ProfileScreen = () => {
  const navigation = useNavigation<any>();
  const { isDarkMode, toggleTheme, theme } = useTheme();
  const { userRole, userData, logout } = useAuth();

  const isVisitor = userRole === 'visitor';
  const isUser = userRole === 'user';
  const isStore = userRole === 'store';
  const isAdmin = userRole === 'admin';

  const getProfileImage = () => {
    const imgUrl = userData?.profileImg || userData?.profileImageUrl;
    if (!imgUrl) {
      return 'https://cdn-icons-png.flaticon.com/512/149/149071.png';
    }
    const separator = imgUrl.includes('?') ? '&' : '?';
    return `${imgUrl}${separator}t=${new Date().getTime()}`;
  };

  const user = {
    displayName: userData?.displayName || 'Guest User',
    email: userData?.email || 'Sign in to access features',
    profileImg: userData?.profileImg || 'https://cdn-icons-png.flaticon.com/512/149/149071.png',
  };
  const handleRestrictedAction = () => {
    Alert.alert('Login Required', 'Please login to use this feature.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Login', onPress: () => logout() },
    ]);
  };

  const MenuItem = ({
    icon,
    title,
    onPress,
    isSwitch = false,
    value = false,
    onToggle,
    restricted = false,
    titleColor,
    iconColor,
  }: any) => (
    <TouchableOpacity
      style={[styles.menuItem, { borderBottomColor: theme.border }]}
      onPress={
        isSwitch
          ? undefined
          : restricted && isVisitor
          ? handleRestrictedAction
          : onPress
      }
      activeOpacity={isSwitch ? 1 : 0.7}
    >
      <View style={styles.menuLeft}>
        <View
          style={[
            styles.iconBox,
            { backgroundColor: isDarkMode ? '#1E293B' : '#EFF6FF' },
          ]}
        >
          <Icon
            name={icon}
            size={22}
            color={
              iconColor
                ? iconColor
                : restricted && isVisitor
                ? '#CBD5E1'
                : theme.icon
            }
          />
        </View>
        <Text
          style={[
            styles.menuText,
            {
              color: titleColor
                ? titleColor
                : restricted && isVisitor
                ? '#CBD5E1'
                : theme.text,
            },
          ]}
        >
          {title}
        </Text>
        {restricted && isVisitor && (
          <Icon
            name="lock"
            size={14}
            color="#CBD5E1"
            style={{ marginLeft: 8 }}
          />
        )}
      </View>
      {isSwitch ? (
        <Switch
          value={value}
          onValueChange={onToggle}
          trackColor={{ false: '#767577', true: '#2563EB' }}
          thumbColor={'#f4f3f4'}
        />
      ) : (
        <Icon
          name="chevron-right"
          size={24}
          color={restricted && isVisitor ? '#CBD5E1' : theme.subText}
        />
      )}
    </TouchableOpacity>
  );

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={theme.background}
      />
      <SafeAreaView
        style={[styles.safeArea, { backgroundColor: theme.background }]}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={styles.header}>
            <Image
              key={user.profileImg}
              source={{ 
                uri: user.profileImg,
                cache: 'reload'
              }}
              style={styles.avatar}
            />
            <Text style={[styles.name, { color: theme.text }]}>
              {user.displayName || 'Guest User'}
            </Text>
            <Text style={styles.email}>
              {isAdmin ? 'Administrator' : isStore ? 'Store' : user.email || ''}
            </Text>
            {!isVisitor && (
              <TouchableOpacity
                style={styles.editBadge}
                onPress={() => navigation.navigate('EditProfile')}
              >
                <Icon name="pencil" size={14} color="#fff" />
              </TouchableOpacity>
            )}
          </View>

          {/* Visitor: Login/Register buttons */}
          {isVisitor && (
            <TouchableOpacity style={styles.visitorLoginBox} onPress={logout}>
              <Text style={styles.visitorLoginText}>Sign In / Register</Text>
            </TouchableOpacity>
          )}

          {/* Admin: System Management */}
          {isAdmin && (
            <View
              style={[
                styles.section,
                {
                  backgroundColor: theme.card,
                  borderColor: '#2563EB',
                  borderWidth: 1,
                },
              ]}
            >
              <Text style={[styles.sectionTitle, { color: '#2563EB' }]}>
                System Management
              </Text>
              <MenuItem
                icon="account-group"
                title="Manage Users"
                onPress={() => navigation.navigate('ManageUsers')}
              />
              <MenuItem
                icon="cube"
                title="Manage Models"
                onPress={() => navigation.navigate('ManageModels')}
              />
              <MenuItem
                icon="shape"
                title="Manage Categories"
                onPress={() => navigation.navigate('ManageCategories')}
              />
              <MenuItem
                icon="file-document-outline"
                title="System Logs"
                onPress={() => navigation.navigate('SystemLogs')}
              />
              <MenuItem
                icon="chart-bar"
                title="System Statistics"
                onPress={() => navigation.navigate('AdminDashboard')}
              />
            </View>
          )}

          {/* Store: Store Management */}
          {isStore && (
            <View
              style={[
                styles.section,
                {
                  backgroundColor: theme.card,
                  borderColor: '#10B981',
                  borderWidth: 1,
                },
              ]}
            >
              <Text style={[styles.sectionTitle, { color: '#10B981' }]}>
                Store Management
              </Text>
              <MenuItem
                icon="cube"
                title="Manage Models"
                onPress={() => navigation.navigate('ManageModels')}
              />
              <MenuItem
                icon="chart-bar"
                title="Store Statistics"
                onPress={() => navigation.navigate('AdminDashboard')}
              />
            </View>
          )}

          {/* Account Section */}
          <View
            style={[
              styles.section,
              {
                backgroundColor: theme.card,
                borderColor: '#2563EB',
                borderWidth: 1,
              },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: '#2563EB' }]}>Account</Text>

            {/* Favorites — user role only */}
            {isUser && (
              <MenuItem
                icon="heart-outline"
                title="Favorites"
                onPress={() => navigation.navigate('Favorites')}
              />
            )}

            {/* Change Password — all logged-in roles */}
            {!isVisitor && (
              <MenuItem
                icon="lock-outline"
                title="Change Password"
                onPress={() => navigation.navigate('ChangePassword')}
              />
            )}

            <MenuItem
              icon="translate"
              title="Language"
              onPress={() => navigation.navigate('Language')}
            />
            <MenuItem
              icon="theme-light-dark"
              title="Dark Mode"
              isSwitch={true}
              value={isDarkMode}
              onToggle={toggleTheme}
            />
            <MenuItem
              icon="cube-scan"
              title="AR Configuration"
              onPress={() => navigation.navigate('ARPreferences')}
            />
          </View>

          {/* Extra spacing to prevent overlap with tab bar */}
          <View style={{ height: 20 }} />

          <TouchableOpacity
            style={[styles.logoutButton, { marginBottom: 40 }]}
            onPress={() => logout()}
          >
            <Icon
              name="logout"
              size={20}
              color="#EF4444"
              style={{ marginRight: 8 }}
            />
            <Text style={styles.logoutText}>
              {isVisitor ? 'Exit Visitor Mode' : 'Log Out'}
            </Text>
          </TouchableOpacity>

          <View style={{ height: 30 }} />
        </ScrollView>
      </SafeAreaView>
    </View>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight! + 10 : 0,
  },
  scrollContent: { padding: 20, paddingBottom: 140 },
  header: { alignItems: 'center', marginBottom: 20, marginTop: 10 },
  avatar: {
    width: 100,
    height: 100,
    borderRadius: 50,
    marginBottom: 15,
    backgroundColor: '#ddd',
  },
  editBadge: {
    position: 'absolute',
    bottom: 65,
    right: '35%',
    backgroundColor: '#2563EB',
    padding: 6,
    borderRadius: 15,
    borderWidth: 2,
    borderColor: '#fff',
  },
  name: { fontSize: 22, fontWeight: 'bold', marginBottom: 4 },
  email: { fontSize: 14, color: '#94A3B8' },
  visitorLoginBox: {
    backgroundColor: '#EFF6FF',
    padding: 15,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#DBEAFE',
  },
  visitorLoginText: { color: '#2563EB', fontWeight: 'bold', fontSize: 16 },
  section: {
    borderRadius: 16,
    padding: 5,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 2,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#94A3B8',
    marginTop: 15,
    marginLeft: 15,
    marginBottom: 5,
    textTransform: 'uppercase',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 15,
    paddingHorizontal: 15,
    borderBottomWidth: 1,
  },
  menuLeft: { flexDirection: 'row', alignItems: 'center' },
  iconBox: {
    width: 36,
    height: 36,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  menuText: { fontSize: 16, fontWeight: '500' },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FEF2F2',
    paddingVertical: 16,
    borderRadius: 16,
    marginTop: 10,
  },
  logoutText: { color: '#EF4444', fontSize: 16, fontWeight: 'bold' },
});

export default ProfileScreen;
