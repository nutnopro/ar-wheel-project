import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import api from '../../services/api';

interface Log {
  id: string;
  action: string;
  userId?: string;
  timestamp?: any;
  details?: string;
  oldRole?: string;
  newRole?: string;
  createAt?: any;
}

import Header from '../../components/Header';

const SystemLogsScreen = () => {
  const { theme } = useTheme();
  const [logs, setLogs] = useState<Log[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Fetch logs from backend
  const fetchLogs = useCallback(async () => {
    try {
      const response = await api.get('/logs');
      setLogs(response.data || []);
    } catch (error: any) {
      console.error('Error fetching logs:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchLogs();

    // Auto-refresh every 10 seconds
    const interval = setInterval(() => {
      fetchLogs();
    }, 10000);

    return () => clearInterval(interval);
  }, [fetchLogs]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchLogs();
  };

  const formatTimestamp = (timestamp: any) => {
    if (!timestamp) return 'Unknown time';

    try {
      let date: Date;

      // Handle Firestore Timestamp
      if (timestamp._seconds) {
        date = new Date(timestamp._seconds * 1000);
      }
      // Handle Firebase Timestamp with toDate method
      else if (timestamp.toDate && typeof timestamp.toDate === 'function') {
        date = timestamp.toDate();
      }
      // Handle regular Date object or string
      else {
        date = new Date(timestamp);
      }

      const now = new Date();
      const diff = now.getTime() - date.getTime();
      const seconds = Math.floor(diff / 1000);
      const minutes = Math.floor(seconds / 60);
      const hours = Math.floor(minutes / 60);
      const days = Math.floor(hours / 24);

      if (days > 0) return `${days}d ago`;
      if (hours > 0) return `${hours}h ago`;
      if (minutes > 0) return `${minutes}m ago`;
      return 'Just now';
    } catch (error) {
      return 'Invalid date';
    }
  };

  const getActionIcon = (action: string) => {
    if (action.includes('REGISTER')) return 'account-plus';
    if (action.includes('LOGIN')) return 'login';
    if (action.includes('ROLE')) return 'shield-edit';
    if (action.includes('UPDATE')) return 'pencil';
    if (action.includes('DELETE')) return 'delete';
    return 'information';
  };

  const getActionColor = (action: string) => {
    if (action.includes('REGISTER')) return '#10B981';
    if (action.includes('LOGIN')) return '#3B82F6';
    if (action.includes('ROLE')) return '#F59E0B';
    if (action.includes('UPDATE')) return '#8B5CF6';
    if (action.includes('DELETE')) return '#EF4444';
    return '#64748B';
  };

  if (loading) {
    return (
      <View
        style={[styles.loadingContainer, { backgroundColor: theme.background }]}
      >
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={[styles.loadingText, { color: theme.text }]}>
          Loading logs...
        </Text>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="System Logs" />
      <FlatList
        data={logs}
        keyExtractor={(item, index) => item.id || `log-${index}`}
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
            <Icon
              name="file-document-outline"
              size={64}
              color={theme.subText}
            />
            <Text style={[styles.emptyText, { color: theme.subText }]}>
              No logs available
            </Text>
          </View>
        }
        renderItem={({ item }) => {
          const actionColor = getActionColor(item.action);
          const timestamp = item.timestamp || item.createAt;

          return (
            <View
              style={[
                styles.card,
                { backgroundColor: theme.card, borderLeftColor: actionColor },
              ]}
            >
              <View
                style={[
                  styles.iconBox,
                  { backgroundColor: actionColor + '15' },
                ]}
              >
                <Icon
                  name={getActionIcon(item.action)}
                  size={22}
                  color={actionColor}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.actionText, { color: theme.text }]}>
                  {item.action.replace(/_/g, ' ')}
                </Text>
                {item.details && (
                  <Text style={[styles.detailsText, { color: theme.subText }]}>
                    {item.details}
                  </Text>
                )}
                {item.userId && (
                  <Text style={[styles.userIdText, { color: theme.subText }]}>
                    User ID: {item.userId.substring(0, 8)}...
                  </Text>
                )}
                <Text style={[styles.timeText, { color: theme.subText }]}>
                  {formatTimestamp(timestamp)}
                </Text>
              </View>
            </View>
          );
        }}
      />

      {/* Auto-refresh indicator */}
      <View style={[styles.refreshIndicator, { backgroundColor: theme.card }]}>
        <Icon name="autorenew" size={14} color="#10B981" />
        <Text style={styles.refreshText}>Auto-refreshing every 10s</Text>
      </View>
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
    alignItems: 'flex-start',
    padding: 16,
    marginBottom: 12,
    borderRadius: 12,
    elevation: 2,
    borderLeftWidth: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
  },
  iconBox: {
    width: 40,
    height: 40,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  actionText: { fontWeight: 'bold', fontSize: 15, marginBottom: 4 },
  detailsText: { fontSize: 13, marginBottom: 4 },
  userIdText: { fontSize: 11, marginBottom: 2 },
  timeText: { fontSize: 11, marginTop: 4 },
  refreshIndicator: {
    position: 'absolute',
    bottom: 20,
    right: 20,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  refreshText: {
    fontSize: 11,
    color: '#10B981',
    marginLeft: 6,
    fontWeight: '600',
  },
});

export default SystemLogsScreen;
