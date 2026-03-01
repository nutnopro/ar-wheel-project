import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  Modal,
  RefreshControl,
  TextInput,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import api from '../../services/api';

interface User {
  id: string;
  uid?: string;
  username: string;
  email: string;
  role: 'visitor' | 'user' | 'store' | 'admin';
  status?: string;
  isActive?: boolean;
}

const ROLES = [
  { value: 'visitor', label: 'Visitor', color: '#94A3B8' },
  { value: 'user', label: 'User', color: '#3B82F6' },
  { value: 'store', label: 'Store', color: '#F59E0B' },
  { value: 'admin', label: 'Admin', color: '#EF4444' },
];

import Header from '../../components/Header';

const ManageUsersScreen = () => {
  const { theme } = useTheme();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [selectedRole, setSelectedRole] = useState<string>('');

  // Fetch users from backend
  const fetchUsers = useCallback(async () => {
    try {
      const response = await api.get('/users');
      const usersData = response.data.map((user: any) => ({
        id: user.id || user.uid,
        uid: user.id || user.uid,
        username: user.username || 'Unknown',
        email: user.email || 'No email',
        role: user.role || 'user',
        status: user.status || 'active',
        isActive: user.isActive !== false,
      }));
      setUsers(usersData);
    } catch (error: any) {
      console.error('Error fetching users:', error);
      Alert.alert('Error', 'Failed to load users: ' + error.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchUsers();
  };

  const handleEditRole = (user: User) => {
    setSelectedUser(user);
    setSelectedRole(user.role);
    setModalVisible(true);
  };

  const handleUpdateRole = async () => {
    if (!selectedUser || !selectedRole) return;

    try {
      const response = await api.patch(`/users/${selectedUser.uid}/role`, {
        role: selectedRole,
      });

      Alert.alert('Success', `User role updated to ${selectedRole}`);

      // Update local state
      setUsers(prev =>
        prev.map(u =>
          u.uid === selectedUser.uid ? { ...u, role: selectedRole as any } : u,
        ),
      );

      setModalVisible(false);
      setSelectedUser(null);
    } catch (error: any) {
      console.error('Error updating role:', error);
      Alert.alert('Error', 'Failed to update role: ' + error.message);
    }
  };

  const getRoleColor = (role: string) => {
    const roleObj = ROLES.find(r => r.value === role);
    return roleObj?.color || '#94A3B8';
  };

  const getRoleLabel = (role: string) => {
    const roleObj = ROLES.find(r => r.value === role);
    return roleObj?.label || role;
  };

  if (loading) {
    return (
      <View
        style={[styles.loadingContainer, { backgroundColor: theme.background }]}
      >
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={[styles.loadingText, { color: theme.text }]}>
          Loading users...
        </Text>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="Manage Users" />
      <FlatList
        data={users}
        keyExtractor={item => item.uid || item.id}
        contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={['#2563EB']}
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Icon name="account-off" size={64} color={theme.subText} />
            <Text style={[styles.emptyText, { color: theme.subText }]}>
              No users found
            </Text>
          </View>
        }
        renderItem={({ item }) => {
          const isDeleted =
            item.status === 'deleted' || item.status === 'Deleted';
          const isInactive = item.isActive === false;
          const disabled = isDeleted || isInactive;

          return (
            <View
              style={[
                styles.card,
                { backgroundColor: theme.card, opacity: disabled ? 0.6 : 1 },
              ]}
            >
              <View style={[styles.iconBox, { backgroundColor: '#EFF6FF' }]}>
                <Icon name="account" size={24} color="#2563EB" />
              </View>
              <View style={{ flex: 1 }}>
                <Text
                  style={[
                    styles.title,
                    {
                      color: theme.text,
                      textDecorationLine: disabled ? 'line-through' : 'none',
                    },
                  ]}
                >
                  {item.username}
                </Text>
                <Text
                  style={{ color: theme.subText, fontSize: 12, marginTop: 2 }}
                >
                  {item.email}
                </Text>
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    marginTop: 4,
                  }}
                >
                  <View
                    style={[
                      styles.roleBadge,
                      { backgroundColor: getRoleColor(item.role) + '20' },
                    ]}
                  >
                    <Text
                      style={{
                        color: getRoleColor(item.role),
                        fontSize: 11,
                        fontWeight: '600',
                      }}
                    >
                      {getRoleLabel(item.role)}
                    </Text>
                  </View>
                  <Text
                    style={{
                      color: disabled ? '#EF4444' : '#10B981',
                      fontSize: 11,
                      marginLeft: 8,
                    }}
                  >
                    • {item.status || 'active'}
                  </Text>
                </View>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => handleEditRole(item)}
                  disabled={disabled}
                  style={styles.actionBtn}
                >
                  <Icon
                    name="shield-edit-outline"
                    size={24}
                    color={disabled ? theme.subText : '#2563EB'}
                  />
                </TouchableOpacity>
              </View>
            </View>
          );
        }}
      />

      {/* Role Edit Modal */}
      <Modal
        visible={modalVisible}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setModalVisible(false)}
      >
        <TouchableOpacity
          style={styles.modalOverlay}
          activeOpacity={1}
          onPress={() => setModalVisible(false)}
        >
          <TouchableOpacity
            activeOpacity={1}
            onPress={e => e.stopPropagation()}
          >
            <View
              style={[styles.modalContent, { backgroundColor: theme.card }]}
            >
              <Text style={[styles.modalTitle, { color: theme.text }]}>
                Change User Role
              </Text>
              <Text style={[styles.modalSubtitle, { color: theme.subText }]}>
                {selectedUser?.username} ({selectedUser?.email})
              </Text>

              <View style={styles.roleOptions}>
                {ROLES.map(role => (
                  <TouchableOpacity
                    key={role.value}
                    style={[
                      styles.roleOption,
                      {
                        borderColor:
                          selectedRole === role.value
                            ? role.color
                            : theme.border,
                        backgroundColor:
                          selectedRole === role.value
                            ? role.color + '10'
                            : 'transparent',
                      },
                    ]}
                    onPress={() => setSelectedRole(role.value)}
                  >
                    <Icon
                      name={
                        selectedRole === role.value
                          ? 'radiobox-marked'
                          : 'radiobox-blank'
                      }
                      size={24}
                      color={
                        selectedRole === role.value ? role.color : theme.subText
                      }
                    />
                    <Text
                      style={[
                        styles.roleOptionText,
                        {
                          color:
                            selectedRole === role.value
                              ? role.color
                              : theme.text,
                          fontWeight:
                            selectedRole === role.value ? 'bold' : 'normal',
                        },
                      ]}
                    >
                      {role.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <View style={styles.modalActions}>
                <TouchableOpacity
                  style={[styles.modalButton, styles.cancelButton]}
                  onPress={() => setModalVisible(false)}
                >
                  <Text style={styles.cancelButtonText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.modalButton, styles.saveButton]}
                  onPress={handleUpdateRole}
                >
                  <Text style={styles.saveButtonText}>Save Changes</Text>
                </TouchableOpacity>
              </View>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  loadingText: { marginTop: 12, fontSize: 16 },
  emptyContainer: { alignItems: 'center', marginTop: 50 },
  emptyText: { marginTop: 16, fontSize: 16 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    marginBottom: 12,
    borderRadius: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  title: { fontSize: 16, fontWeight: '600' },
  roleBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  actions: { flexDirection: 'row' },
  actionBtn: { padding: 8 },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    width: '85%',
    borderRadius: 16,
    padding: 24,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
  },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 8 },
  modalSubtitle: { fontSize: 14, marginBottom: 20 },
  roleOptions: { marginBottom: 24 },
  roleOption: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    borderRadius: 10,
    borderWidth: 2,
    marginBottom: 10,
  },
  roleOptionText: { fontSize: 16, marginLeft: 12 },
  modalActions: { flexDirection: 'row', justifyContent: 'space-between' },
  modalButton: { flex: 1, padding: 14, borderRadius: 10, alignItems: 'center' },
  cancelButton: { backgroundColor: '#F1F5F9', marginRight: 8 },
  cancelButtonText: { color: '#64748B', fontWeight: '600', fontSize: 16 },
  saveButton: { backgroundColor: '#2563EB', marginLeft: 8 },
  saveButtonText: { color: '#fff', fontWeight: '600', fontSize: 16 },
});

export default ManageUsersScreen;
