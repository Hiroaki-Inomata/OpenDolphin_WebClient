# P7-05 受信メッセージ再生ツール（RUN_ID: 20260311T220125Z）

## 目的
- PVT受信メッセージをローカルで再生し、再試行・重複防止・毒メッセージ退避の挙動を短時間で確認できるようにする。

## 追加物
- replay ツール（test scope）
  - `server-modernized/src/test/java/open/dolphin/worker/pvt/PvtReplayTool.java`
  - `--input` で指定した `.xml/.txt` payload を読み込み、`PvtSocketWorker` の pipeline へ投入。
  - `--repeat` / `--fail-first` / `--retry-max` などで再試行・重複・毒メッセージを再現可能。
  - 実行結果を `replay-summary total=...` 形式で出力。
- サンプル入力
  - `server-modernized/src/test/resources/replay/pvt/normal-message.xml`
- 回帰テスト
  - `server-modernized/src/test/java/open/dolphin/worker/pvt/PvtReplayToolTest.java`
  - 同一payload再生時の duplicate 検出
  - retry 上限超過時の poison 退避

## 実行例
- テスト実行:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=PvtReplayToolTest,PvtSocketWorkerPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test`
- ツール実行（IDE/テスト実行環境）:
  - `PvtReplayTool --input server-modernized/src/test/resources/replay/pvt --repeat 2 --retry-max 3 --fail-first 0`
