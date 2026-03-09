import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Image,
  Alert,
} from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-aware-scroll-view';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { pick, types, isErrorWithCode, errorCodes, DocumentPickerResponse } from '@react-native-documents/picker';
import { useTheme } from '../../context/ThemeContext';
import { useNavigation, useRoute } from '@react-navigation/native';
import { productService } from '../../services/productService';
import Header from '../../components/Header';

const ManageAddModelScreen = () => {
  const { theme } = useTheme();
  const navigation = useNavigation<any>();
  const route = useRoute<any>();

  const editModel = route.params?.modelInfo;
  const isEditMode = !!editModel;

  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    price: '',
    brand: '',
    description: '',
    categories: '',
    size: '',
    width: '',
    offset: '',
    pcd: '',
  });

  const [glbFile, setGlbFile] = useState<DocumentPickerResponse | null>(null);
  const [usdzFile, setUsdzFile] = useState<DocumentPickerResponse | null>(null);
  const [imageFiles, setImageFiles] = useState<DocumentPickerResponse[]>([]);

  useEffect(() => {
    if (isEditMode) {
      setFormData({
        name: editModel.name || '',
        price: editModel.price ? String(editModel.price) : '',
        brand: editModel.brand || '',
        description: editModel.description || '',
        categories: editModel.categories ? editModel.categories.join(', ') : '',
        size: editModel.size || '',
        width: editModel.width || '',
        offset: editModel.offset || '',
        pcd: editModel.pcd || '',
      });
    }
  }, [editModel]);

  const handlePickGlb = async () => {
    try {
      const [res] = await pick({
        type: [types.allFiles],
      });
      console.log(res);
      // Validate extension
      const ext = res.name?.split('.').pop()?.toLowerCase();
      if (ext !== 'glb') {
        Alert.alert('Invalid File', 'Please select a .glb file.');
        return;
      }
      setGlbFile(res);
    } catch (err) {
      if (!(isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED)) {
        console.error(err);
        Alert.alert('Error', 'Failed to pick model file');
      }
    }
  };

  const handlePickUsdz = async () => {
    try {
      const [res] = await pick({
        type: [types.allFiles],
      });
      // Validate extension
      const ext = res.name?.split('.').pop()?.toLowerCase();
      if (ext !== 'usdz') {
        Alert.alert('Invalid File', 'Please select a .usdz file.');
        return;
      }
      setUsdzFile(res);
    } catch (err) {
      if (!(isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED)) {
        console.error(err);
        Alert.alert('Error', 'Failed to pick usdz file');
      }
    }
  };

  const handlePickImages = async () => {
    try {
      const results = await pick({
        allowMultiSelection: true,
        type: [types.images],
      });
      setImageFiles(prev => [...prev, ...results]);
    } catch (err) {
      if (!(isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED)) {
        console.error(err);
        Alert.alert('Error', 'Failed to pick image files');
      }
    }
  };

  const handleSubmit = async () => {
    if (!formData.name || !formData.price || (!isEditMode && (!glbFile && !usdzFile && imageFiles.length === 0))) {
      Alert.alert('Validation', 'Please fill name, price, and select required files.');
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
        size: formData.size,
        width: formData.width,
        offset: formData.offset,
        pcd: formData.pcd,
      };

      let glbObj = glbFile ? { uri: glbFile.uri, type: glbFile.type || 'model/gltf-binary', name: glbFile.name } : null;
      let usdzObj = usdzFile ? { uri: usdzFile.uri, type: usdzFile.type || 'model/vnd.usdz+zip', name: usdzFile.name } : null;
      const imagesArray = imageFiles.map(img => ({ uri: img.uri, type: img.type || 'image/jpeg', name: img.name }));

      if (isEditMode) {
        const targetId = editModel.id || editModel._id;
        await productService.updateWithFile(targetId, payload, glbObj, usdzObj, imagesArray);
        Alert.alert('Success', 'Model updated successfully', [{ text: 'OK', onPress: () => navigation.goBack() }]);
      } else {
        await productService.createWithFile(payload, glbObj, usdzObj, imagesArray);
        Alert.alert('Success', 'Model created successfully', [{ text: 'OK', onPress: () => navigation.goBack() }]);
      }
    } catch (error: any) {
      console.error('Save Model Error:', error);
      Alert.alert('Error', error.response?.data?.message?.toString() || 'Failed to save model');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <Header title={isEditMode ? "Edit Model" : "Add New Model"} showBack />
      
      <KeyboardAwareScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.scrollContent}
        enableOnAndroid={true}
        extraScrollHeight={20}
        keyboardShouldPersistTaps="handled"
      >
        <View style={[styles.card, { backgroundColor: theme.card }]}>
          <Text style={[styles.label, { color: theme.text }]}>Model Name *</Text>
          <TextInput 
            style={[styles.input, { color: theme.text, borderColor: theme.border }]} 
            value={formData.name} 
            onChangeText={t => setFormData({ ...formData, name: t })} 
          />
          <Text style={[styles.label, { color: theme.text }]}>Price (฿) *</Text>
          <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} keyboardType="numeric" value={formData.price} onChangeText={t => setFormData({ ...formData, price: t })} />

          {/* New Fields */}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, marginRight: 8 }}>
              <Text style={[styles.label, { color: theme.text }]}>Size (e.g. 18")</Text>
              <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.size} onChangeText={t => setFormData({ ...formData, size: t })} />
            </View>
            <View style={{ flex: 1, marginLeft: 8 }}>
              <Text style={[styles.label, { color: theme.text }]}>Width (e.g. 8.5J)</Text>
              <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.width} onChangeText={t => setFormData({ ...formData, width: t })} />
            </View>
          </View>

          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, marginRight: 8 }}>
              <Text style={[styles.label, { color: theme.text }]}>Offset (e.g. +35)</Text>
              <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.offset} onChangeText={t => setFormData({ ...formData, offset: t })} />
            </View>
            <View style={{ flex: 1, marginLeft: 8 }}>
              <Text style={[styles.label, { color: theme.text }]}>PCD (e.g. 5x114.3)</Text>
              <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.pcd} onChangeText={t => setFormData({ ...formData, pcd: t })} />
            </View>
          </View>

          <Text style={[styles.label, { color: theme.text }]}>Brand</Text>
          <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.brand} onChangeText={t => setFormData({ ...formData, brand: t })} />

          <Text style={[styles.label, { color: theme.text }]}>Categories (comma separated)</Text>
          <TextInput style={[styles.input, { color: theme.text, borderColor: theme.border }]} value={formData.categories} onChangeText={t => setFormData({ ...formData, categories: t })} />

          <Text style={[styles.label, { color: theme.text }]}>Description</Text>
          <TextInput style={[styles.input, styles.textArea, { color: theme.text, borderColor: theme.border }]} multiline numberOfLines={4} value={formData.description} onChangeText={t => setFormData({ ...formData, description: t })} />
        </View>


        {/* File Uploads */}
        <View style={[styles.card, { backgroundColor: theme.card }]}>
          {isEditMode && <Text style={{ color: '#F59E0B', marginBottom: 10 }}>* Upload new files only if you want to replace existing ones.</Text>}
          <Text style={[styles.sectionTitle, { color: theme.text }]}>GLB Model File</Text>
          <TouchableOpacity
            style={[styles.uploadBox, { borderColor: theme.border, backgroundColor: theme.background, padding: 16 }]}
            onPress={handlePickGlb}
          >
            <Icon name="cube-outline" size={32} color={glbFile ? '#10B981' : theme.subText} />
            <Text style={{ color: glbFile ? '#10B981' : theme.subText, marginTop: 8 }}>
              {glbFile ? glbFile.name : 'Select .GLB File (Android)'}
            </Text>
          </TouchableOpacity>

          <Text style={[styles.sectionTitle, { color: theme.text, marginTop: 24 }]}>USDZ Model File</Text>
          <TouchableOpacity
            style={[styles.uploadBox, { borderColor: theme.border, backgroundColor: theme.background, padding: 16 }]}
            onPress={handlePickUsdz}
          >
            <Icon name="apple" size={32} color={usdzFile ? '#10B981' : theme.subText} />
            <Text style={{ color: usdzFile ? '#10B981' : theme.subText, marginTop: 8 }}>
              {usdzFile ? usdzFile.name : 'Select .USDZ File (iOS)'}
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
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.submitText}>{isEditMode ? "Save Changes" : "Upload Model"}</Text>}
        </TouchableOpacity>
      </KeyboardAwareScrollView>
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
