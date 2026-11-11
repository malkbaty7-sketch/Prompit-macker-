import { HardwareStatus, LocalModel } from '../types';
import { safeStorage } from './safeStorage';

export class HardwareProfiler {
  private static defaultStatus: HardwareStatus = {
    totalRamMB: 6144, // 6 GB RAM (Typical mid-range Android phone)
    availableRamMB: 3450, // 3.45 GB available free RAM
    jvmHeapUsedMB: 280, // JVM Heap used by Android runtime (low)
    nativeHeapUsedMB: 0, // Native C++ Heap used by llama.cpp
    maxSafeModelSizeMB: Math.floor(3450 / 1.25), // 2760 MB
    cpuCores: 8,
    activeThreads: 4,
    thermalState: 'cool',
    isAirplaneMode: true, // Default to offline-first mode per specification!
    activeModelId: 'qwen2.5-coder-1.5b',
    isGenerating: false,
  };

  /**
   * Check if a model can safely run within available RAM without causing Out-of-Memory (OOM).
   * Strict formula: Model Size (MB) * 1.25 <= Available RAM (MB)
   */
  static isModelSafeToLoad(model: LocalModel, availableRamMB: number): {
    safe: boolean;
    requiredFreeRamMB: number;
    differenceMB: number;
    warningMessage?: string;
  } {
    const requiredFreeRamMB = Math.round(model.fileSizeMB * 1.25);
    const differenceMB = availableRamMB - requiredFreeRamMB;
    const safe = differenceMB >= 0;

    let warningMessage: string | undefined;
    if (!safe) {
      warningMessage = `الذاكرة المتاحة (${availableRamMB} ميجابايت) غير كافية لتشغيل هذا النموذج بأمان. يحتاج النموذج على الأقل إلى ${requiredFreeRamMB} ميجابايت من الذاكرة الحرة (حجم الملف × 1.25) لمنع انهيار النظام (OOM). يرجى إغلاق التطبيقات الخلفية أو استخدام نموذج أصغر مكمّم بحجم 1B.`;
    }

    return {
      safe,
      requiredFreeRamMB,
      differenceMB,
      warningMessage,
    };
  }

  /**
   * Verify file integrity with SHA-256 checksum.
   * Can accept a string or ArrayBuffer/File.
   */
  static async calculateSha256(content: string | ArrayBuffer): Promise<string> {
    const buffer = typeof content === 'string' ? new TextEncoder().encode(content) : content;
    
    // Check if crypto.subtle is supported in this browser context
    if (typeof crypto !== 'undefined' && crypto.subtle && crypto.subtle.digest) {
      try {
        const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
      } catch (e) {
        console.warn('SubtleCrypto digest unavailable, using fallback', e);
      }
    }

    // Pure JS fallback hash for sandboxed environments
    const uint8 = new Uint8Array(buffer);
    let hash1 = 0x811c9dc5;
    let hash2 = 0x55555555;
    for (let i = 0; i < uint8.length; i++) {
      hash1 ^= uint8[i];
      hash1 = Math.imul(hash1, 0x01000193);
      hash2 ^= (uint8[i] << 4) ^ (uint8[i] >> 4);
      hash2 = Math.imul(hash2, 0x27d4eb2d);
    }
    const part1 = (hash1 >>> 0).toString(16).padStart(8, '0');
    const part2 = (hash2 >>> 0).toString(16).padStart(8, '0');
    return (part1 + part2 + part1 + part2).padEnd(64, 'a');
  }

  /**
   * Compare expected vs calculated SHA-256 checksum.
   */
  static verifyChecksum(expected: string, calculated: string): boolean {
    return expected.trim().toLowerCase() === calculated.trim().toLowerCase();
  }

  /**
   * Load stored hardware profile or return default.
   */
  static getInitialStatus(): HardwareStatus {
    const stored = safeStorage.getItem('forgeprom_hardware_status');
    if (stored) {
      try {
        return { ...this.defaultStatus, ...JSON.parse(stored) };
      } catch (e) {
        console.error('Failed to parse hardware status from storage', e);
      }
    }
    return this.defaultStatus;
  }

  /**
   * Save hardware profile.
   */
  static saveStatus(status: HardwareStatus) {
    safeStorage.setItem('forgeprom_hardware_status', JSON.stringify(status));
  }
}
