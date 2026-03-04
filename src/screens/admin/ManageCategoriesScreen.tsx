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
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import {useTheme} from '../../context/ThemeContext';
import {adminService} from '../../services/adminService';

import Header from '../../components/Header';

const ManageCategoriesScreen = () => {
  const {theme} = useTheme();
  const [cats, setCats] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

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

  const handleDelete = (id: string) => {
    Alert.alert('Confirm', 'Delete category?', [
      {text: 'Cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await adminService.deleteCategory(id);
            setCats(prev => prev.filter(c => c.id !== id));
            Alert.alert('Success', 'Category deleted');
          } catch (error) {
            Alert.alert('Error', 'Failed to delete category');
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
          style={[styles.addButton, {backgroundColor: '#8B5CF6'}]}
          onPress={() => Alert.alert('Add', 'Category')}>
          <Icon name="plus" size={24} color="#fff" />
          <Text style={styles.addText}>Add Category</Text>
        </TouchableOpacity>
      </View>
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
});
export default ManageCategoriesScreen;
