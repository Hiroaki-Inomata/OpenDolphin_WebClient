# P6-02 java.util.Date -> java.time 移行記録

- 実施日: 2026-03-12
- RUN_ID: 20260311T170115Z
- 対象WBS: `P6-02`

## 目的
- `java.util.Date` 依存を主要モデルから段階的に排除し、`java.time` を正規表現へ移行する。
- 既存呼び出しとの互換を壊さないため、旧 `Date` アクセサは当面変換レイヤとして残す。

## 変更概要
- `persistence` の主要モデルで内部保持型を `java.time` 化。
- 集計系サービス/通知で `LocalDate` 優先の扱いへ寄せ、月次集計の時刻境界（23:59:59）互換を維持。

## 型移行対応表
| モデル | 旧型 | 新内部型 | 互換メソッド |
|---|---|---|---|
| `ActivityModel` | `Date fromDate/toDate` | `LocalDateTime fromDate/toDate` | `getFromDate()/setFromDate(Date)`, `getToDate()/setToDate(Date)` |
| `FacilityModel` | `Date registeredDate` | `LocalDate registeredDate` | `getRegisteredDate()/setRegisteredDate(Date)` |
| `PatientModel` | `Date firstVisited`(Transient) | `LocalDate firstVisited` | `getFirstVisited()/setFirstVisited(Date)` |
| `PostSchedule` | `Date scheduleDate` | `LocalDate scheduleDate` | `getScheduleDate()/setScheduleDate(Date)` |
| `LastDateCount` | `Date created/lastDocDate/lastImageDate` | `LocalDate` | 旧 `Date` getter/setter を維持 |

## サービス層の反映
- `SystemServiceBean`
  - `countActivities(String, LocalDate, LocalDate)` を追加。
  - 既存 `Date` 版は互換用に残し、時刻情報を保持したまま内部処理へ委譲。
  - `ActivityModel` には `setFromLocalDate` / `setToLocalDate` を使用。
- `OidSender`
  - 活動レポートの日付フォーマットを `Date` ではなく `LocalDate` ベースへ変更。

## 検証
- コンパイル:
  - `mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- テスト:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=SystemServiceBeanBulkAggregationTest,SystemResourceTest,MessagingDefensiveCopyTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS（23 tests）

## 既知事項
- 全体の `Date` 廃止は未完了。今回のスコープは「主要モデル + 集計経路」で、他の legacy 経路は後続タスクで段階的に移行する。
