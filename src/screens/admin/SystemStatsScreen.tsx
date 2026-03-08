import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  RefreshControl,
  Dimensions,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { adminService } from '../../services/adminService';
import Header from '../../components/Header';

const { width } = Dimensions.get('window');

const SystemStatsScreen = () => {
  const { theme } = useTheme();
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchStats = useCallback(async () => {
    try {
      const response = await adminService.getSystemStats();
      setStats(response.data);
    } catch (error) {
      console.error('Error fetching system stats:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchStats();
  };

  if (loading) {
    return (
      <View style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={[styles.loadingText, { color: theme.text }]}>Loading Statistics...</Text>
      </View>
    );
  }

  const StatCard = ({ title, value, icon, color }: any) => (
    <View style={[styles.card, { backgroundColor: theme.card }]}>
      <View style={[styles.iconBox, { backgroundColor: color + '15' }]}>
        <Icon name={icon} size={28} color={color} />
      </View>
      <View style={{ flex: 1, marginLeft: 16 }}>
        <Text style={[styles.statTitle, { color: theme.subText }]}>{title}</Text>
        <Text style={[styles.statValue, { color: theme.text }]}>{value}</Text>
      </View>
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="System Statistics" showBack />
      
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#2563EB']} />
        }
      >
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Overview</Text>
        
        <View style={styles.grid}>
          <View style={styles.gridItem}>
            <StatCard 
              title="Total Users" 
              value={stats?.users?.total || 0} 
              icon="account-group" 
              color="#3B82F6" 
            />
          </View>
          <View style={styles.gridItem}>
            <StatCard 
              title="Total Models" 
              value={stats?.models?.total || 0} 
              icon="cube-outline" 
              color="#10B981" 
            />
          </View>
        </View>

        <View style={styles.grid}>
           <View style={styles.gridItem}>
             <StatCard 
               title="Active Sessions" 
               value={stats?.system?.activeSessions || 0} 
               icon="cellphone-link" 
               color="#F59E0B" 
             />
           </View>
           <View style={styles.gridItem}>
             <StatCard 
               title="Total Categories" 
               value={stats?.categories?.total || 0} 
               icon="shape" 
               color="#8B5CF6" 
             />
           </View>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>Recent Activity</Text>
        
        <View style={[styles.fullCard, { backgroundColor: theme.card }]}>
          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>New Signups (Today)</Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {stats?.users?.newToday || 0}
            </Text>
          </View>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          
          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>Models Added (Today)</Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {stats?.models?.newToday || 0}
            </Text>
          </View>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
             <Text style={[styles.activityLabel, { color: theme.subText }]}>Storage Used</Text>
             <Text style={[styles.activityValue, { color: theme.text }]}>
               {stats?.system?.storageUsed || 'Unknown'}
             </Text>
          </View>
        </View>

      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  loadingText: { marginTop: 12, fontSize: 16 },
  scrollContent: { padding: 20, paddingBottom: 60 },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  gridItem: {
    width: (width - 60) / 2, // 20 padding on each side = 40, plus 20 space between
  },
  card: {
    padding: 16,
    borderRadius: 16,
    elevation: 2,
    flexDirection: 'row',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
  },
  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  statTitle: { fontSize: 13, marginBottom: 4 },
  statValue: { fontSize: 20, fontWeight: 'bold' },
  fullCard: {
    padding: 20,
    borderRadius: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
  },
  activityRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  activityLabel: { fontSize: 15 },
  activityValue: { fontSize: 16, fontWeight: 'bold' },
  divider: { height: 1, width: '100%' },
});

export default SystemStatsScreen;
