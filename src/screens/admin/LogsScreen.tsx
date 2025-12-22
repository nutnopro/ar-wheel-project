// src/screens/admin/LogsScreen.tsx
import React from 'react';
import { View, Text, FlatList, StyleSheet, SafeAreaView } from 'react-native';
import SearchBar from '../../components/SearchBar';
import { COLORS } from '../../theme/colors';

const LOGS = [
  { id: 1, action: 'Admin A deleted Model X', time: '10:00 AM', role: 'Admin' },
  { id: 2, action: 'Store B updated Model Y', time: '09:30 AM', role: 'Store' },
];

export default function LogsScreen({ navigation }: any) {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: COLORS.background }}>
      <View style={{ padding: 16 }}>
        <SearchBar placeholder="Search logs..." />
        <View style={{ flexDirection: 'row', marginTop: 10, gap: 10 }}>
          <View style={styles.pill}>
            <Text>All Roles</Text>
          </View>
          <View style={styles.pill}>
            <Text>Today</Text>
          </View>
        </View>
      </View>

      <FlatList
        data={LOGS}
        keyExtractor={i => i.id.toString()}
        renderItem={({ item }) => (
          <View style={styles.logCard}>
            <Text style={styles.logAction}>{item.action}</Text>
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                marginTop: 8,
              }}
            >
              <Text style={styles.logRole}>{item.role}</Text>
              <Text style={{ color: COLORS.textDim }}>{item.time}</Text>
            </View>
          </View>
        )}
        contentContainerStyle={{ padding: 16 }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  pill: {
    backgroundColor: '#e0e0e0',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  logCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    marginBottom: 10,
  },
  logAction: { fontSize: 16, fontWeight: '500' },
  logRole: { color: COLORS.primary, fontWeight: 'bold', fontSize: 12 },
});
