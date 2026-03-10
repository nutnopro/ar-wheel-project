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

    const storeId = userData?.storeId;

    if (!storeId) {
      console.log('Store ID not found, userData:', userData);

      Alert.alert(
        'Error',
        'Store ID not found. Please login with store account.'
      );

      setLoading(false);
      return;
    }

    console.log('Fetching stats for store:', storeId);

    try {

      const response = await adminService.getStoreStats(storeId);

      console.log('Store stats response:', response);

      const statsData =
        response?.data?.data ||
        response?.data ||
        {};

      setStats(statsData);

    } catch (error: any) {

      console.error('Error fetching store stats:', error);

      const errorMessage =
        error?.response?.data?.message ||
        error?.message ||
        'Failed to load store statistics';

      Alert.alert('Error', errorMessage);

      setStats({
        models: { total: 0, thisMonth: 0 },
        views: { total: 0, thisMonth: 0 },
        favorites: { total: 0, thisMonth: 0 },
        arViews: { total: 0, thisMonth: 0 },
        topModels: []
      });

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

  const safeStats = stats || {
    models: { total: 0, thisMonth: 0 },
    views: { total: 0, thisMonth: 0 },
    favorites: { total: 0, thisMonth: 0 },
    arViews: { total: 0, thisMonth: 0 },
    topModels: []
  };

  if (loading) {
    return (
      <View style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color="#10B981" />
        <Text style={[styles.loadingText, { color: theme.text }]}>
          Loading Store Statistics...
        </Text>
      </View>
    );
  }

  const StatCard = ({ title, value, icon, color }: any) => (
    <View style={[styles.card, { backgroundColor: theme.card }]}>
      <View style={[styles.iconBox, { backgroundColor: color + '15' }]}>
        <Icon name={icon} size={28} color={color} />
      </View>

      <View style={{ flex: 1, marginLeft: 16 }}>
        <Text style={[styles.statTitle, { color: theme.subText }]}>
          {title}
        </Text>

        <Text style={[styles.statValue, { color: theme.text }]}>
          {value}
        </Text>
      </View>
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>

      <Header title="Store Statistics" showBack />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={['#10B981']}
          />
        }
      >

        <Text style={[styles.sectionTitle, { color: theme.text }]}>
          Store Overview
        </Text>

        <View style={styles.grid}>
          <View style={styles.gridItem}>
            <StatCard
              title="Total Models"
              value={safeStats.models.total}
              icon="cube-outline"
              color="#10B981"
            />
          </View>

          <View style={styles.gridItem}>
            <StatCard
              title="Total Views"
              value={safeStats.views.total}
              icon="eye"
              color="#3B82F6"
            />
          </View>
        </View>

        <View style={styles.grid}>
          <View style={styles.gridItem}>
            <StatCard
              title="Favorites"
              value={safeStats.favorites.total}
              icon="heart"
              color="#EF4444"
            />
          </View>

          <View style={styles.gridItem}>
            <StatCard
              title="AR Views"
              value={safeStats.arViews.total}
              icon="cube-scan"
              color="#8B5CF6"
            />
          </View>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>
          Recent Activity
        </Text>

        <View style={[styles.fullCard, { backgroundColor: theme.card }]}>

          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>
              Models Added (This Month)
            </Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {safeStats.models.thisMonth}
            </Text>
          </View>

          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>
              Views (This Month)
            </Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {safeStats.views.thisMonth}
            </Text>
          </View>

          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>
              New Favorites (This Month)
            </Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {safeStats.favorites.thisMonth}
            </Text>
          </View>

          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.activityRow}>
            <Text style={[styles.activityLabel, { color: theme.subText }]}>
              AR Views (This Month)
            </Text>
            <Text style={[styles.activityValue, { color: theme.text }]}>
              {safeStats.arViews.thisMonth}
            </Text>
          </View>

        </View>

        <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>
          Top Models
        </Text>

        <View style={[styles.fullCard, { backgroundColor: theme.card }]}>

          {safeStats.topModels.length > 0 ? (

            safeStats.topModels.map((model: any, index: number) => (

              <View key={model.id || model._id}>

                <View style={styles.activityRow}>
                  <Text style={[styles.activityLabel, { color: theme.text, flex: 1 }]}>
                    {index + 1}. {model.name}
                  </Text>

                  <Text style={[styles.activityValue, { color: theme.text }]}>
                    {model.views || 0} views
                  </Text>
                </View>

                {index < safeStats.topModels.length - 1 && (
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

  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center'
  },

  loadingText: {
    marginTop: 12,
    fontSize: 16
  },

  scrollContent: {
    padding: 20,
    paddingBottom: 60
  },

  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16
  },

  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16
  },

  gridItem: {
    width: (width - 60) / 2
  },

  card: {
    padding: 16,
    borderRadius: 16,
    elevation: 2,
    flexDirection: 'row',
    alignItems: 'center'
  },

  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center'
  },

  statTitle: {
    fontSize: 13,
    marginBottom: 4
  },

  statValue: {
    fontSize: 20,
    fontWeight: 'bold'
  },

  fullCard: {
    padding: 20,
    borderRadius: 16,
    elevation: 2
  },

  activityRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12
  },

  activityLabel: {
    fontSize: 15
  },

  activityValue: {
    fontSize: 16,
    fontWeight: 'bold'
  },

  divider: {
    height: 1,
    width: '100%'
  }

});

export default StoreStatsScreen;