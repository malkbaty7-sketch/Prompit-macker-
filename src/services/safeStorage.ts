/**
 * Safe storage wrapper that guards against sandboxed iframes
 * where localStorage access throws SecurityError / DOMException.
 */
class SafeStorage {
  private memoryFallback: Map<string, string> = new Map();

  getItem(key: string): string | null {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        return window.localStorage.getItem(key);
      }
    } catch {
      // Sandboxed or restricted storage
    }
    return this.memoryFallback.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(key, value);
        return;
      }
    } catch {
      // Sandboxed or quota exceeded
    }
    this.memoryFallback.set(key, value);
  }

  removeItem(key: string): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.removeItem(key);
      }
    } catch {}
    this.memoryFallback.delete(key);
  }

  clear(): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.clear();
      }
    } catch {}
    this.memoryFallback.clear();
  }
}

export const safeStorage = new SafeStorage();

/**
 * Safe clipboard copy with execCommand fallback for sandboxed iframes
 */
export async function safeCopy(text: string): Promise<boolean> {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // Clipboard API blocked in iframe
  }

  try {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    textArea.style.top = '-999999px';
    textArea.setAttribute('readonly', '');
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    const successful = document.execCommand('copy');
    document.body.removeChild(textArea);
    return successful;
  } catch {
    return false;
  }
}
