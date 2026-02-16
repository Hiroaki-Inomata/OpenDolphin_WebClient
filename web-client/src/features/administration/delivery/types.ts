export type DeliverySection =
  | 'dashboard'
  | 'connection'
  | 'config'
  | 'queue'
  | 'master-health'
  | 'medicalset'
  | 'debug';

export type DeliverySectionItem = {
  id: DeliverySection;
  label: string;
  description: string;
};

export const DELIVERY_SECTION_ITEMS: DeliverySectionItem[] = [
  { id: 'dashboard', label: '概要', description: '運用KPI・異常サマリー' },
  { id: 'connection', label: '接続', description: 'WebORCA接続設定' },
  { id: 'config', label: '配信設定', description: '保存して配信' },
  { id: 'queue', label: '配信キュー', description: 'ORCA queue監視・操作' },
  { id: 'master-health', label: 'マスタ/ヘルス', description: 'master同期・system health' },
  { id: 'medicalset', label: '診療セット', description: 'medicalsetv2検索' },
  { id: 'debug', label: '診断/デバッグ', description: 'XMLプロキシ/内製ラッパー/Legacy/Touch' },
];
