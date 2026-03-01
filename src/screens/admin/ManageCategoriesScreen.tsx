import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';

import Header from '../../components/Header';

const ManageCategoriesScreen = () => {
  const { theme } = useTheme();
  const [cats, setCats] = useState([
    { id: '1', name: 'Sport Wheels', count: 10, status: 'Active' },
    { id: '2', name: 'Luxury Wheels', count: 5, status: 'Active' },
  ]);

  const handleDelete = (id: string) => {
    Alert.alert('Confirm', 'Delete category?', [
      { text: 'Cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () =>
          setCats(prev =>
            prev.map(c => (c.id === id ? { ...c, status: 'Deleted' } : c)),
          ),
      },
    ]);
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="Manage Categories" />
      <FlatList
        data={cats}
        keyExtractor={item => item.id}
        contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
        renderItem={({ item }) => {
          const isDeleted = item.status === 'Deleted';
          return (
            <View
              style={[
                styles.card,
                { backgroundColor: theme.card, opacity: isDeleted ? 0.6 : 1 },
              ]}
            >
              <View style={[styles.iconBox, { backgroundColor: '#F3E8FF' }]}>
                <Icon name="shape" size={24} color="#8B5CF6" />
              </View>
              <View style={{ flex: 1 }}>
                <Text
                  style={[
                    styles.title,
                    {
                      color: theme.text,
                      textDecorationLine: isDeleted ? 'line-through' : 'none',
                    },
                  ]}
                >
                  {item.name}
                </Text>
                <Text style={{ color: theme.subText }}>{item.count} items</Text>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity
                  onPress={() => Alert.alert('Edit', item.name)}
                  disabled={isDeleted}
                  style={styles.actionBtn}
                >
                  <Icon
                    name="pencil-outline"
                    size={24}
                    color={isDeleted ? theme.subText : '#F59E0B'}
                  />
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => handleDelete(item.id)}
                  disabled={isDeleted}
                  style={styles.actionBtn}
                >
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
          { backgroundColor: theme.card, borderTopColor: theme.border },
        ]}
      >
        <TouchableOpacity
          style={[styles.addButton, { backgroundColor: '#8B5CF6' }]}
          onPress={() => Alert.alert('Add', 'Category')}
        >
          <Icon name="plus" size={24} color="#fff" />
          <Text style={styles.addText}>Add Category</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};
const styles = StyleSheet.create({
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
  title: { fontSize: 16, fontWeight: '600' },
  actions: { flexDirection: 'row' },
  actionBtn: { padding: 8 },
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
  addText: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginLeft: 8 },
});
export default ManageCategoriesScreen;
