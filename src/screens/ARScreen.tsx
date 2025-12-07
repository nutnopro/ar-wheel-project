// src/screens/ARScreen.tsx
import React, { useState } from 'react';
import { View, StyleSheet, Modal } from 'react-native';
import ARCamera from '../components/ARCamera';
import { useTheme } from '../contexts/ThemeContext';

export default function ARScreen() {
  const { colors } = useTheme();
  const [showCam, setShowCam] = useState(true);

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      <Modal visible={showCam} animationType="slide">
        <ARCamera
          onCapture={p => {
            console.log('captured', p);
            setShowCam(false);
          }}
          onClose={() => setShowCam(false)}
        />
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({ container: { flex: 1 } });
