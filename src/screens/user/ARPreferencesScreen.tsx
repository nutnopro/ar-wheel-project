import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../context/ThemeContext';
import Header from '../../components/Header';

const ARPreferencesScreen = () => {
  const { theme } = useTheme();

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="AR Preferences" />
      <View style={styles.content}>
        <Text style={{ color: theme.text, fontSize: 16 }}>
          AR preferences settings will be available here soon.
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
});

export default ARPreferencesScreen;
