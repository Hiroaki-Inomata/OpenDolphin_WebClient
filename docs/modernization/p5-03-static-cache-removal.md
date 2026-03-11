# P5-03 static singleton / ローカルキャッシュ前提の整理（RUN_ID: 20260311T130114Z）

## 目的
- ORCA 連携の実装で残っていた mutable static 状態を除去する。
- `RestOrcaTransport` の無期限ローカルキャッシュ前提を短寿命化し、設定再読込の挙動を明示する。

## 実施内容
- `open/orca/rest/OrcaResource`
  - mutable static をインスタンス状態へ移行:
    - `HOSP_NUM` → `hospNum`
    - `DB_VERSION` → `dbVersion`
    - `RP_OUT` → `rpOut`
  - `@PostConstruct` 初期化と参照箇所をすべてインスタンスフィールドへ更新。
- `open/dolphin/orca/transport/RestOrcaTransport`
  - `reloadLock` によるグローバル再読込ロックを削除。
  - facility ごとの設定キャッシュを短寿命化:
    - 既定 TTL: `30000ms`
    - 設定キー: `ORCA_TRANSPORT_CACHE_TTL_MS` / `orca.transport.cache.ttl-ms`
  - `CachedTransportEntry` に `loadedAtEpochMilli` を追加し、TTL 超過時に再ロード。
  - 設定ロード処理を `loadSettingsWithFallback` に分離（admin config 優先、失敗時 fallback）。

## 期待効果
- JVM 内 static 状態に依存した ORCA 設定保持を減らし、再読込の見通しを改善。
- ローカルキャッシュの寿命が明示され、設定更新反映遅延を制御可能にする。

## 検証コマンド
```bash
mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile

JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=OrcaTransportSettingsSecurityPolicyTest,OrcaTransportSettingsExternalConfigTest,AdminOrcaConnectionResourceTest,OrcaPatientApiResourceRunIdTest,OrcaPatientResourceIdempotencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
