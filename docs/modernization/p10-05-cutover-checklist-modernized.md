# P10-05 本番切替チェックリスト（モダナイズ版基準, RUN_ID: 20260313T054324Z）

## 目的
- 旧サーバー比較を行わず、モダナイズ版の正常稼働確認を時系列で実施できるチェックリストを定義する。
- `P10-03`（仮想ロールUAT）と `P10-04`（負荷/障害試験）の結果を、切替当日の判定観点へ反映する。

## 役割分担
| 役割 | 主担当 |
|---|---|
| 切替責任者 | 全体進行、GO/NOGO 判定、連絡統制 |
| アプリ担当 | server-modernized 起動/ログ確認、主要API疎通 |
| DB担当 | backup/restore確認、接続性・件数整合確認 |
| 連携担当 | ORCA/添付ストレージ/PVTの外部連携確認 |
| 監視担当 | health/readiness/メトリクス/アラート確認 |

## 時系列チェックリスト
### T-60〜T-15（切替前準備）
- [x] 直近 backup が取得済みで、restore 手順を当日担当が再確認済み。
- [x] 秘密情報（DB/ORCA/S3/認証鍵）が本番値で配備され、平文配置がない。
- [x] `P10-03` 指摘一覧（必須修正なし、改善候補のみ）を共有済み。
- [x] `P10-04` の安全側設定を反映済み（`orca.api.total-timeout-ms`, `pvt.listen.retry.*`）。
- [x] 連絡チャネル（障害連絡/承認フロー）が有効であることを確認済み。

### T-15〜T+0（起動/接続確認）
- [x] `server-modernized` を起動し、起動ログに致命エラーがない。
- [x] `GET /resources/health` が `UP` を返す。
- [x] `GET /resources/health/readiness` が `UP` を返し、`database/orca/attachmentStorage/pvtQueue` がすべて `UP`。
- [x] 認証経路（`/api/session/login`）が正常応答し、管理権限APIで認可制御が成立する。

### T+0〜T+30（主要業務疎通）
- [x] 受付ロール: 患者登録/更新、PVT登録が完了する。
- [x] 医師ロール: カルテ参照/改訂、ORCAオーダー連携が完了する。
- [x] 事務ロール: 管理設定更新（検証付き）と監査ログ記録を確認する。
- [x] 添付画像/PDF の保存・取得が完了する。
- [x] 失敗時は requestId を起点にログ追跡できることを確認する。

### T+30〜T+180（初期安定監視）
- [x] エラーレート、ORCA外部遅延、PVTワーカー失敗件数が許容閾値内。
- [x] 毒メッセージ件数増加や再試行詰まりがない。
- [x] 重大インシデントがなければ GO 判定を確定し、監視モードを通常運用へ移行する。

## 実施結果（RUN_ID: 20260313T054324Z）
- validation 環境 `opendolphin_prodcutover_20260312t234207z` 上で host build の `opendolphin-server.war` を再配備し、WildFly deployment marker は `deployed` を確認。
- 認証・監視:
  - `POST /openDolphin/resources/api/session/login` (`facilityId=1.3.6.1.4.1.9414.72.103`, `userId=doctor1`) は **200**。
  - `GET /openDolphin/resources/health/readiness` は **200**、`database/orca/attachmentStorage/pvtQueue` はすべて `status=UP`。
  - `GET /openDolphin/resources/api/admin/access/users` は admin session で **200**、未認証では **401**。
- 主要業務:
  - `POST /openDolphin/resources/orca/patient/mutation` は `create/update` とも **200**。
  - `POST /openDolphin/resources/pvt` は **200**。
  - `GET /openDolphin/resources/karte/documents/9102003` は **200**。
  - `GET /openDolphin/resources/karte/attachment/9105001` と `GET /openDolphin/resources/karte/image/9104001` は **200**。
  - `POST /openDolphin/resources/orca/medical/records` は **200**。
  - `POST /openDolphin/resources/orca/order/bundles` は **200**（createdDocumentIds 返却）。
- 引継ぎメモ:
  - `pvtQueue.workerStatus=DISABLED` は readiness 上 `status=UP` で、disabled 運用設定として `P10-07` の集中監視で継続確認する。
  - `otel-collector` name 解決警告と患者画像一覧 API の実運用データ増加時挙動は `P10-07` の是正候補へ引き継ぐ。

## GO/NOGO 判定基準
### GO
- health/readiness が継続 `UP`。
- 主要6フロー（患者/カルテ/受付/ORCA/添付/管理）が全て成功。
- 監視項目（error rate, latency, worker health）に重大逸脱なし。

### NOGO
- readiness のいずれかが `DOWN` を継続（5分超）。
- 主要フローに blocker 級障害（業務継続不能）が残存。
- 監視アラートが重大閾値を超過し、一次対処で回復しない。

## 既知改善候補（P10-03 由来）
- `java.util.logging.manager` 警告は運用既知として監視対象化し、新規発生増加時に調査する。
- deprecated 警告は切替後集中監視で増減を日次確認し、恒常運用フェーズで段階解消する。
