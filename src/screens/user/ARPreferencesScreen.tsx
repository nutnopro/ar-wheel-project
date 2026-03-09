import React, { useState, useEffect } from 'react';
import { 
  View, 
  Text, 
  StyleSheet, 
  Switch, 
  TouchableOpacity, 
  ScrollView, 
  NativeModules, 
  Alert,
  ActivityIndicator,
  TextInput,
  Image
} from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-aware-scroll-view';
import { useTheme } from '../../context/ThemeContext';
import Header from '../../components/Header';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { storage } from '../../utils/storage';

const { ARLauncher } = NativeModules;

const ARPreferencesScreen = () => {
  const { theme } = useTheme();
  
  const [markers, setMarkers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [markerSize, setMarkerSize] = useState('15'); // default 15cm

  useEffect(() => {
    loadSettings();
    fetchMarkers();
  }, []);

  const loadSettings = async () => {
    try {
      const size = storage?.getString('@ar_marker_size');
      if (size) setMarkerSize(size);
    } catch (e) {
      console.error('Failed to load settings', e);
    }
  };

  const saveSettings = (sizeStr: string) => {
    setMarkerSize(sizeStr);
    try {
      storage?.set('@ar_marker_size', sizeStr);
    } catch (e) {
      console.error('Failed to save settings', e);
    }
  };

  const fetchMarkers = async () => {
    try {
      if (ARLauncher && ARLauncher.getMarkerImages) {
        const result = await ARLauncher.getMarkerImages();
        setMarkers(result);
      } else {
        console.warn('ARLauncher is not available for fetching markers.');
      }
    } catch (e) {
      console.error('Failed to fetch markers', e);
    } finally {
      setLoading(false);
    }
  };

  const saveMarkerToGallery = async (filename: string) => {
    try {
      if (ARLauncher && ARLauncher.saveMarkerImage) {
        const msg = await ARLauncher.saveMarkerImage(filename);
        Alert.alert('Success', msg);
      } else {
        Alert.alert('Error', 'ARLauncher not available.');
      }
    } catch (e: any) {
      Alert.alert('Error', e.message || 'Failed to save image');
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="AR Preferences" showBack />
      
      <KeyboardAwareScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.content}
        enableOnAndroid={true}
        extraScrollHeight={20}
        keyboardShouldPersistTaps="handled"
      >
        
        {/* Settings Section */}
        <View style={[styles.section, { backgroundColor: theme.card, shadowColor: theme.text }]}>   
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Marker Settings</Text>
          
          <View style={styles.settingRow}>
            <View>
              <Text style={{ color: theme.text, fontSize: 16, fontWeight: '500' }}>Real-World Marker Size (cm)</Text>
              <Text style={{ color: theme.subText, fontSize: 13, marginTop: 4 }}>
                Size of the physical paper marker used for AR.
              </Text>
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <TextInput 
                style={[styles.input, { borderColor: theme.border, color: theme.text }]}
                keyboardType="numeric"
                value={markerSize}
                onChangeText={saveSettings}
                maxLength={4}
              />
              <Text style={{ color: theme.subText, marginLeft: 8 }}>cm</Text>
            </View>
          </View>
        </View>

        {/* Markers Section */}
        <View style={[styles.section, { backgroundColor: theme.card, shadowColor: theme.text }]}>   
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Available Target Markers</Text>
          <Text style={{ color: theme.subText, fontSize: 14, marginBottom: 16 }}>
            Print one of these images and place it on the floor to use Marker-based AR.
          </Text>

          {loading ? (
             <ActivityIndicator size="large" color="#2563EB" style={{ marginVertical: 20 }} />
          ) : markers.length === 0 ? (
             <Text style={{ color: theme.subText, textAlign: 'center', marginVertical: 20 }}>
               No markers found on this device.
             </Text>
          ) : (
            <View style={styles.grid}>
              {markers.map((item, index) => (
                <View key={index} style={[styles.markerCard, { borderColor: theme.border }]}>
                  <Image source={{ uri: item.base64 }} style={styles.markerImage} resizeMode="cover" />
                  <View style={styles.markerInfo}>
                    <Text style={[styles.markerName, { color: theme.text }]} numberOfLines={1}>{item.name}</Text>
                    <TouchableOpacity 
                      style={styles.saveBtn} 
                      onPress={() => saveMarkerToGallery(item.name)}
                    >
                      <Icon name="download" size={16} color="#fff" />
                      <Text style={styles.saveBtnText}>Save</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ))}
            </View>
          )}

        </View>
      </KeyboardAwareScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  content: {
    padding: 20,
    paddingBottom: 60,
  },
  section: {
    padding: 20,
    borderRadius: 16,
    marginBottom: 20,
    elevation: 2,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    width: 60,
    textAlign: 'center',
    fontSize: 16,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  markerCard: {
    width: '48%',
    borderWidth: 1,
    borderRadius: 12,
    marginBottom: 16,
    overflow: 'hidden',
  },
  markerImage: {
    width: '100%',
    aspectRatio: 1,
    backgroundColor: '#fff', // White background so markers pop
  },
  markerInfo: {
    padding: 12,
    alignItems: 'center',
  },
  markerName: {
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 8,
  },
  saveBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2563EB',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
  },
  saveBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
    marginLeft: 4,
  },
});

export default ARPreferencesScreen;
