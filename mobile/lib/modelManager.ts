import * as FileSystem from 'expo-file-system';
import { GEMMA_MODELS } from './types';

const MODELS_DIR = FileSystem.documentDirectory + 'models/';

export interface DownloadProgress {
  file: string;
  bytesWritten: number;
  totalBytes: number;
  fileIndex: number;
  totalFiles: number;
}

export function modelDir() {
  return MODELS_DIR;
}

export function modelPath(filename: string) {
  return MODELS_DIR + filename;
}

export async function modelsReady(): Promise<boolean> {
  try {
    for (const m of GEMMA_MODELS) {
      const info = await FileSystem.getInfoAsync(MODELS_DIR + m.name);
      if (!info.exists || info.size < m.sizeBytes * 0.95) return false;
    }
    return true;
  } catch {
    return false;
  }
}

export async function ensureModelsDir() {
  const info = await FileSystem.getInfoAsync(MODELS_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(MODELS_DIR, { intermediates: true });
}

export async function downloadModels(
  onProgress: (p: DownloadProgress) => void,
  onError: (msg: string) => void,
): Promise<boolean> {
  await ensureModelsDir();

  for (let i = 0; i < GEMMA_MODELS.length; i++) {
    const model = GEMMA_MODELS[i];
    const dest = MODELS_DIR + model.name;

    const existing = await FileSystem.getInfoAsync(dest);
    if (existing.exists && existing.size >= model.sizeBytes * 0.95) {
      onProgress({ file: model.name, bytesWritten: model.sizeBytes, totalBytes: model.sizeBytes, fileIndex: i, totalFiles: GEMMA_MODELS.length });
      continue;
    }

    const dl = FileSystem.createDownloadResumable(
      model.url,
      dest,
      {},
      ({ totalBytesWritten, totalBytesExpectedToWrite }) => {
        onProgress({
          file: model.name,
          bytesWritten: totalBytesWritten,
          totalBytes: totalBytesExpectedToWrite ?? model.sizeBytes,
          fileIndex: i,
          totalFiles: GEMMA_MODELS.length,
        });
      },
    );

    try {
      const result = await dl.downloadAsync();
      if (!result?.uri) throw new Error('Download returned no URI');
    } catch (e) {
      onError(`Failed to download ${model.name}: ${e instanceof Error ? e.message : String(e)}`);
      return false;
    }
  }

  return true;
}

export async function deleteModels() {
  const info = await FileSystem.getInfoAsync(MODELS_DIR);
  if (info.exists) await FileSystem.deleteAsync(MODELS_DIR, { idempotent: true });
}
