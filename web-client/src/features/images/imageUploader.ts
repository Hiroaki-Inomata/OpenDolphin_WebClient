import type { KarteAttachmentPayload, KarteDocumentAttachmentPayload } from './api';

export type UploadSource = {
  name: string;
  type: string;
  size: number;
  lastModified?: number;
  arrayBuffer: () => Promise<ArrayBuffer>;
};

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
const BASE64_CHUNK_SIZE = 0x8000;

const normalizeExtension = (name: string) => {
  const idx = name.lastIndexOf('.');
  if (idx < 0) return undefined;
  const ext = name.slice(idx + 1).trim().toLowerCase();
  return ext.length > 0 ? ext : undefined;
};

export const bytesToBase64 = (bytes: Uint8Array) => {
  if (typeof Buffer !== 'undefined') {
    return Buffer.from(bytes).toString('base64');
  }
  const chunks: string[] = [];
  for (let index = 0; index < bytes.length; index += BASE64_CHUNK_SIZE) {
    const chunk = bytes.subarray(index, index + BASE64_CHUNK_SIZE);
    chunks.push(String.fromCharCode(...chunk));
  }
  return btoa(chunks.join(''));
};

export async function buildAttachmentPayload(source: UploadSource): Promise<KarteAttachmentPayload> {
  if (source.size > MAX_ATTACHMENT_BYTES) {
    throw new Error('attachment_too_large');
  }

  const buffer = await source.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  if (bytes.byteLength > MAX_ATTACHMENT_BYTES) {
    throw new Error('attachment_too_large');
  }

  return {
    fileName: source.name,
    contentType: source.type || 'application/octet-stream',
    contentSize: source.size,
    lastModified: source.lastModified,
    extension: normalizeExtension(source.name),
    bytes: bytesToBase64(bytes),
  };
}

export function buildImageDocumentPayload(params: {
  attachments: KarteAttachmentPayload[];
  patientId?: string;
  title?: string;
  memo?: string;
  recordedAt?: string;
}): KarteDocumentAttachmentPayload {
  const recordedAt = params.recordedAt ?? new Date().toISOString();
  return {
    status: 'temp',
    docInfoModel: {
      title: params.title ?? '画像添付',
      patientId: params.patientId,
      recordedAt,
    },
    attachment: params.attachments.map((attachment) => ({
      ...attachment,
      title: attachment.title ?? params.title ?? attachment.fileName,
      memo: attachment.memo ?? params.memo,
    })),
  };
}

export function formatBytes(value?: number) {
  if (value === undefined || Number.isNaN(value)) return '―';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB'];
  let size = value / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toFixed(size < 10 ? 1 : 0)} ${units[unitIndex]}`;
}
