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
  ScrollView,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import api from '../../services/api';
import Header from '../../components/Header';

interface User {
  id: string;
  uid?: string;
  username: string;
  email: string;
  role: 'visitor' | 'user' | 'store' | 'admin';
  status?: string;
  phoneNumber?: string;
}

const ROLES = [
  { value: 'visitor', label: 'Visitor', color: '#94A3B8' },
  { value: 'user', label: 'User', color: '#3B82F6' },
  { value: 'store', label: 'Store', color: '#F59E0B' },
  { value: 'admin', label: 'Admin', color: '#EF4444' },
];

const ManageUsersScreen = () => {
  const { theme } = useTheme();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  // Edit Form State
  const [editFormData, setEditFormData] = useState({
    displayName: '',
    email: '',
    phoneNumber: '',
    role: '',
  });

  const [saving, setSaving] = useState(false);

  const fetchUsers = useCallback(async () => {
    try {
      const response = await api.get('/users');
      const usersData = response.data.map((user: any) => ({
        id: user.id || user.uid,
        uid: user.id || user.uid,
        username: user.displayName || user.username || 'Unknown',
        email: user.email || 'No email',
        role: user.role || 'user',
        status: user.status || 'active',
        phoneNumber: user.phoneNumber || '',
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

  const handleEditOpen = (user: User) => {
    setSelectedUser(user);
    setEditFormData({
      displayName: user.username,
      email: user.email,
      phoneNumber: user.phoneNumber || '',
      role: user.role,
    });
    setModalVisible(true);
  };

  const handleUpdateUser = async () => {
    if (!selectedUser) return;
    setSaving(true);
    try {
      await api.patch(`/users/profile/${selectedUser.uid}`, {
        displayName: editFormData.displayName,
        email: editFormData.email,
        phoneNumber: editFormData.phoneNumber,
        role: editFormData.role,
      });

      Alert.alert('Success', 'User profile updated successfully.');
      setModalVisible(false);
      fetchUsers();
    } catch (error: any) {
      console.error('Error updating user:', error);
      Alert.alert('Error', 'Failed to update user: ' + error.message);
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteUser = (user: User) => {
    Alert.alert(
      'Delete User',
      `Are you sure you want to permanently delete ${user.email}? This action cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
             setLoading(true);
             try {
                await api.delete(`/users/${user.uid}`);
                Alert.alert('Success', 'User deleted successfully.');
                fetchUsers();
             } catch (error: any) {
                setLoading(false);
                Alert.alert('Error', 'Failed to delete user: ' + error.message);
             }
          },
        },
      ]
    );
  };

  const getRoleColor = (role: string) => ROLES.find(r => r.value === role)?.color || '#94A3B8';
  const getRoleLabel = (role: string) => ROLES.find(r => r.value === role)?.label || role;

  if (loading) {
    return (
      <View style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={[styles.loadingText, { color: theme.text }]}>Loading users...</Text>
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
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#2563EB']} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Icon name="account-off" size={64} color={theme.subText} />
            <Text style={[styles.emptyText, { color: theme.subText }]}>No users found</Text>
          </View>
        }
        renderItem={({ item }) => {
          const isDeleted = item.status === 'deleted' || item.status === 'Deleted';
          const disabled = isDeleted;

          return (
            <View style={[styles.card, { backgroundColor: theme.card, opacity: disabled ? 0.6 : 1 }]}>
              <View style={[styles.iconBox, { backgroundColor: getRoleColor(item.role) + '20' }]}>
                <Icon
                  name={item.role === 'admin' ? 'shield-account' : item.role === 'store' ? 'store' : 'account'}
                  size={24}
                  color={getRoleColor(item.role)}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.title, { color: theme.text, textDecorationLine: disabled ? 'line-through' : 'none' }]}>
                  {item.username}
                </Text>
                <Text style={{ color: theme.subText, fontSize: 12, marginTop: 2 }}>{item.email}</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}>
                  <Text style={{ color: getRoleColor(item.role), fontSize: 11, fontWeight: '600' }}>
                    {getRoleLabel(item.role).toUpperCase()}
                  </Text>
                  <Text style={{ color: disabled ? '#EF4444' : '#10B981', fontSize: 11, marginLeft: 8 }}>
                    • {disabled ? 'Deleted' : 'Active'}
                  </Text>
                </View>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity onPress={() => handleEditOpen(item)} disabled={disabled} style={styles.actionBtn}>
                  <Icon name="pencil" size={22} color={disabled ? theme.subText : '#2563EB'} />
                </TouchableOpacity>
                <TouchableOpacity onPress={() => handleDeleteUser(item)} disabled={disabled} style={styles.actionBtn}>
                  <Icon name="delete" size={22} color={disabled ? theme.subText : '#EF4444'} />
                </TouchableOpacity>
              </View>
            </View>
          );
        }}
      />

      {/* Edit User Modal */}
      <Modal
        visible={modalVisible}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setModalVisible(false)}
      >
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setModalVisible(false)}>
          <TouchableOpacity activeOpacity={1} onPress={e => e.stopPropagation()} style={[styles.modalContent, { backgroundColor: theme.card }]}>
            <Text style={[styles.modalTitle, { color: theme.text }]}>Edit User</Text>
            
            <ScrollView showsVerticalScrollIndicator={false} style={{ width: '100%', maxHeight: 400 }}>
               <Text style={[styles.inputLabel, { color: theme.subText }]}>Display Name</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 value={editFormData.displayName}
                 onChangeText={v => setEditFormData({ ...editFormData, displayName: v })}
               />

               <Text style={[styles.inputLabel, { color: theme.subText }]}>Email</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 value={editFormData.email}
                 onChangeText={v => setEditFormData({ ...editFormData, email: v })}
                 keyboardType="email-address"
                 autoCapitalize="none"
               />

               <Text style={[styles.inputLabel, { color: theme.subText }]}>Phone Number</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 value={editFormData.phoneNumber}
                 onChangeText={v => setEditFormData({ ...editFormData, phoneNumber: v })}
                 keyboardType="phone-pad"
               />

               <Text style={[styles.inputLabel, { color: theme.subText, marginTop: 10 }]}>Role</Text>
               <View style={styles.roleOptions}>
                 {ROLES.map(role => (
                   <TouchableOpacity
                     key={role.value}
                     style={[
                       styles.roleOption,
                       {
                         borderColor: editFormData.role === role.value ? role.color : theme.border,
                         backgroundColor: editFormData.role === role.value ? role.color + '10' : 'transparent',
                       },
                     ]}
                     onPress={() => setEditFormData({ ...editFormData, role: role.value })}
                   >
                     <Icon
                       name={editFormData.role === role.value ? 'radiobox-marked' : 'radiobox-blank'}
                       size={20}
                       color={editFormData.role === role.value ? role.color : theme.subText}
                     />
                     <Text style={[styles.roleOptionText, { color: editFormData.role === role.value ? role.color : theme.text }]}>
                       {role.label}
                     </Text>
                   </TouchableOpacity>
                 ))}
               </View>
            </ScrollView>

            <View style={styles.modalActions}>
              <TouchableOpacity style={[styles.modalButton, styles.cancelButton]} onPress={() => setModalVisible(false)} disabled={saving}>
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalButton, styles.saveButton]} onPress={handleUpdateUser} disabled={saving}>
                <Text style={styles.saveButtonText}>{saving ? 'Saving...' : 'Save'}</Text>
              </TouchableOpacity>
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
  actions: { flexDirection: 'row' },
  actionBtn: { padding: 8 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center' },
  modalContent: { width: '90%', borderRadius: 16, padding: 24, elevation: 5, maxHeight: '80%' },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 15, textAlign: 'center' },
  inputLabel: { fontSize: 13, marginBottom: 5, marginTop: 10 },
  input: { borderWidth: 1, borderRadius: 8, padding: 12, fontSize: 15, marginBottom: 5 },
  roleOptions: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  roleOption: { flexDirection: 'row', alignItems: 'center', padding: 12, borderRadius: 8, borderWidth: 1, marginBottom: 10, width: '48%' },
  roleOptionText: { fontSize: 14, marginLeft: 8 },
  modalActions: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 15 },
  modalButton: { flex: 1, padding: 14, borderRadius: 10, alignItems: 'center' },
  cancelButton: { backgroundColor: '#F1F5F9', marginRight: 8 },
  cancelButtonText: { color: '#64748B', fontWeight: '600', fontSize: 16 },
  saveButton: { backgroundColor: '#2563EB', marginLeft: 8 },
  saveButtonText: { color: '#fff', fontWeight: '600', fontSize: 16 },
});

export default ManageUsersScreen;
