import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  ListRenderItem,
  SafeAreaView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

// Import Components ที่เราทำไว้
import SearchButton from '../SearchButton';
import FilterButton from '../FilterButton';
import AddEditButton from '../AddEditButton';

interface Props<T> {
  // --- Header ---
  title: string;
  onBackPress: () => void;

  // --- Data & List ---
  data: T[];
  renderItem: ListRenderItem<T>; // ฟังก์ชันแปลงข้อมูลเป็น UniversalCard
  loading?: boolean;
  emptyText?: string; // ข้อความตอนไม่มีข้อมูล

  // --- Search & Filter (Optional) ---
  searchText?: string;
  onSearchChange?: (text: string) => void;
  filterContent?: React.ReactNode; // เนื้อหาที่จะใส่ใน Filter Modal
  onFilterApply?: () => void;
  onFilterReset?: () => void;
  activeFilterCount?: number;

  // --- Add Button ---
  addButtonTitle?: string;
  onAddPress: () => void;
}

export default function ManageLayout<T>({
  title,
  onBackPress,
  data,
  renderItem,
  loading = false,
  emptyText = 'No items found',
  searchText,
  onSearchChange,
  filterContent,
  onFilterApply,
  onFilterReset,
  activeFilterCount,
  addButtonTitle = 'Add New',
  onAddPress,
}: Props<T>) {
  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        {/* 1. Header Row */}
        <View style={styles.header}>
          <TouchableOpacity onPress={onBackPress} style={styles.backBtn}>
            <Ionicons name="arrow-back" size={24} color={COLORS.text} />
          </TouchableOpacity>
          <Text style={styles.title}>{title}</Text>
        </View>

        {/* 2. Toolbar (Search & Filter) */}
        {/* แสดงเฉพาะเมื่อมีการส่ง onSearchChange หรือ filterContent มา */}
        {(onSearchChange || filterContent) && (
          <View style={styles.toolbar}>
            {onSearchChange && searchText !== undefined && (
              <SearchButton
                value={searchText}
                onChangeText={onSearchChange}
                placeholder={`Search ${title}...`}
              />
            )}

            {filterContent && onFilterApply && (
              <FilterButton
                onApply={onFilterApply}
                onReset={onFilterReset}
                activeCount={activeFilterCount}
              >
                {filterContent}
              </FilterButton>
            )}
          </View>
        )}

        {/* 3. Data List */}
        {loading ? (
          <View style={styles.center}>
            <ActivityIndicator size="large" color={COLORS.primary} />
          </View>
        ) : (
          <FlatList
            data={data}
            renderItem={renderItem}
            keyExtractor={(item: any, index) =>
              item.id?.toString() || index.toString()
            }
            contentContainerStyle={styles.listContent}
            ListEmptyComponent={
              <View style={styles.centerEmpty}>
                <Ionicons
                  name="file-tray-outline"
                  size={48}
                  color={COLORS.textDim}
                />
                <Text style={styles.emptyText}>{emptyText}</Text>
              </View>
            }
          />
        )}

        {/* 4. Floating Add Button */}
        <AddEditButton title={addButtonTitle} onPress={onAddPress} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  backBtn: {
    marginRight: 16,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: COLORS.text,
  },
  toolbar: {
    flexDirection: 'row',
    padding: 16,
    gap: 12,
    justifyContent: 'flex-end', // ให้ปุ่ม Search/Filter ชิดขวา
    alignItems: 'center',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingTop: 8,
    // สำคัญ: เว้นที่ข้างล่างเยอะๆ เพื่อไม่ให้ปุ่ม Add บัง List อันสุดท้าย
    paddingBottom: 100,
  },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  centerEmpty: {
    marginTop: 60,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    marginTop: 12,
    color: COLORS.textDim,
    fontSize: 16,
  },
});
