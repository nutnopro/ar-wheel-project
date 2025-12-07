// src/components/ARCamera.tsx
import React, { useEffect, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { Camera, useCameraDevices } from 'react-native-vision-camera';
import RNFS from 'react-native-fs';
import Ionicons from '@react-native-vector-icons/Ionicons';
import { useTheme } from '../contexts/ThemeContext';

type Props = {
  selectedWheel?: any;
  onCapture?: (path: string) => void;
  onClose?: () => void;
};

export default function ARCamera({ selectedWheel, onCapture, onClose }: Props) {
  const devices = useCameraDevices();
  const device = devices.back;
  const camRef = useRef<Camera>(null);
  const { colors } = useTheme();
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);

  useEffect(() => {
    (async () => {
      const status = await Camera.requestCameraPermission();
      setHasPermission(status === 'authorized');
    })();
  }, []);

  const takePhoto = async () => {
    try {
      const photo = await camRef.current?.takePhoto({
        qualityPrioritization: 'quality',
      });
      if (photo?.path) {
        const dest = `${RNFS.DocumentDirectoryPath}/AR_${Date.now()}.jpg`;
        await RNFS.copyFile(photo.path, dest);
        onCapture?.(dest);
        Alert.alert('Saved', 'Photo saved to app folder');
      }
    } catch (e) {
      console.warn(e);
      Alert.alert('Error', 'Cannot take photo');
    }
  };

  if (hasPermission === null)
    return (
      <View style={styles.center}>
        <Text>Checking permission...</Text>
      </View>
    );
  if (!hasPermission)
    return (
      <View style={styles.center}>
        <Text>Camera permission required</Text>
        <TouchableOpacity
          style={[styles.btn, { backgroundColor: colors.primary }]}
          onPress={() =>
            Camera.requestCameraPermission().then(s =>
              setHasPermission(s === 'authorized'),
            )
          }
        >
          <Text style={{ color: '#fff' }}>Grant</Text>
        </TouchableOpacity>
      </View>
    );

  return (
    <View style={styles.container}>
      {device ? (
        <Camera
          ref={camRef}
          style={StyleSheet.absoluteFill}
          device={device}
          isActive={true}
          photo={true}
        />
      ) : (
        <View style={styles.center}>
          <Text>No camera device</Text>
        </View>
      )}

      <View style={styles.bottomControls}>
        <TouchableOpacity style={styles.captureBtn} onPress={takePhoto}>
          <View style={styles.captureInner} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  bottomControls: {
    position: 'absolute',
    bottom: 40,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  captureBtn: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
  },
  captureInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#667eea',
  },
  btn: { padding: 12, borderRadius: 12 },
});
