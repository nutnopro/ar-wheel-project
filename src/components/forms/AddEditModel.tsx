import React from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
} from 'react-native';
import Ionicons from '@react-native-vector-icons/ionicons';
import { COLORS } from '../../theme/colors';

// --- Props Interface ---
interface Props {
  // Data & Handlers
  name: string;
  onChangeName: (val: string) => void;
  price: string;
  onChangePrice: (val: string) => void;
  diameter: string;
  onChangeDiameter: (val: string) => void;
  width: string;
  onChangeWidth: (val: string) => void;
  offset: string;
  onChangeOffset: (val: string) => void;
  boltSize: string;
  onChangeBoltSize: (val: string) => void;
  material: string;
  onChangeMaterial: (val: string) => void;
  weight: string;
  onChangeWeight: (val: string) => void;
  brand: string;
  onChangeBrand: (val: string) => void;
  origin: string;
  onChangeOrigin: (val: string) => void;
  category: string;
  onChangeCategory: (val: string) => void;

  // File Names (Mockup)
  thumbnailName?: string;
  modelFileName?: string;

  // Actions
  onSelectThumbnail: () => void;
  onSelectModelFile: () => void;
  onSave: () => void;
  onBack: () => void;
}

// --- Sub-Component: Upload Box ---
const UploadBox = ({ title, fileName, onSelect, supportedFormats }: any) => (
  <View style={styles.uploadSection}>
    <Text style={styles.uploadTitle}>{title}</Text>

    {/* File Name & Color Picker Row */}
    <View style={styles.fileRow}>
      <Text style={styles.fileName}>{fileName || 'No file selected'}</Text>
      {/* Mockup Color Picker Button */}
      <TouchableOpacity style={styles.colorPickerBtn}>
        <Text style={styles.colorPickerText}>color</Text>
        <Ionicons name="chevron-down" size={16} color={COLORS.textDim} />
      </TouchableOpacity>
    </View>

    {/* Select Button */}
    <TouchableOpacity
      style={styles.selectButton}
      onPress={onSelect}
      activeOpacity={0.8}
    >
      <Ionicons
        name="cloud-upload-outline"
        size={24}
        color={COLORS.textDim}
        style={{ marginBottom: 4 }}
      />
      <Text style={styles.selectButtonText}>Select {title.toLowerCase()}</Text>
      <Text style={styles.supportedFormats}>
        Supported Format: {supportedFormats}
      </Text>
    </TouchableOpacity>
  </View>
);

// --- Sub-Component: Underline Input Field ---
const InputField = ({
  label,
  value,
  onChangeText,
  placeholder,
  keyboardType = 'default',
}: any) => (
  <View style={styles.inputContainer}>
    <Text style={styles.inputLabel}>{label}</Text>
    <TextInput
      style={styles.input}
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={COLORS.textDim}
      keyboardType={keyboardType}
    />
  </View>
);

// --- Main Component ---
export default function AddEditModelForm(props: Props) {
  return (
    <SafeAreaView style={styles.safeArea}>
      {/* --- Header --- */}
      <View style={styles.header}>
        <TouchableOpacity onPress={props.onBack} style={styles.backBtn}>
          <Ionicons name="arrow-back" size={28} color={COLORS.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Add New Model</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* --- Upload Sections --- */}
        <UploadBox
          title="Model thumbnails"
          fileName={props.thumbnailName || 'file_name_1'}
          onSelect={props.onSelectThumbnail}
          supportedFormats="jpg, jpeg, png, webp"
        />

        <UploadBox
          title="Model files"
          fileName={props.modelFileName || 'file_name_1'}
          onSelect={props.onSelectModelFile}
          supportedFormats="glb, obj, usdz"
        />

        {/* --- Form Fields --- */}
        <View style={styles.formContainer}>
          <InputField
            label="model name"
            value={props.name}
            onChangeText={props.onChangeName}
            placeholder="model_name"
          />
          <InputField
            label="price"
            value={props.price}
            onChangeText={props.onChangePrice}
            placeholder="฿000.00"
            keyboardType="numeric"
          />
          <InputField
            label="diameter"
            value={props.diameter}
            onChangeText={props.onChangeDiameter}
            placeholder='00"'
            keyboardType="numeric"
          />
          <InputField
            label="width"
            value={props.width}
            onChangeText={props.onChangeWidth}
            placeholder="0.0J"
            keyboardType="numeric"
          />
          <InputField
            label="offset"
            value={props.offset}
            onChangeText={props.onChangeOffset}
            placeholder="ET+00"
          />
          <InputField
            label="bolt size"
            value={props.boltSize}
            onChangeText={props.onChangeBoltSize}
            placeholder="0x000.0"
          />
          <InputField
            label="material"
            value={props.material}
            onChangeText={props.onChangeMaterial}
            placeholder="aluminum alloy"
          />
          <InputField
            label="weight"
            value={props.weight}
            onChangeText={props.onChangeWeight}
            placeholder="0.0 kg"
            keyboardType="numeric"
          />
          <InputField
            label="brand"
            value={props.brand}
            onChangeText={props.onChangeBrand}
            placeholder="brand AAA"
          />
          <InputField
            label="country of origin"
            value={props.origin}
            onChangeText={props.onChangeOrigin}
            placeholder="made in japan"
          />
          <InputField
            label="category"
            value={props.category}
            onChangeText={props.onChangeCategory}
            placeholder="category"
          />
        </View>

        {/* --- Save Button --- */}
        <TouchableOpacity
          style={styles.saveButton}
          onPress={props.onSave}
          activeOpacity={0.9}
        >
          <Text style={styles.saveButtonText}>Save New Model</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#fff' },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E2E8F0',
  },
  backBtn: { marginRight: 16 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: COLORS.text },

  scrollContent: { padding: 20, paddingBottom: 40 },

  // Upload Section
  uploadSection: { marginBottom: 24 },
  uploadTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: COLORS.text,
    marginBottom: 8,
  },
  fileRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
    paddingHorizontal: 4,
  },
  fileName: { fontSize: 16, color: COLORS.text },
  colorPickerBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F1F5F9',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  colorPickerText: { fontSize: 14, color: COLORS.text, marginRight: 4 },
  selectButton: {
    backgroundColor: '#E9ECEF', // สีเทาอ่อนๆ ตามรูป
    borderRadius: 12,
    paddingVertical: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#DEE2E6',
    borderStyle: 'dashed', // เพิ่มเส้นประให้ดูเป็น Dropzone
  },
  selectButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: COLORS.text,
    marginBottom: 4,
  },
  supportedFormats: { fontSize: 12, color: COLORS.textDim },

  // Form Fields
  formContainer: { marginBottom: 32 },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#E2E8F0',
    marginBottom: 16,
    paddingVertical: 4,
  },
  inputLabel: {
    width: 120, // Fixed width for labels to align inputs
    fontSize: 16,
    fontWeight: '500',
    color: COLORS.text,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: COLORS.text,
    paddingVertical: 8, // เพิ่ม padding ให้ดูสบายตา
  },

  // Save Button
  saveButton: {
    backgroundColor: COLORS.primary, // ใช้สีหลักของแอป (เช่น น้ำเงินเข้ม)
    borderRadius: 16,
    paddingVertical: 16,
    alignItems: 'center',
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 4,
  },
  saveButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
  },
});
