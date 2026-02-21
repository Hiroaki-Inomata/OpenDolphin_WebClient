# G担当メモ: QA/CI 改善

- RUN_ID: `20260221T033838Z`
- 対象: `server-modernized`

## 実施内容
1. **Failsafe導入（IT実行）**
- `server-modernized/pom.xml` に `maven-failsafe-plugin` を追加。
- `integration-test` / `verify` フェーズで `**/*IT.java`, `**/*ITCase.java` を実行するように設定。
- `mvn -q verify` で `DemoResourceAspProdProfileIT` 実行を確認。

2. **Flyway整合性チェックテスト追加**
- 追加: `server-modernized/src/test/java/open/dolphin/db/FlywayMigrationConsistencyTest.java`
- 検査項目:
  - 重複 version 検知（canonical / mirror 両方）
  - 正本 `tools/flyway/sql` と `src/main/resources/db/migration` の filename/content 同期

3. **System property 汚染対策**
- 修正: `server-modernized/src/test/java/open/dolphin/msg/MessagingDefensiveCopyTest.java`
- `jboss.home.dir` を `try/finally` で復元するよう変更。

4. **スタブ資産存在保証テスト追加**
- 追加: `server-modernized/src/test/java/open/dolphin/orca/transport/OrcaEndpointStubResourceTest.java`
- `OrcaEndpoint.values()` を全走査し、stub resource の存在・非空を検証。

5. **Snapshot fixture / 成果物の target 寄せ**
- 修正: `server-modernized/src/test/java/open/dolphin/adm/AdmConverterSnapshotTest.java`
  - fixture の既定先を `src/test/resources/fixtures/adm` に変更
  - 出力先の既定を `target/adm-snapshots` に変更
  - 比較成功時も `baseline/actual/diff` を `target/adm-snapshots` に出力
- 追加: `server-modernized/src/test/resources/fixtures/adm/**`（ops fixture を移設）
- `pom.xml` の surefire/failsafe system properties も新しい既定先へ更新。

## 検証
- `cd server-modernized && mvn -q test` : PASS
- `cd server-modernized && mvn -q verify` : PASS
- `target/failsafe-reports/failsafe-summary.xml` で IT 1件実行・失敗0を確認
- `target/adm-snapshots/*` に snapshot 成果物が生成されることを確認

## 備考
- 本件は QA/CI 範囲のみを変更（本番コード変更なし）。
