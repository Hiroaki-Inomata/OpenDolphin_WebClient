# P1-07 PVT/JMS 連携テスト判定表

- RUN_ID: 20260310T215953Z
- 対象: P1-07（PVT 受信と JMS 連携の性格確認）
- 判定方針: 受信プロトコルではなく、受信後の業務結果（PVT登録・状態更新・失敗時不登録）を固定する。

## 代表入力メッセージ（3種類）
| 種別 | 代表入力 | 期待業務結果 |
|---|---|---|
| JMS 監査イベント | `{"type":"AUDIT_EVENT", ...}` | 監査イベントとして受理し、PVT登録処理は走らない |
| JMS PVT不正入力 | `{"type":"PVT_XML","pvtXml":"   "}` | 空XMLとして拒否し、PVT登録しない |
| REST PVT登録 | `POST /pvt` 患者JSON（保険情報付き） | 施設ID付与・保険の親参照構築後に `addPvt` が呼ばれる |

## 機能別テスト
| 機能 | 正常系テスト | 代表失敗系テスト |
|---|---|---|
| JMS envelope 処理 | `MessageSenderTest#auditEnvelopeIsAcceptedWithoutPvtImport` | `MessageSenderTest#pvtEnvelopeWithBlankXmlIsRejectedWithoutProcessing` |
| PVT登録（REST） | `PVTResourceLimitTest#postPvt_setsFacilityAndInsuranceRelationBeforeAdd` | `PVTResourceLimitTest#putPvtState_throwsNotFoundWhenUpdateCountIsZero` |
| PVT一覧取得（受信後参照） | `PVTResourceLimitTest#getPvt_withLimitPassesExplicitLimitToStandardRoute` | `PVTResourceLimitTest#getPvt_withoutLimitUsesDefaultPageSize`（limit未指定境界） |
| 受付リアルタイム通知 | `ReceptionRealtimeStreamResourceTest#subscribeRegistersWhenSessionIsAvailable` | `ReceptionRealtimeStreamResourceTest#subscribeReturnsServiceUnavailableWhenRegisterFails` |

## 実行コマンド（P1-07）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn -o -f pom.server-modernized.xml \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=MessageSenderTest,PVTResourceLimitTest,PVTServiceBeanPaginationTest,ReceptionRealtimeStreamResourceTest,ReceptionRealtimeSseSupportTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
