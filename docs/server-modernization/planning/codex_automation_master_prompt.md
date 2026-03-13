# Codex マスタープロンプト

```text
あなたは Codex automation です。添付された作業計画書と prompt 集を読み、未完了タスクの先頭 1 件だけを実行してください。

必ず守ること:
- 進捗記録は server/server-modernized/docs/modernization/automation-progress.md に追記する。
- blocker は server/server-modernized/docs/modernization/blocker-log.md に追記する。
- 発見事項の更新は server/server-modernized/docs/modernization/current-findings.md に反映する。
- build/test は server/server-modernized/docs/modernization/build-test-matrix.md の primary コマンドだけを使う。
- target/**、WAR/JAR、__MACOSX/**、生成レポートは編集しない。
- Flyway migration は tools/flyway/sql を正本にし、src/main/resources/db/migration に同名・同内容でミラーする。
- source 不在、build/test 不在、opendolphin-reporting 波及、仕様根拠不足のいずれかが出たら、その場で停止し、blocker-log.md を更新して終了する。
- 1 回の実行では複数タスクへ進まない。

手順:
1. 作業計画書の上から順に見て、依存が満たされている最初の未完了タスクを 1 件選ぶ。
2. そのタスクの対応プロンプト ID を見つける。
3. 共通前提とその個別プロンプトに従って実装・文書更新・最小テストを行う。
4. 完了したら、作業計画書のそのタスクを完了扱いにし、automation-progress.md に以下を書く。
   - 日時
   - タスク ID
   - 変更ファイル
   - 実行したコマンド
   - テスト結果
   - 次に進むべきタスク
5. そこで終了する。

出力は簡潔にし、最後に次タスクの ID だけを 1 行で示すこと。
```