import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
  Image,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import DocumentPicker, { DocumentPickerResponse } from 'react-native-document-picker';
import { useTheme } from '../../context/ThemeContext';
import { useNavigation } from '@react-navigation/native';
import { productService } from '../../services/productService';
import Header from '../../components/Header';

const ManageAddModelScreen = () => {
  const { theme } = useTheme();
  const navigation = useNavigation<any>();

  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    price: '',
    brand: '',
    description: '',
    categories: '',
  });

  const [modelFile, setModelFile] = useState<DocumentPickerResponse | null>(null);
  const [imageFiles, setImageFiles] = useState<DocumentPickerResponse[]>([]);

  const handlePickModel = async () => {
    try {
      const res = await DocumentPicker.pickSingle({
        type: [DocumentPicker.types.allFiles],
      });
      console.log(res);
      // Validate extension
      const ext = res.name?.split('.').pop()?.toLowerCase();
      if (ext !== 'glb' && ext !== 'usdz') {
        Alert.alert('Invalid File', 'Please select a .glb or .usdz file for the 3D model.');
        return;
      }
      setModelFile(res);
    } catch (err) {
      if (!DocumentPicker.isCancel(err)) {
        console.error(err);
        Alert.alert('Error', 'Failed to pick model file');
      }
    }
  };

  const handlePickImages = async () => {
    try {
      const results = await DocumentPicker.pick({
        allowMultiSelection: true,
        type: [DocumentPicker.types.images],
      });
      setImageFiles(prev => [...prev, ...results]);
    } catch (err) {
      if (!DocumentPicker.isCancel(err)) {
        console.error(err);
        Alert.alert('Error', 'Failed to pick image files');
      }
    }
  };

  const handleSubmit = async () => {
    if (!formData.name || !formData.price || !modelFile || imageFiles.length === 0) {
      Alert.alert('Validation Check', 'Please fill name, price, select a model file, and at least 1 image.');
      return;
    }

    try {
      setLoading(true);

      const payload = {
        name: formData.name,
        price: Number(formData.price),
        brand: formData.brand,
        description: formData.description,
        categories: formData.categories ? formData.categories.split(',').map(c => c.trim()) : [],
      };

      const modelFileObj = {
        uri: modelFile.uri,
        type: modelFile.type || 'application/octet-stream',
        name: modelFile.name,
      };

      const primaryImageObj = {
        uri: imageFiles[0].uri,
        type: imageFiles[0].type || 'image/jpeg',
        name: imageFiles[0].name,
      };

      // Create model and upload first file and image directly
      const response = await productService.createWithFile(payload, modelFileObj, primaryImageObj);
      const newModelId = response.data?.id;

      // Upload remaining images if any
      if (newModelId && imageFiles.length > 1) {
        for (let i = 1; i < imageFiles.length; i++) {
          const extraImg = {
            uri: imageFiles[i].uri,
            type: imageFiles[i].type || 'image/jpeg',
            name: imageFiles[i].name,
          };
          await productService.uploadFile(newModelId, extraImg);
        }
      }

      Alert.alert('Success', 'Model created successfully', [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (error) {
      console.error(error);
      Alert.alert('Error', 'Failed to create model');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title="Add New Model" showBack />
      
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Form Fields */}
        <View style={[styles.card, { backgroundColor: theme.card }]}>
          <Text style={[styles.label, { color: theme.text }]}>Model Name *</Text>
          <TextInput
            style={[styles.input, { color: theme.text, borderColor: theme.border }]}
            placeholder="e.g. BBS RS"
            placeholderTextColor={theme.subText}
            value={formData.name}
            onChangeText={t => setFormData({ ...formData, name: t })}
          />

          <Text style={[styles.label, { color: theme.text }]}>Price (฿) *</Text>
          <TextInput
            style={[styles.input, { color: theme.text, borderColor: theme.border }]}
            placeholder="e.g. 15000"
            placeholderTextColor={theme.subText}
            keyboardType="numeric"
            value={formData.price}
            onChangeText={t => setFormData({ ...formData, price: t })}
          />

          <Text style={[styles.label, { color: theme.text }]}>Brand</Text>
          <TextInput
            style={[styles.input, { color: theme.text, borderColor: theme.border }]}
            placeholder="e.g. BBS"
            placeholderTextColor={theme.subText}
            value={formData.brand}
            onChangeText={t => setFormData({ ...formData, brand: t })}
          />

          <Text style={[styles.label, { color: theme.text }]}>Description</Text>
          <TextInput
            style={[styles.input, styles.textArea, { color: theme.text, borderColor: theme.border }]}
            placeholder="Details about the wheel..."
            placeholderTextColor={theme.subText}
            multiline
            numberOfLines={4}
            value={formData.description}
            onChangeText={t => setFormData({ ...formData, description: t })}
          />

          <Text style={[styles.label, { color: theme.text }]}>Categories (comma separated)</Text>
          <TextInput
            style={[styles.input, { color: theme.text, borderColor: theme.border }]}
            placeholder="e.g. Sport, Luxury"
            placeholderTextColor={theme.subText}
            value={formData.categories}
            onChangeText={t => setFormData({ ...formData, categories: t })}
          />
        </View>

        {/* File Uploads */}
        <View style={[styles.card, { backgroundColor: theme.card }]}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>3D Model File *</Text>
          <TouchableOpacity
            style={[styles.uploadBox, { borderColor: theme.border, backgroundColor: theme.background }]}
            onPress={handlePickModel}
          >
            <Icon name="cube-outline" size={32} color={modelFile ? '#10B981' : theme.subText} />
            <Text style={{ color: modelFile ? '#10B981' : theme.subText, marginTop: 8 }}>
              {modelFile ? modelFile.name : 'Select .GLB or .USDZ File'}
            </Text>
          </TouchableOpacity>

          <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>Images *</Text>
          <TouchableOpacity
            style={[styles.uploadBox, { borderColor: theme.border, backgroundColor: theme.background }]}
            onPress={handlePickImages}
          >
            <Icon name="image-plus" size={32} color={theme.subText} />
            <Text style={{ color: theme.subText, marginTop: 8 }}>Select Images (Multi)</Text>
          </TouchableOpacity>

          {imageFiles.length > 0 && (
            <ScrollView horizontal style={styles.imageScroll} showsHorizontalScrollIndicator={false}>
              {imageFiles.map((img, idx) => (
                <View key={idx.toString()} style={styles.imagePreviewWrapper}>
                  <Image source={{ uri: img.uri }} style={styles.imagePreview} />
                  <TouchableOpacity
                    style={styles.removeImageBtn}
                    onPress={() => setImageFiles(prev => prev.filter((_, i) => i !== idx))}
                  >
                    <Icon name="close" size={16} color="#fff" />
                  </TouchableOpacity>
                </View>
              ))}
            </ScrollView>
          )}
        </View>

        {/* Submit Button */}
        <TouchableOpacity
          style={[styles.submitBtn, { opacity: loading ? 0.7 : 1 }]}
          onPress={handleSubmit}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.submitText}>Upload Model</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  scrollContent: {
    padding: 20,
    paddingBottom: 60,
  },
  card: {
    borderRadius: 16,
    padding: 16,
    marginBottom: 20,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
  },
  label: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 8,
    marginTop: 12,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  textArea: {
    textAlignVertical: 'top',
    height: 100,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
  },
  uploadBox: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  imageScroll: {
    marginTop: 16,
    flexDirection: 'row',
  },
  imagePreviewWrapper: {
    position: 'relative',
    marginRight: 12,
  },
  imagePreview: {
    width: 80,
    height: 80,
    borderRadius: 8,
  },
  removeImageBtn: {
    position: 'absolute',
    top: -6,
    right: -6,
    backgroundColor: '#EF4444',
    borderRadius: 12,
    width: 24,
    height: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  submitBtn: {
    backgroundColor: '#2563EB',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
  },
  submitText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default ManageAddModelScreen;
