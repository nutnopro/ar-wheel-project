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
import api from '../../services/api';

import Header from '../../components/Header';

const ManageCategoriesScreen = () => {
  const {theme} = useTheme();
  const [cats, setCats] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [modalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [newCategory, setNewCategory] = useState({
    name: '',
    description: '',
    icon: 'shape',
  });

  const fetchCategories = useCallback(async () => {
    try {
      const response = await adminService.getCategories();
      setCats(response.data);
    } catch (error: any) {
      console.error('Error fetching categories:', error);
      Alert.alert('Error', 'Failed to load categories');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchCategories();
  };

  const handleDelete = (id: string, name: string) => {
    Alert.alert('Confirm', `Delete category "${name}"?`, [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await adminService.deleteCategory(id);
            setCats(prev => prev.filter(c => c.id !== id && c._id !== id));
            Alert.alert('Success', 'Category deleted');
          } catch (error) {
            Alert.alert('Error', 'Failed to delete category');
          }
        },
      },
    ]);
  };

  const handleAddCategory = async () => {
    if (!newCategory.name.trim()) {
      Alert.alert('Validation Error', 'Category name is required.');
      return;
    }

    setSaving(true);
    try {
      await adminService.createCategory({
        name: newCategory.name,
        description: newCategory.description || '',
        icon: newCategory.icon || 'shape',
        isActive: true,
      });

      Alert.alert('Success', 'Category added successfully');
      setModalVisible(false);
      setNewCategory({ name: '', description: '', icon: 'shape' });
      fetchCategories();
    } catch (error: any) {
      Alert.alert('Error', 'Failed to create category: ' + (error.response?.data?.message || error.message));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View
        style={[
          styles.loadingContainer,
          {backgroundColor: theme.background},
        ]}>
        <ActivityIndicator size="large" color="#8B5CF6" />
      </View>
    );
  }

  return (
    <View style={{flex: 1, backgroundColor: theme.background}}>
      <Header title="Manage Categories" />
      <FlatList
        data={cats}
        keyExtractor={item => item.id?.toString() || item._id?.toString()}
        contentContainerStyle={{padding: 20, paddingBottom: 100}}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={['#8B5CF6']}
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Icon name="shape-outline" size={64} color={theme.subText} />
            <Text style={[styles.emptyText, {color: theme.subText}]}>
              No categories found
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
              <View style={[styles.iconBox, {backgroundColor: '#F3E8FF'}]}>
                <Icon name="shape" size={24} color="#8B5CF6" />
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
                <Text style={{color: theme.subText}}>
                  {item.count != null ? `${item.count} items` : ''}
                </Text>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => Alert.alert('Edit', item.name)}
                  disabled={isDeleted}
                  style={styles.actionBtn}>
                  <Icon
                    name="pencil-outline"
                    size={24}
                    color={isDeleted ? theme.subText : '#F59E0B'}
                  />
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleDelete(item.id || item._id, item.name)}
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
          style={[styles.addButton, {backgroundColor: '#8B5CF6'}]}
          onPress={() => setModalVisible(true)}>
          <Icon name="plus" size={24} color="#fff" />
          <Text style={styles.addText}>Add Category</Text>
        </TouchableOpacity>
      </View>

      {/* Add Category Modal */}
      <Modal
        visible={modalVisible}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setModalVisible(false)}
      >
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setModalVisible(false)}>
          <TouchableOpacity activeOpacity={1} onPress={e => e.stopPropagation()} style={[styles.modalContent, { backgroundColor: theme.card }]}>
            <Text style={[styles.modalTitle, { color: theme.text }]}>Add New Category</Text>

            <ScrollView showsVerticalScrollIndicator={false} style={{ width: '100%', maxHeight: 400 }}>
               <Text style={[styles.inputLabel, { color: theme.subText }]}>Category Name *</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 placeholder="e.g. SUV, Sedan"
                 placeholderTextColor={theme.subText}
                 value={newCategory.name}
                 onChangeText={v => setNewCategory({ ...newCategory, name: v })}
               />

               <Text style={[styles.inputLabel, { color: theme.subText, marginTop: 15 }]}>Description</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border, height: 80, textAlignVertical: 'top' }]}
                 placeholder="About this category..."
                 placeholderTextColor={theme.subText}
                 value={newCategory.description}
                 onChangeText={v => setNewCategory({ ...newCategory, description: v })}
                 multiline
               />

               <Text style={[styles.inputLabel, { color: theme.subText, marginTop: 15 }]}>Icon Name (MaterialDesign)</Text>
               <TextInput
                 style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                 placeholder="e.g. car, shape, cube"
                 placeholderTextColor={theme.subText}
                 value={newCategory.icon}
                 onChangeText={v => setNewCategory({ ...newCategory, icon: v })}
                 autoCapitalize="none"
               />
            </ScrollView>

            <View style={styles.modalActions}>
              <TouchableOpacity style={[styles.modalButton, styles.cancelButton]} onPress={() => setModalVisible(false)} disabled={saving}>
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalButton, styles.saveButton, { backgroundColor: '#8B5CF6' }]} onPress={handleAddCategory} disabled={saving}>
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
export default ManageCategoriesScreen;
