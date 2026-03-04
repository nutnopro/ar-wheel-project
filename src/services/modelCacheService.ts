// src/services/modelCacheService.ts
import RNFS from 'react-native-fs';
import { MMKV } from 'react-native-mmkv';
import { WheelModel } from '../utils/types';

const storage = new MMKV();
const CACHE_DIR = `${RNFS.CachesDirectoryPath}/ar_models`;

const metaKey = (id: string) => `ar_model_meta_${id}`;

// สร้าง folder cache ถ้ายังไม่มี
const ensureCacheDir = async () => {
  const exists = await RNFS.exists(CACHE_DIR);
  if (!exists) await RNFS.mkdir(CACHE_DIR);
};

/**
 * ดาวน์โหลดไฟล์โมเดลจาก modelUrl แล้วบันทึกไว้ใน cache
 * จากนั้นบันทึก metadata (รวม localPath) ลง MMKV
 * คืนค่า localPath ของไฟล์ที่บันทึก
 */
export async function downloadAndCacheModel(model: WheelModel): Promise<string> {
  await ensureCacheDir();
  const localPath = `${CACHE_DIR}/${model.id}.glb`;

  await RNFS.downloadFile({
    fromUrl: model.modelUrl,
    toFile: localPath,
  }).promise;

  // บันทึก metadata พร้อม localPath ลง MMKV
  const meta = JSON.stringify({ ...model, localPath });
  storage.set(metaKey(model.id), meta);

  return localPath;
}

/**
 * คืน localPath ของโมเดลที่ cache ไว้
 * ถ้า cache หายหรือไฟล์ถูกลบ ให้ดาวน์โหลดใหม่จาก modelUrl
 */
export async function resolveModelPath(model: WheelModel): Promise<string> {
  const raw = storage.getString(metaKey(model.id));
  if (raw) {
    try {
      const cached = JSON.parse(raw) as { localPath?: string };
      if (cached.localPath && (await RNFS.exists(cached.localPath))) {
        return cached.localPath;
      }
    } catch {
      // JSON parse error → re-download
    }
  }
  // ไม่มี cache หรือไฟล์หาย → ดาวน์โหลดใหม่
  return downloadAndCacheModel(model);
}

/**
 * ลบ cache ของโมเดล
 * ถ้าไม่ระบุ id → ลบทั้งหมด
 */
export async function clearModelCache(id?: string): Promise<void> {
  if (id) {
    const raw = storage.getString(metaKey(id));
    if (raw) {
      try {
        const { localPath } = JSON.parse(raw) as { localPath?: string };
        if (localPath && (await RNFS.exists(localPath))) {
          await RNFS.unlink(localPath);
        }
      } catch {}
    }
    storage.delete(metaKey(id));
  } else {
    // ลบ folder cache ทั้งหมด
    if (await RNFS.exists(CACHE_DIR)) {
      await RNFS.unlink(CACHE_DIR);
    }
    // ลบ MMKV keys ทั้งหมดที่มี prefix ar_model_meta_
    storage
      .getAllKeys()
      .filter(k => k.startsWith('ar_model_meta_'))
      .forEach(k => storage.delete(k));
  }
}
