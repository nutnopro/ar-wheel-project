import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  RefreshControl,
  Dimensions,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { adminService } from '../../services/adminService';
import Header from '../../components/Header';

const { width } = Dimensions.get('window');

const StoreStatsScreen = () => {
  const { theme } = useTheme();
  const { userData } = useAuth();
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchStats = useCallback(async () => {
    if (!userData?.id && !userData?.uid) {
      Alert.alert('Error', 'Store ID not found');
      setLoading(false);
      return;
    }

    try {
      const storeId = userData.id || userData.uid;
      const response = await adminService.getStoreStats(storeId);
      setStats(response.data);
    } catch (error) {
      console.error('Error fetching store stats:', error);
      Alert.alert('Error', 'Failed to load store statistics');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [userData]);

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
        <ActivityIndicator size="large" color="#10B981" />
        <Text style={[styles.loadingText, { color: theme.text }]}>Loading Store Statistics...</Text>
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
      <Header title="Store Statistics" showBack />
      
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#10B981']} />
        }
      >
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Store Overview</Text>
        
        <View style={styles.grid}>
          <View style={styles.gridItem}>
            <StatCard 
              title="Total Models" 
              value={stats?.models?.total || 0} 
              icon="cube-outline" 
              color="#10B981" 
            />
          </View>
          <View style={styles.gridItem}>
            <StatCard 
              title="Total Views" 
              value={stats?.views?.total || 0} 
              icon="eye" 
              color="#3B82F6" 
            />
          </View>
        </View>

        <View style={styles.grid}>
           <View style={styles.gridItem}>
             <StatCard 
               title="Favorites" 
               value={stats?.favorites?.total || 0} 
               icon="heart" 
               color="#EF4444" 
             />
           </View>
           <View style={styles.gridItem}>
             <StatCard 
               title="AR Views" 
               value={stats?.arViews?.total || 0} 
               icon="cube-scan" 
               color="#8B5CF6" 
             />
           </View>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>Recent Activity</Text>
        
        <View style={[styles.fullCard, { backgroundColor: theme.card }]}>
          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>Models Added (This Month)</Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {stats?.models?.thisMonth || 0}
            </Text>
          </View>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          
          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>Views (This Month)</Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {stats?.views?.thisMonth || 0}
            </Text>
          </View>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
             <Text style={[styles.activityLabel, { color: theme.subText }]}>New Favorites (This Month)</Text>
             <Text style={[styles.activityValue, { color: theme.text }]}>
               {stats?.favorites?.thisMonth || 0}
             </Text>
          </View>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
             <Text style={[styles.activityLabel, { color: theme.subText }]}>AR Views (This Month)</Text>
             <Text style={[styles.activityValue, { color: theme.text }]}>
               {stats?.arViews?.thisMonth || 0}
             </Text>
          </View>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>Top Models</Text>
        
        <View style={[styles.fullCard, { backgroundColor: theme.card }]}>
          {stats?.topModels?.length > 0 ? (
            stats.topModels.map((model: any, index: number) => (
              <View key={model.id}>
                <View style={styles.activityRow}>
                  <Text style={[styles.activityLabel, { color: theme.text, flex: 1 }]}>
                    {index + 1}. {model.name}
                  </Text>
                  <Text style={[styles.activityValue, { color: theme.text }]}>
                    {model.views || 0} views
                  </Text>
                </View>
                {index < stats.topModels.length - 1 && (
                  <View style={[styles.divider, { backgroundColor: theme.border }]} />
                )}
              </View>
            ))
          ) : (
            <Text style={[styles.activityLabel, { color: theme.subText, textAlign: 'center', paddingVertical: 20 }]}>
              No models available
            </Text>
          )}
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

export default StoreStatsScreen;
