import React, {useState, useEffect, useCallback} from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  RefreshControl,
  Modal,
  TextInput,
  ScrollView,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import {useTheme} from '../../context/ThemeContext';
import {adminService} from '../../services/adminService';

import Header from '../../components/Header';

const ManageStoresScreen = () => {
  const {theme} = useTheme();
  const [stores, setStores] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [modalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [newStore, setNewStore] = useState({
    name: '',
    location: '',
    description: '',
  });

  const fetchStores = useCallback(async () => {
    try {
      const response = await adminService.getStores();
      setStores(response.data);
    } catch (error: any) {
      console.error('Error fetching stores:', error);
      Alert.alert('Error', 'Failed to load stores');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchStores();
  }, [fetchStores]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchStores();
  };

  const handleAddStore = async () => {
    if (!newStore.name.trim()) {
      Alert.alert('Validation Error', 'Store name is required.');
      return;
    }

    setSaving(true);
    try {
      await adminService.createStore({
        name: newStore.name,
        location: newStore.location || '',
        description: newStore.description || '',
        isActive: true,
      });

      Alert.alert('Success', 'Store added successfully');
      setModalVisible(false);
      setNewStore({ name: '', location: '', description: '' });
      fetchStores();
    } catch (error: any) {
      Alert.alert('Error', 'Failed to create store: ' + (error.response?.data?.message || error.message));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (id: string) => {
    Alert.alert('Confirm', 'Delete store?', [
      {text: 'Cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await adminService.deleteStore(id);
            setStores(prev => prev.filter(s => s.id !== id));
            Alert.alert('Success', 'Store deleted');
          } catch (error) {
            Alert.alert('Error', 'Failed to delete store');
          }
        },
      },
    ]);
  };

  if (loading) {
    return (
      <View
        style={[
          styles.loadingContainer,
          {backgroundColor: theme.background},
        ]}>
        <ActivityIndicator size="large" color="#10B981" />
      </View>
    );
  }

  return (
    <View style={{flex: 1, backgroundColor: theme.background}}>
      <Header title="Manage Stores" />
      <FlatList
        data={stores}
        keyExtractor={item => item.id?.toString() || item._id?.toString()}
        contentContainerStyle={{padding: 20, paddingBottom: 100}}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={['#10B981']}
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Icon name="store-off" size={64} color={theme.subText} />
            <Text style={[styles.emptyText, {color: theme.subText}]}>
              No stores found
            </Text>
          </View>
        }
        renderItem={({item}) => {
          const isDeleted = item.status === 'Deleted';
          return (
            <View
              style={[
                styles.card,
                {backgroundColor: theme.card, opacity: isDeleted ? 0.6 : 1},
              ]}>
              <View style={[styles.iconBox, {backgroundColor: '#ECFDF5'}]}>
                <Icon name="store" size={24} color="#10B981" />
              </View>
              <View style={{flex: 1}}>
                <Text
                  style={[
                    styles.title,
                    {
                      color: theme.text,
                      textDecorationLine: isDeleted ? 'line-through' : 'none',
                    },
                  ]}>
                  {item.name}
                </Text>
                <Text style={{color: theme.subText}}>{item.location}</Text>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => Alert.alert('Edit', item.name)}
                  disabled={isDeleted}
                  style={styles.actionBtn}>
                  <Icon
                    name="pencil-outline"
                    size={24}
                    color={isDeleted ? theme.subText : '#3B82F6'}
                  />
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleDelete(item.id || item._id)}
                  disabled={isDeleted}
                  style={styles.actionBtn}>
                  <Icon
                    name="trash-can-outline"
                    size={24}
                    color={isDeleted ? theme.subText : '#EF4444'}
                  />
                </TouchableOpacity>
              </View>
            </View>
          );
        }}
      />
      <View
        style={[
          styles.footer,
          {backgroundColor: theme.card, borderTopColor: theme.border},
        ]}>
        <TouchableOpacity
          style={[styles.addButton, {backgroundColor: '#10B981'}]}
          onPress={() => setModalVisible(true)}>
          <Icon name="plus" size={24} color="#fff" />
          <Text style={styles.addText}>Add Store</Text>
        </TouchableOpacity>
      </View>

      {/* Add Store Modal */}
      <Modal
        visible={modalVisible}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setModalVisible(false)}
      >
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setModalVisible(false)}>
          <TouchableOpacity activeOpacity={1} onPress={e => e.stopPropagation()} style={[styles.modalContent, { backgroundColor: theme.card }]}>
            <Text style={[styles.modalTitle, { color: theme.text }]}>Add New Store</Text>

            <ScrollView showsVerticalScrollIndicator={false} style={{ width: '100%', maxHeight: 400 }}>
               <Text style={[styles.inputLabel, { color: theme.subText }]}>Store Name *</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 placeholder="e.g. Auto Parts Store"
                 placeholderTextColor={theme.subText}
                 value={newStore.name}
                 onChangeText={v => setNewStore({ ...newStore, name: v })}
               />

               <Text style={[styles.inputLabel, { color: theme.subText, marginTop: 15 }]}>Location</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 placeholder="e.g. Bangkok, Thailand"
                 placeholderTextColor={theme.subText}
                 value={newStore.location}
                 onChangeText={v => setNewStore({ ...newStore, location: v })}
               />

               <Text style={[styles.inputLabel, { color: theme.subText, marginTop: 15 }]}>Description</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border, height: 80, textAlignVertical: 'top' }]}
                 placeholder="About this store..."
                 placeholderTextColor={theme.subText}
                 value={newStore.description}
                 onChangeText={v => setNewStore({ ...newStore, description: v })}
                 multiline
               />
            </ScrollView>

            <View style={styles.modalActions}>
              <TouchableOpacity style={[styles.modalButton, styles.cancelButton]} onPress={() => setModalVisible(false)} disabled={saving}>
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalButton, styles.saveButton, { backgroundColor: '#10B981' }]} onPress={handleAddStore} disabled={saving}>
                <Text style={styles.saveButtonText}>{saving ? 'Saving...' : 'Create'}</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  loadingContainer: {flex: 1, justifyContent: 'center', alignItems: 'center'},
  emptyContainer: {alignItems: 'center', marginTop: 50},
  emptyText: {marginTop: 16, fontSize: 16},
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    marginBottom: 12,
    borderRadius: 12,
    elevation: 2,
  },
  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  title: {fontSize: 16, fontWeight: '600'},
  actions: {flexDirection: 'row'},
  actionBtn: {padding: 8},
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 20,
    paddingBottom: 40,
    borderTopWidth: 1,
  },
  addButton: {
    backgroundColor: '#2563EB',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
  },
  addText: {color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 8},

  // Modal Styles
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center' },
  modalContent: { width: '90%', borderRadius: 16, padding: 24, elevation: 5, maxHeight: '80%' },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 15, textAlign: 'center' },
  inputLabel: { fontSize: 13, marginBottom: 5, marginTop: 10 },
  input: { borderWidth: 1, borderRadius: 8, padding: 12, fontSize: 15, marginBottom: 5 },
  modalActions: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 15 },
  modalButton: { flex: 1, padding: 14, borderRadius: 10, alignItems: 'center' },
  cancelButton: { backgroundColor: '#F1F5F9', marginRight: 8 },
  cancelButtonText: { color: '#64748B', fontWeight: '600', fontSize: 16 },
  saveButton: { marginLeft: 8 },
  saveButtonText: { color: '#fff', fontWeight: '600', fontSize: 16 },
});
export default ManageStoresScreen;
