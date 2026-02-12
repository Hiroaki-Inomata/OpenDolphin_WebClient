import type { OrderBundleItem } from './orderBundleApi';

export type LocalStampBundle = {
  bundleName: string;
  admin: string;
  bundleNumber: string;
  classCode?: string;
  classCodeSystem?: string;
  className?: string;
  adminMemo: string;
  memo: string;
  startDate: string;
  items: OrderBundleItem[];
};

export type LocalStampEntry = {
  id: string;
  name: string;
  category: string;
  target: string;
  entity: string;
  savedAt: string;
  bundle: LocalStampBundle;
};

export type StampClipboardEntry = {
  savedAt: string;
  source: 'local' | 'server';
  stampId?: string;
  name: string;
  category: string;
  target: string;
  entity: string;
  bundle: LocalStampBundle;
};

const STORAGE_PREFIX = 'web-client:order-stamps';
const CLIPBOARD_PREFIX = `${STORAGE_PREFIX}:clipboard`;
const LEGACY_USER_NAME = ':';

const buildStorageKey = (userName: string) => `${STORAGE_PREFIX}:${userName}`;
const buildClipboardKey = (userName: string) => `${CLIPBOARD_PREFIX}:${userName}`;
const buildLegacyStorageKey = () => buildStorageKey(LEGACY_USER_NAME);
const buildLegacyClipboardKey = () => buildClipboardKey(LEGACY_USER_NAME);
const MAX_LOCAL_STAMP_COUNT = 200;

const persistLocalStamps = (userName: string, entries: LocalStampEntry[]) => {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(buildStorageKey(userName), JSON.stringify(entries.slice(0, MAX_LOCAL_STAMP_COUNT)));
};

const generateLocalStampId = () => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const resolveClipboardStorage = () => {
  if (typeof sessionStorage !== 'undefined') return sessionStorage;
  if (typeof localStorage !== 'undefined') return localStorage;
  return null;
};

export function loadLocalStamps(userName: string): LocalStampEntry[] {
  if (typeof localStorage === 'undefined') return [];
  const key = buildStorageKey(userName);
  const raw = localStorage.getItem(key);
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      return parsed as LocalStampEntry[];
    } catch {
      return [];
    }
  }
  if (userName === LEGACY_USER_NAME) return [];
  const legacyRaw = localStorage.getItem(buildLegacyStorageKey());
  if (!legacyRaw) return [];
  try {
    const parsed = JSON.parse(legacyRaw);
    if (!Array.isArray(parsed)) return [];
    localStorage.setItem(key, JSON.stringify(parsed));
    return parsed as LocalStampEntry[];
  } catch {
    return [];
  }
}

export function saveLocalStamp(
  userName: string,
  entry: Omit<LocalStampEntry, 'id' | 'savedAt'>,
): LocalStampEntry {
  if (typeof localStorage === 'undefined') {
    return { ...entry, id: generateLocalStampId(), savedAt: new Date().toISOString() };
  }
  const existing = loadLocalStamps(userName);
  const next: LocalStampEntry = { ...entry, id: generateLocalStampId(), savedAt: new Date().toISOString() };
  const updated = [next, ...existing];
  persistLocalStamps(userName, updated);
  return next;
}

export function updateLocalStamp(
  userName: string,
  stampId: string,
  entry: Omit<LocalStampEntry, 'id' | 'savedAt'>,
): LocalStampEntry | null {
  if (typeof localStorage === 'undefined') return null;
  const existing = loadLocalStamps(userName);
  let updatedStamp: LocalStampEntry | null = null;
  const updated = existing.map((current) => {
    if (current.id !== stampId) return current;
    updatedStamp = {
      ...entry,
      id: current.id,
      savedAt: new Date().toISOString(),
    };
    return updatedStamp;
  });
  if (!updatedStamp) return null;
  persistLocalStamps(userName, updated);
  return updatedStamp;
}

export function deleteLocalStamp(userName: string, stampId: string): boolean {
  if (typeof localStorage === 'undefined') return false;
  const existing = loadLocalStamps(userName);
  const updated = existing.filter((entry) => entry.id !== stampId);
  if (updated.length === existing.length) return false;
  persistLocalStamps(userName, updated);
  return true;
}

export function loadStampClipboard(userName: string): StampClipboardEntry | null {
  const storage = resolveClipboardStorage();
  if (!storage) return null;
  const raw = storage.getItem(buildClipboardKey(userName));
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') return null;
      return parsed as StampClipboardEntry;
    } catch {
      return null;
    }
  }
  if (userName === LEGACY_USER_NAME) return null;
  const legacyRaw = storage.getItem(buildLegacyClipboardKey());
  if (!legacyRaw) return null;
  try {
    const parsed = JSON.parse(legacyRaw);
    if (!parsed || typeof parsed !== 'object') return null;
    storage.setItem(buildClipboardKey(userName), JSON.stringify(parsed));
    return parsed as StampClipboardEntry;
  } catch {
    return null;
  }
}

export function saveStampClipboard(userName: string, entry: StampClipboardEntry): StampClipboardEntry {
  const storage = resolveClipboardStorage();
  if (!storage) {
    return { ...entry, savedAt: entry.savedAt || new Date().toISOString() };
  }
  const next = { ...entry, savedAt: entry.savedAt || new Date().toISOString() };
  storage.setItem(buildClipboardKey(userName), JSON.stringify(next));
  return next;
}

export function clearStampClipboard(userName: string): void {
  const storage = resolveClipboardStorage();
  if (!storage) return;
  storage.removeItem(buildClipboardKey(userName));
}
