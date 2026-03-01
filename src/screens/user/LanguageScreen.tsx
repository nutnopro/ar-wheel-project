import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from '../../context/ThemeContext';
import { useLanguage } from '../../context/LanguageContext';

import Header from '../../components/Header';

const LanguageScreen = () => {
  const { theme } = useTheme();
  const { language, changeLanguage, t } = useLanguage();

  const LanguageOption = ({ langCode, label, flag }: any) => (
    <TouchableOpacity
      style={[
        styles.option,
        {
          backgroundColor: theme.card,
          borderColor: language === langCode ? '#2563EB' : theme.border
        }
      ]}
      onPress={() => changeLanguage(langCode)}
      activeOpacity={0.7}
    >
      <View style={styles.left}>
        {/* ใส่ Icon ธง หรือตัวอักษรย่อก็ได้ */}
        <View style={styles.flagBox}>
          <Text style={styles.flagText}>{flag}</Text>
        </View>
        <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      </View>

      {language === langCode && (
        <Icon name="check-circle" size={24} color="#2563EB" />
      )}
    </TouchableOpacity>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Header title={t.select_language} />
      <Text style={[styles.headerTitle, { color: theme.subText }]}>{t.select_language}</Text>

      <LanguageOption
        langCode="en"
        label={t.lang_english}
        flag="EN"
      />

      <LanguageOption
        langCode="th"
        label={t.lang_thai}
        flag="TH"
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20 },
  headerTitle: { fontSize: 14, fontWeight: '600', marginBottom: 15, textTransform: 'uppercase' },
  option: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 16, borderRadius: 12, marginBottom: 12, borderWidth: 1
  },
  left: { flexDirection: 'row', alignItems: 'center' },
  flagBox: {
    width: 40, height: 40, borderRadius: 20, backgroundColor: '#EFF6FF',
    justifyContent: 'center', alignItems: 'center', marginRight: 15
  },
  flagText: { fontWeight: 'bold', color: '#2563EB' },
  label: { fontSize: 16, fontWeight: '500' }
});

export default LanguageScreen;