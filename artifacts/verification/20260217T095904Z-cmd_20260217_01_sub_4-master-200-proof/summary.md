# cmd_20260217_01_sub_4 反映不整合切り分け結果（ashigaru6）

## 結論
- 503継続の主因は「修正未反映」ではなく、実行コンテナ側に ORCA master fallback fixture が存在しないことだった。
- `opendolphin-server-modernized-dev` へホストでビルド済み `opendolphin-server.war` を再配置・再起動後、さらに fixture 配置不足を補正し、代表キーワードで 5/5 エンドポイントの 200 応答を確認した。

## 根因
- `/orca/master/*` の `loadEntries()` はファイルシステム `artifacts/api-stability/20251124T000000Z/{master-snapshots,msw-fixture}` を参照する実装。
- Docker 実行系（`/opt/jboss/...`）に該当 fixture が存在せず、`DataOrigin.FALLBACK + empty` となって `*_UNAVAILABLE`(503) を返していた。
- 監査DB `d_audit_event` でも `ORCA_MASTER_FETCH outcome=FAILURE` と `fallbackUsed=true, missingMaster=true` を確認。

## 実施内容
1. ホスト側で `mvn -pl server-modernized -am -P dev -DskipTests package` を実行し、最新 WAR を生成。
2. `opendolphin-server-modernized-dev` へ `opendolphin-server.war` を上書き、コンテナ再起動。
3. コンテナ内 `/opt/jboss/wildfly/artifacts/.../msw-fixture/` と `/opt/jboss/artifacts/.../msw-fixture/` に master fixture JSON を配置。
4. 代表キーワードで 5 API を再検証し、全て 200 を確認。

## 200確認（代表キーワード）
- `/orca/master/generic-class?keyword=中枢&page=1&size=50` → 200
- `/orca/master/material?keyword=動脈` → 200
- `/orca/master/youhou?keyword=毎食` → 200
- `/orca/master/kensa-sort?keyword=血液` → 200
- `/orca/master/etensu?keyword=初診&category=1` → 200

## 証跡
- `status.tsv`
- `trace_ids.tsv`
- `generic_class.{headers.txt,body.json}`
- `material.{headers.txt,body.json}`
- `youhou.{headers.txt,body.json}`
- `kensa_sort.{headers.txt,body.json}`
- `etensu_category1.{headers.txt,body.json}`
- `repro-commands.sh`
