# cmd_20260218_01_sub_16 判定フローチャート（簡易）

```text
Start
  |
  v
Master API response status?
  |
  +-- 2xx --------------------------> PASS (受入継続)
  |
  +-- 404 --------------------------> body.code == TENSU_NOT_FOUND ?
  |                                     |
  |                                     +-- No --> FAIL (実装/経路不整合の疑い)
  |                                     |
  |                                     +-- Yes --> endpoint health check 2xx ?
  |                                                    |
  |                                                    +-- Yes --> EXCLUDE
  |                                                    |           (401不整合判定から除外)
  |                                                    |
  |                                                    +-- No --> FAIL
  |
  +-- 401/403/5xx ------------------> FAIL (認証/実装不整合)
```

判定メモ:
- `EXCLUDE` は「認証不整合修正の受入判定」からのみ除外。
- 障害票は必要に応じ別管理（業務検索0件として扱う）。
