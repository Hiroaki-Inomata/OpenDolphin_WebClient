# Codex オートメーション作業計画書（追加修正版）

## この計画書の位置づけ
この計画書は、今回の再レビューで確認できた実コードの状態をもとに、Codex のオートメーションで順番に直していくための実行用手順書です。  
1 回のタスク量は、GPT-5.4 High で 2 時間以内に終わる分量へ分けています。  
各タスクは **前のタスクが完了してから** 進めます。途中で blocker が出たら、その場で止め、勝手に次へ進めません。

## 今回の再レビューで固定した前提
- `common/common` に中核モデルの source があり、`DocumentModel`、`ModuleModel`、`UserModel`、`KarteBean`、`HealthInsuranceModel` は直接修正できます。
- `server/server-modernized` に `open/dolphin/touch/**` の source が残っており、Touch は表面だけでなく実装もまだ残っています。
- `ModuleModel` と `HealthInsuranceModel` は今も `beanJson` を持ち、`ModuleJsonConverter` は `activateDefaultTyping` を使っています。
- `DocumentModel` には今も `toDetuch()` / `toPersist()` が残っています。
- `UserModel` と `KarteBean` には `java.util.Date` が残り、`UserModel.roles` は `EAGER` です。
- `ORCAConnection` と `OrcaTransportSettings` は今も `custom.properties` / `jboss.home.dir` に引っ張られる経路を持っています。
- `AdminConfigStore`、`MasterUpdateStore`、`OrcaConnectionConfigStore`、`OrcaPatientSyncStateStore`、`PushEventDeduplicator`、`FileLicenseRepository` には、今もローカル JSON / ファイル / `user.home` 依存が残っています。
- `AttachmentStorageConfigLoader` は固定パス YAML を既定に持ち、`AttachmentStorageManager` と `ImageStorageManager` は static credential と `byte[]` 全載せの経路を持っています。
- `PvtService` は本体アプリ内で `ServerSocket` を抱えています。
- `MeterRegistryProducer` は JNDI fallback と OTLP sweeper を抱えたままです。
- `opendolphin-reporting` の source は今回の workspace に見当たりません。そこへ修正が波及した時点で blocker として停止します。

## 共通ルール
1. 作業対象の workspace は、親フォルダ配下の **`common/common`** と **`server/server-modernized`** を同時に扱います。  
2. 進捗・blocker・build/test 手順の記録は、**`server/server-modernized/docs/modernization/`** にまとめます。  
3. **編集禁止**: `target/**`、WAR/JAR、`__MACOSX/**`、生成レポート、ビルド成果物。参照はしてよいですが、編集しません。  
4. Flyway の正本は **`server/server-modernized/tools/flyway/sql`** です。migration を追加・変更したときは、**同名・同内容** を `src/main/resources/db/migration` にミラーします。  
5. source がなく、`target/` や WAR/JAR にしか存在しないクラスへ変更が波及したら、その場で停止します。  
6. `opendolphin-reporting` へ波及したら、その場で停止します。  
7. build/test コマンドが再現できないまま実装を進めません。A01 で固定できなければ停止します。  
8. 1 回の automation 実行では、**未完了タスクの先頭 1 件だけ** 実行します。終わったら progress を更新して止めます。  
9. blocker が出た場合は、**推測で続けずに停止**し、`blocker-log.md` に次の4点を書きます。  
   - どのタスクで止まったか  
   - 何が足りないか  
   - どのファイル / 機能に影響するか  
   - 人間が判断すべきことは何か  

## 実行順の要約
1. A系で、作業台・build/test・性格確認テストを固める。  
2. B系で、Touch / legacy / XML 入口と converter 群を落とす。  
3. C系で、`bean_json`、`Date`、`EAGER`、`toDetuch/toPersist` を抜く。  
4. D系で、ORCA 設定と file-based state を近代化する。  
5. E系で、添付保存と PVT 受信の古い構造を分離する。  
6. F系で、metrics の扱いを判断し、最終の回帰と引き継ぎ資料を閉じる。  

## タスク一覧

| 完了 | ID | フェーズ | 目安 | 依存 | 作業項目 |
|---|---|---|---:|---|---|
| [ ] | A00 | 0. 制御面を先に固める | 45分 | - | 作業台を作り、禁止領域と記録場所を固定する |
| [ ] | A01 | 0. 制御面を先に固める | 60分 | A00 | 再現できる build / test 起点を見つけ、使うコマンドを固定する |
| [ ] | A02 | 0. 制御面を先に固める | 60分 | A01 | 性格確認テストの最小セットを固定する |
| [ ] | B00 | 1. 旧入口を閉じる | 60分 | A02 | Touch / legacy / XML 入口の全量を台帳にする |
| [ ] | B01 | 1. 旧入口を閉じる | 60分 | B00 | Touch 専用テストを整理し、削除前の置換テストへ差し替える |
| [ ] | B02 | 1. 旧入口を閉じる | 90分 | B01 | Touch の共通補助層を先に切り離し、削除可能な下位パッケージを消す |
| [ ] | B03 | 1. 旧入口を閉じる | 90分 | B02 | 残った Touch resource と LegacyTouch 抽象層を削除する |
| [ ] | B04 | 1. 旧入口を閉じる | 90分 | B03 | common/converter の利用実態を固め、不要分を削る |
| [ ] | B05 | 1. 旧入口を閉じる | 60分 | B04 | web.xml と descriptor を最小化し、旧公開面を閉じる |
| [ ] | C00 | 2. データモデルの古い芯を抜く | 60分 | B05 | bean_json と payload 種別を全量棚卸しする |
| [ ] | C01 | 2. データモデルの古い芯を抜く | 90分 | C00 | 新しい payload 保存形式と Flyway の骨組みを入れる |
| [ ] | C02 | 2. データモデルの古い芯を抜く | 120分 | C01 | 主要 payload 型から新形式へ書き換える |
| [ ] | C03 | 2. データモデルの古い芯を抜く | 120分 | C02 | 残りの payload 型へ横展開し、ModuleJsonConverter を退役させる |
| [ ] | C04 | 2. データモデルの古い芯を抜く | 90分 | C03 | DocumentModel の toDetuch / toPersist を廃止する |
| [ ] | C05 | 2. データモデルの古い芯を抜く | 120分 | C04 | Date と EAGER を主要モデルから外す |
| [ ] | D00 | 3. 設定と状態保存を近代化する | 60分 | C05 | custom.properties / jboss.home.dir / user.home / 固定パス依存の台帳を作る |
| [ ] | D01 | 3. 設定と状態保存を近代化する | 90分 | D00 | ORCA HTTP 設定を一元化し、custom.properties fallback をやめる |
| [ ] | D02 | 3. 設定と状態保存を近代化する | 90分 | D01 | ORCAConnection から custom.properties file fallback を外す |
| [ ] | D03 | 3. 設定と状態保存を近代化する | 120分 | D02 | AdminConfigStore と MasterUpdateStore を DB-backed state へ移す |
| [ ] | D04 | 3. 設定と状態保存を近代化する | 120分 | D03 | ORCA 接続設定・同期状態・push 重複防止を DB / managed state へ寄せる |
| [ ] | D05 | 3. 設定と状態保存を近代化する | 120分 | D04 | License 保存の置換を試み、根拠不足ならここで止める |
| [ ] | E00 | 4. 添付保存と受信処理を刷新する | 60分 | D04 | Attachment 設定の読み込み規則を一本化する |
| [ ] | E01 | 4. 添付保存と受信処理を刷新する | 90分 | E00 | S3 static credentials をやめる |
| [ ] | E02 | 4. 添付保存と受信処理を刷新する | 120分 | E01 | 添付と画像の I/O をストリーミングへ寄せる |
| [ ] | E03 | 4. 添付保存と受信処理を刷新する | 90分 | E02 | PvtService から受信処理の境界を切り出し、再生テストを足す |
| [ ] | E04 | 4. 添付保存と受信処理を刷新する | 120分 | E03 | socket accept loop を worker-facing 入口へ移す |
| [ ] | F00 | 5. 仕上げと後続整理 | 90分 | E04 | MeterRegistryProducer を単純化できるか判断し、無理なら止める |
| [ ] | F01 | 5. 仕上げと後続整理 | 90分 | F00 | Flyway 正本/ミラー整合、最小回帰、残ブロッカー一覧を閉じる |

## [ ] A00 作業台を作り、禁止領域と記録場所を固定する

- フェーズ: 0. 制御面を先に固める
- 目安: 45分
- 依存: -
- 対象:
  - server/server-modernized/docs/modernization/**（新規）
  - server/server-modernized/tools/flyway/sql/**
  - server/server-modernized/src/main/resources/db/migration/**
  - common/common/target/**, server/server-modernized/target/**, __MACOSX/** は参照のみ
- 目的: Codex が毎回同じ場所へ進捗と blocker を書き、編集してよい場所と触れてはいけない場所を最初に固定する。
- 具体作業:
  1. server/server-modernized/docs/modernization/ を作り、automation-progress.md、blocker-log.md、current-findings.md、build-test-matrix.md を作成する。
  2. current-findings.md に、今回の再レビューで確認した修正対象を箇条書きで固定する。少なくとも Touch 入口、bean_json、DocumentModel の toDetuch/toPersist、UserModel/KarteBean の Date と EAGER、ORCA の custom.properties、ローカル JSON/ファイル保存、添付の static credential / byte[]、PvtService の ServerSocket、MeterRegistryProducer を書く。
  3. automation-progress.md に、1タスク1行で状態、開始時刻、終了時刻、変更ファイル、実行テスト、次タスクを書くテンプレートを作る。
  4. blocker-log.md に、停止条件、停止時に残す内容、再開条件のテンプレートを書く。
  5. target/、WAR/JAR、__MACOSX/、生成レポートを編集禁止と明記する。
- 完了条件:
  - docs/modernization 配下に4つの記録ファイルがあり、以後の作業で毎回そこへ記録できる。
  - 編集禁止領域と Flyway の正本・ミラー規則が文章で固定されている。
- このタスクで止める条件:
  - workspace に common/common と server/server-modernized の両方が見えない。
  - 記録ファイルを書けない読み取り専用 workspace である。
- このタスクで回す確認:
  - コード変更はまだ行わない。必要ならファイル存在確認だけ行う。
- 対応プロンプト: `A00`

## [ ] A01 再現できる build / test 起点を見つけ、使うコマンドを固定する

- フェーズ: 0. 制御面を先に固める
- 目安: 60分
- 依存: A00
- 対象:
  - common/common/pom.xml
  - server/server-modernized/pom.xml
  - server/server-modernized/docs/modernization/build-test-matrix.md
- 目的: 後続タスクが毎回同じ手順で最小テストを回せるようにする。使えるコマンドが曖昧なまま進まない。
- 具体作業:
  1. common/common と server/server-modernized それぞれについて、使える build/test コマンド候補を確認する。
  2. 親 pom 不足、wrapper 不在、依存解決不能などがあれば build-test-matrix.md へそのまま書く。
  3. 通せる最小単位のテスト実行コマンド、compile コマンド、migration 整合性確認コマンドを build-test-matrix.md に固定する。
  4. コマンドが複数候補ある場合は、最短で壊れた範囲が分かるものを primary として 1つに絞る。
- 完了条件:
  - 後続タスクが使う primary build/test コマンドが common と server それぞれで1つ以上定まっている。
  - もし build がまだ再現できないなら、その理由が blocker-log.md に明記され、ここで停止できる。
- このタスクで止める条件:
  - 親 pom や build tool が足りず、再現可能な test / compile コマンドを定義できない。
  - タスクを進める前提の最小テストさえ実行方法が不明である。
- このタスクで回す確認:
  - 可能なら最小の compile または test を 1 回だけ実行し、成功/失敗の事実を記録する。
- 対応プロンプト: `A01`

## [ ] A02 性格確認テストの最小セットを固定する

- フェーズ: 0. 制御面を先に固める
- 目安: 60分
- 依存: A01
- 対象:
  - common/common/src/test/java/open/dolphin/infomodel/ModuleJsonConverterTest.java
  - common/common/src/test/java/open/dolphin/infomodel/TypedDateModelTest.java
  - server/server-modernized/src/test/java/open/dolphin/rest/WebXmlEndpointExposureTest.java
  - server/server-modernized/src/test/java/open/dolphin/rest/KarteResourceDocumentContractTest.java
  - server/server-modernized/src/test/java/open/dolphin/rest/KarteRevisionSnapshotContractTest.java
  - server/server-modernized/src/test/java/open/dolphin/rest/KarteLegacyImagesXmlContractTest.java
  - server/server-modernized/src/test/java/open/dolphin/storage/attachment/AttachmentStorageManagerTest.java
  - server/server-modernized/src/test/java/open/dolphin/orca/config/OrcaConnectionConfigStoreTest.java
  - server/server-modernized/src/test/java/open/dolphin/session/PVTServiceBeanClinicalTest.java
  - server/server-modernized/docs/modernization/build-test-matrix.md
- 目的: 後続の各タスクで、どのテストを最小確認として回すかを先に決める。
- 具体作業:
  1. 既存テストを、Legacy 入口、カルテ/文書、module payload、ORCA 設定、添付、PVT、Flyway の7群に分類する。
  2. 各群ごとに最小 smoke set と、余裕があるときに回す extended set を build-test-matrix.md へ書く。
  3. Legacy を削除するタスクで消す予定のテストは、今の段階では削除せず、後で置換する予定と注記する。
- 完了条件:
  - 各タスクで最低限回すテスト群が build-test-matrix.md に明記されている。
- このタスクで止める条件:
  - どのテストが該当機能を守っているか判断できない。
  - 同名機能のテストが多すぎて最小セットを決められない。
- このタスクで回す確認:
  - A01 で確定した方法で smoke set の dry-run を試せるなら 1 群だけ実行する。
- 対応プロンプト: `A02`

## [ ] B00 Touch / legacy / XML 入口の全量を台帳にする

- フェーズ: 1. 旧入口を閉じる
- 目安: 60分
- 依存: A02
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/touch/**
  - server/server-modernized/src/main/java/open/dolphin/shared/legacytouch/**
  - server/server-modernized/src/main/java/open/dolphin/rest/legacy/**
  - server/server-modernized/src/main/webapp/WEB-INF/web.xml
  - common/common/src/main/java/open/dolphin/converter/**
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: 削除対象を曖昧にせず、Touch と legacy XML の全入口と依存先を一度で見えるようにする。
- 具体作業:
  1. Touch 配下、shared/legacytouch、rest/legacy、web.xml の公開経路、common/converter の利用先を grep または IDE 検索で洗い出す。
  2. current-findings.md に、入口・補助層・converter を『公開入口』『内部補助』『共通変換』の3分類で追記する。
  3. 削除候補と、まだ他経路から参照されていて先に移設が必要なものを分けて記録する。
- 完了条件:
  - Touch / legacy / XML まわりの全量台帳があり、次タスクで削除順を迷わない。
- このタスクで止める条件:
  - 参照が動的で、コード検索だけでは削除安全性を判断できない。
  - 削除すると守るべき主経路まで消える可能性が高いのに、代替経路が見つからない。
- このタスクで回す確認:
  - まだ削除しない。A02 の legacy 群の smoke set を 1 回通し、現状を記録する。
- 対応プロンプト: `B00`

## [ ] B01 Touch 専用テストを整理し、削除前の置換テストへ差し替える

- フェーズ: 1. 旧入口を閉じる
- 目安: 60分
- 依存: B00
- 対象:
  - server/server-modernized/src/test/java/open/dolphin/touch/**
  - server/server-modernized/src/test/java/open/dolphin/rest/WebXmlEndpointExposureTest.java
- 目的: 旧入口を残す前提のテストを削り、旧入口が消えていることを確認するテストへ置き換える。
- 具体作業:
  1. open/dolphin/touch/** のテストを、『Touch の存在を守るだけのテスト』と『共有ロジックを守るテスト』に分ける。
  2. 存在を守るだけのテストは削除候補に回し、WebXmlEndpointExposureTest などの absence test を補強する。
  3. 共有ロジックのテストは、Touch 依存を外しても意味が残るなら、非 Touch パッケージ側へ移す準備だけ行う。
- 完了条件:
  - 以後の Touch 削除で、不要な 410/Gone 前提テストに引きずられない。
- このタスクで止める条件:
  - Touch テストの中に、まだ唯一の業務仕様テストが混ざっていて置換先が決められない。
- このタスクで回す確認:
  - legacy 群 smoke set を実行し、削除・追加したテストだけ確認する。
- 対応プロンプト: `B01`

## [ ] B02 Touch の共通補助層を先に切り離し、削除可能な下位パッケージを消す

- フェーズ: 1. 旧入口を閉じる
- 目安: 90分
- 依存: B01
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/touch/dto/**
  - server/server-modernized/src/main/java/open/dolphin/touch/converter/**
  - server/server-modernized/src/main/java/open/dolphin/touch/transform/**
  - server/server-modernized/src/main/java/open/dolphin/touch/support/**
  - 対応する src/test/java/open/dolphin/touch/**
- 目的: Touch 配下のうち、他の公開経路に直接ぶら下がっていない下位層から先に落とす。
- 具体作業:
  1. B00 の台帳を見て、他パッケージから参照されていない下位パッケージを先に削除する。
  2. もし共有ロジックが残るなら、Touch ではなく server の通常 package へ移してから Touch 側を削除する。
  3. 削除後に import 残骸、未使用依存、死んだ test utility を掃除する。
- 完了条件:
  - Touch の下位パッケージが大きく減り、残るのは上位 resource / service だけになる。
- このタスクで止める条件:
  - 下位パッケージのクラスが ORCA や通常 REST から直接参照されていて、移設先を決めないと進められない。
- このタスクで回す確認:
  - B01 で残した legacy absence test と、共有ロジックへ影響するテストだけ実行する。
- 対応プロンプト: `B02`

## [ ] B03 残った Touch resource と LegacyTouch 抽象層を削除する

- フェーズ: 1. 旧入口を閉じる
- 目安: 90分
- 依存: B02
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/touch/**
  - server/server-modernized/src/main/java/open/dolphin/shared/legacytouch/LegacyTouchAbstractResource.java
  - server/server-modernized/src/main/java/open/dolphin/rest/legacy/LegacyImageXmlWriter.java
  - 対応する src/test/java/**
- 目的: 公開不要な Touch 入口と、それを支える抽象層をコードベースから消す。
- 具体作業:
  1. DolphinResourceASP を含む Touch resource/service を削除する。
  2. shared/legacytouch と rest/legacy の残骸を削除する。
  3. 他の通常 REST から再利用すべき処理だけは明示的に移してから削除する。
  4. 削除後に Touch パッケージが空またはゼロ件になることを確認する。
- 完了条件:
  - Touch と LegacyTouch の実装が src/main/java から消える。
- このタスクで止める条件:
  - Touch の中に、通常 API で今も使っている処理が残っており、移設方針なしでは安全に削除できない。
- このタスクで回す確認:
  - legacy absence test、WebXmlEndpointExposureTest、関連 compile を実行する。
- 対応プロンプト: `B03`

## [ ] B04 common/converter の利用実態を固め、不要分を削る

- フェーズ: 1. 旧入口を閉じる
- 目安: 90分
- 依存: B03
- 対象:
  - common/common/src/main/java/open/dolphin/converter/**
  - server/server-modernized/src/main/java/**
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: 73 個ある converter 群のうち、生きているものと dead code を分ける。
- 具体作業:
  1. converter ごとに利用元を検索し、未使用・Touch 専用・現行 API でまだ使うものへ分類する。
  2. current-findings.md に keep/delete 表を追記する。
  3. 未使用と Touch 専用は削除する。現行 API で必要なものは、用途ごとに明示 mapper を server 側へ作る下準備だけ行う。
- 完了条件:
  - converter 群の keep/delete 方針がファイル単位で決まっている。不要分は削除済みである。
- このタスクで止める条件:
  - converter が業務ロジックそのものを含み、単純削除できない。
  - 利用元が reflection や文字列参照で追えない。
- このタスクで回す確認:
  - カルテ/文書関連の smoke set と compile を実行する。
- 対応プロンプト: `B04`

## [ ] B05 web.xml と descriptor を最小化し、旧公開面を閉じる

- フェーズ: 1. 旧入口を閉じる
- 目安: 60分
- 依存: B04
- 対象:
  - server/server-modernized/src/main/webapp/WEB-INF/web.xml
  - server/server-modernized/src/main/webapp/WEB-INF/jboss-web.xml
  - server/server-modernized/src/main/webapp/WEB-INF/jboss-deployment-structure.xml
  - server/server-modernized/src/main/resources/META-INF/ejb-jar.xml
  - server/server-modernized/src/test/java/open/dolphin/rest/WebXmlEndpointExposureTest.java
- 目的: Touch と legacy XML を消した後に、descriptor 側だけ残る事故を防ぐ。
- 具体作業:
  1. web.xml の resource 列挙、servlet mapping、filter mapping を見直し、消した入口を取り除く。
  2. descriptor の不要な設定を削る。使っているものだけ残す。
  3. WebXmlEndpointExposureTest を更新し、『存在しないこと』を確認する方向に揃える。
- 完了条件:
  - descriptor とコードの公開面が一致している。
- このタスクで止める条件:
  - descriptor 削除で現行主経路まで落ちるが、その根拠がコードだけでは判定できない。
- このタスクで回す確認:
  - WebXmlEndpointExposureTest と関連する REST smoke set を実行する。
- 対応プロンプト: `B05`

## [ ] C00 bean_json と payload 種別を全量棚卸しする

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 60分
- 依存: B05
- 対象:
  - common/common/src/main/java/open/dolphin/infomodel/ModuleModel.java
  - common/common/src/main/java/open/dolphin/infomodel/HealthInsuranceModel.java
  - common/common/src/main/java/open/dolphin/infomodel/ModuleJsonConverter.java
  - common/common/src/test/java/open/dolphin/infomodel/ModuleJsonConverterTest.java
  - server/server-modernized/src/main/java/**
  - server/server-modernized/src/test/java/**
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: bean_json を何が書き、何が読み、どの型が実際に入るかを先に確定する。
- 具体作業:
  1. ModuleModel / HealthInsuranceModel の beanJson 参照箇所を全検索する。
  2. ModuleJsonConverter の decode/serialize 利用箇所と、実際に通る payload 型をテスト・fixture・service から拾う。
  3. payload 種別一覧、HealthInsurance の現行 JSON 形、優先して移す型を current-findings.md に追記する。
- 完了条件:
  - 移行対象の payload 型一覧と優先順が書面で固定されている。
- このタスクで止める条件:
  - payload 型が動的すぎて、コードとテストだけでは代表型を決められない。
- このタスクで回す確認:
  - ModuleJsonConverterTest を現状の性格確認として実行する。
- 対応プロンプト: `C00`

## [ ] C01 新しい payload 保存形式と Flyway の骨組みを入れる

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 90分
- 依存: C00
- 対象:
  - server/server-modernized/tools/flyway/sql/**（新規 migration）
  - server/server-modernized/src/main/resources/db/migration/**（ミラー）
  - common/common/src/main/java/open/dolphin/infomodel/** または新設 payload model
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: bean_json をやめる受け皿を先に作る。正本とミラーの2か所を同期して追加する。
- 具体作業:
  1. C00 の棚卸しに基づき、新しい保存形式を version 付き構造化 JSON か payload 専用テーブルのどちらかへ決める。
  2. Flyway migration を tools/flyway/sql に追加し、同名・同内容を src/main/resources/db/migration にミラーする。
  3. current-findings.md に採用した構造、列、互換方針なしで進めることを書く。
- 完了条件:
  - 新しい保存形式の器と migration が repo 上に存在する。
  - Flyway 正本とミラーのファイル名・内容が一致している。
- このタスクで止める条件:
  - C00 で payload 型一覧が固まらず、受け皿設計が決められない。
  - migration を追加すると既存 schema と明らかに衝突するが、根拠ある解決策が見えない。
- このタスクで回す確認:
  - FlywayMigrationConsistencyTest または A01 で確定した migration 整合性確認を実行する。
- 対応プロンプト: `C01`

## [ ] C02 主要 payload 型から新形式へ書き換える

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 120分
- 依存: C01
- 対象:
  - common/common/src/main/java/open/dolphin/infomodel/ModuleModel.java
  - common/common/src/main/java/open/dolphin/infomodel/ModuleJsonConverter.java または後継 mapper
  - server/server-modernized/src/main/java/** module 読み書き箇所
  - 関連テスト
- 目的: 最も頻度の高い 2 型から、新形式で保存・読出しできる状態にする。
- 具体作業:
  1. C00 で優先度が高かった payload 型 2 つを選び、新形式での serialize / deserialize / 永続化を実装する。
  2. 既存の default typing に依存せず、型は明示フィールドか専用テーブルで表す。
  3. 旧 beanJson 読取を残さない前提なので、使うコードを新形式へ切り替える。
  4. 必要なら移行補助コードまたは one-shot converter を追加する。
- 完了条件:
  - 主要 2 型が新形式で round-trip できる。
  - その 2 型の通り道では default typing に依存しない。
- このタスクで止める条件:
  - 主要型を新形式へ写すと、関連 API の期待形が不明になる。
  - 型ごとの構造差が大きく、2 時間で安全に 2 型へ絞れない。
- このタスクで回す確認:
  - ModuleJsonConverterTest を置換した後継テスト、カルテ/文書系 smoke set を実行する。
- 対応プロンプト: `C02`

## [ ] C03 残りの payload 型へ横展開し、ModuleJsonConverter を退役させる

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 120分
- 依存: C02
- 対象:
  - common/common/src/main/java/open/dolphin/infomodel/ModuleJsonConverter.java
  - common/common/src/main/java/open/dolphin/infomodel/HealthInsuranceModel.java
  - server/server-modernized/src/main/java/**
  - 関連 migration / tests
- 目的: bean_json と activateDefaultTyping 依存を仕上げて外す。
- 具体作業:
  1. C02 で作った形式を残りの payload 型へ広げる。
  2. HealthInsuranceModel の beanJson も同じ方針で置換する。
  3. ModuleJsonConverter が不要になったら削除する。まだ必要でも default typing は完全に外す。
  4. 旧 bean_json 列の利用箇所を検索し、残存ゼロまたは残存理由明記のどちらかへする。
- 完了条件:
  - bean_json 利用箇所が実運用コードから消えるか、残件が明示される。
  - activateDefaultTyping を使う経路が消える。
- このタスクで止める条件:
  - 残り型の中に source 不在の型が含まれ、実装の根拠が取れない。
  - HealthInsurance の新形式が既存業務ロジックへどう渡るか不明で、テストも資料もない。
- このタスクで回す確認:
  - module / health insurance 関連テスト、カルテ smoke set、Flyway 整合性確認を実行する。
- 対応プロンプト: `C03`

## [ ] C04 DocumentModel の toDetuch / toPersist を廃止する

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 90分
- 依存: C03
- 対象:
  - common/common/src/main/java/open/dolphin/infomodel/DocumentModel.java
  - server/server-modernized/src/main/java/** DocumentModel 利用箇所
  - 関連テスト
- 目的: entity 自身が API 都合の相互変換を持つ古い形をやめる。
- 具体作業:
  1. toDetuch() / toPersist() の呼び出し元を全検索する。
  2. 必要な変換は service / mapper 側へ明示的に移す。
  3. 呼び出し元が消えたことを確認してから DocumentModel の 2 メソッドを削除する。
- 完了条件:
  - DocumentModel から toDetuch / toPersist が消え、変換責務が外へ出る。
- このタスクで止める条件:
  - 呼び出しが予想外に多く、同一タスク内で安全に移せる範囲を超える。
- このタスクで回す確認:
  - KarteResourceDocumentContractTest、KarteRevisionSnapshotContractTest、関連 session テストを実行する。
- 対応プロンプト: `C04`

## [ ] C05 Date と EAGER を主要モデルから外す

- フェーズ: 2. データモデルの古い芯を抜く
- 目安: 120分
- 依存: C04
- 対象:
  - common/common/src/main/java/open/dolphin/infomodel/UserModel.java
  - common/common/src/main/java/open/dolphin/infomodel/KarteBean.java
  - 必要に応じて DTO / serializer / mapper
  - common/common/src/test/java/open/dolphin/infomodel/TypedDateModelTest.java
  - server/server-modernized/src/test/java/**
- 目的: UserModel と KarteBean の古い日時型と EAGER fetch を先に減らす。
- 具体作業:
  1. UserModel.registeredDate、KarteBean.created、KarteBean.lastDocDate を用途に応じた java.time 型へ変える。
  2. UserModel.roles の EAGER を見直し、必要な取得は query 側で明示する。
  3. DTO / JSON / DB マッピングとテストを合わせて直す。
- 完了条件:
  - 対象フィールドが Date から外れ、roles の EAGER が消えている。
  - 日時の入出力テストが通る。
- このタスクで止める条件:
  - DB カラムや JSON 形の確定根拠がなく、型変換を決め打ちできない。
- このタスクで回す確認:
  - TypedDateModelTest、カルテ/ユーザー関連 smoke set を実行する。
- 対応プロンプト: `C05`

## [ ] D00 custom.properties / jboss.home.dir / user.home / 固定パス依存の台帳を作る

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 60分
- 依存: C05
- 対象:
  - server/server-modernized/src/main/java/open/orca/rest/ORCAConnection.java
  - server/server-modernized/src/main/java/open/dolphin/orca/transport/OrcaTransportSettings.java
  - server/server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageConfigLoader.java
  - server/server-modernized/src/main/java/open/dolphin/orca/support/PushEventDeduplicator.java
  - server/server-modernized/src/main/java/open/dolphin/system/license/FileLicenseRepository.java
  - server/server-modernized/src/main/java/open/dolphin/rest/admin/AdminConfigStore.java
  - server/server-modernized/src/main/java/open/dolphin/rest/masterupdate/MasterUpdateStore.java
  - server/server-modernized/src/main/java/open/dolphin/orca/config/OrcaConnectionConfigStore.java
  - server/server-modernized/src/main/java/open/dolphin/orca/sync/OrcaPatientSyncStateStore.java
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: 古い設定・状態保存を一気に直す前に、残件を漏れなく固定する。
- 具体作業:
  1. 上記クラスの固定パス、home dir、properties、ローカル JSON 保存を洗い出す。
  2. current-findings.md に、設定値・保存先・置換先候補を表で追記する。
  3. この後の D01-D04 でどの順に直すかを current-findings.md に明記する。
- 完了条件:
  - 固定パス依存と file-based state の全量表がある。
- このタスクで止める条件:
  - コード外の設定ファイルにしか意味がなく、置換先候補を決める根拠がない。
- このタスクで回す確認:
  - まだコード変更は最小限。必要なら ORCA 設定系テストを dry-run する。
- 対応プロンプト: `D00`

## [ ] D01 ORCA HTTP 設定を一元化し、custom.properties fallback をやめる

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 90分
- 依存: D00
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/orca/transport/OrcaTransportSettings.java
  - server/server-modernized/src/main/java/open/dolphin/orca/transport/RestOrcaTransport.java
  - server/server-modernized/config/server-modernized.env.sample
  - 関連テスト
- 目的: ORCA HTTP 設定の入口を env / 管理設定へ寄せ、custom.properties 読みを切る。
- 具体作業:
  1. OrcaTransportSettings から custom.properties 由来の読み込みを除去する。
  2. RestOrcaTransport がその新しい解決経路だけを使うようにする。
  3. env.sample や README に残る custom.properties fallback の説明を削る。
  4. 必要な precedence をテストで固定する。
- 完了条件:
  - ORCA HTTP 設定が custom.properties に依存しない。
- このタスクで止める条件:
  - 管理設定と env のどちらを優先するか、コードと既存テストだけでは決められない。
- このタスクで回す確認:
  - OrcaTransportSettingsSecurityPolicyTest、OrcaHttpClientRequestTest、関連 ORCA smoke set を実行する。
- 対応プロンプト: `D01`

## [ ] D02 ORCAConnection から custom.properties file fallback を外す

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 90分
- 依存: D01
- 対象:
  - server/server-modernized/src/main/java/open/orca/rest/ORCAConnection.java
  - 関連テストまたは新設テスト
- 目的: DB 接続側も file fallback をやめ、JNDI / env / managed config だけにする。
- 具体作業:
  1. ORCAConnection の constructor で custom.properties を読む処理を削除する。
  2. sensitive property のブロックや audit は、新しい設定入口に沿う形で整理する。
  3. file fallback が消えたことをテストまたは明示的な absence assertion で固定する。
- 完了条件:
  - ORCAConnection が jboss.home.dir/custom.properties を触らない。
- このタスクで止める条件:
  - JNDI / env 前提で置き換えると、既存の主経路がどう初期化されるか根拠が足りない。
- このタスクで回す確認:
  - ORCA 関連 smoke set と、設定系の最小テストを実行する。
- 対応プロンプト: `D02`

## [ ] D03 AdminConfigStore と MasterUpdateStore を DB-backed state へ移す

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 120分
- 依存: D02
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/rest/admin/AdminConfigStore.java
  - server/server-modernized/src/main/java/open/dolphin/rest/masterupdate/MasterUpdateStore.java
  - server/server-modernized/tools/flyway/sql/**（新規 migration）
  - server/server-modernized/src/main/resources/db/migration/**（ミラー）
  - 必要なら新設 state repository
- 目的: ローカル JSON 永続化をやめ、複数ノードや再配備に耐える状態保存へ移す。
- 具体作業:
  1. runtime state 用の repository とテーブル設計を追加する。
  2. AdminConfigStore と MasterUpdateStore をその repository 経由へ置き換える。
  3. 既存 JSON を読む fallback は残さない前提なので、必要なら one-shot migration 補助だけ用意する。
- 完了条件:
  - 2つの Store が Files / Path / JSON ファイルへ依存しない。
- このタスクで止める条件:
  - 状態モデルが大きく違いすぎて、1 タスクで 2 Store を安全にまとめられない。
- このタスクで回す確認:
  - AdminAccessResourceTest、AdminMasterUpdateResourceTest、Flyway 整合性確認を実行する。
- 対応プロンプト: `D03`

## [ ] D04 ORCA 接続設定・同期状態・push 重複防止を DB / managed state へ寄せる

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 120分
- 依存: D03
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/orca/config/OrcaConnectionConfigStore.java
  - server/server-modernized/src/main/java/open/dolphin/orca/sync/OrcaPatientSyncStateStore.java
  - server/server-modernized/src/main/java/open/dolphin/orca/support/PushEventDeduplicator.java
  - 必要な migration / repository / tests
- 目的: ORCA 周辺の state をノードローカルから外す。
- 具体作業:
  1. D03 で作った state repository を拡張し、OrcaConnectionConfigStore と OrcaPatientSyncStateStore を DB-backed にする。
  2. PushEventDeduplicator の user.home キャッシュをやめ、同じ repository か少なくとも外部管理された保存先へ寄せる。
  3. メモリのみ運用に一時退避する場合は、current-findings.md に理由と再開条件を書く。
- 完了条件:
  - ORCA 周辺 state が user.home / ローカル JSON に依存しない。
- このタスクで止める条件:
  - event dedup の保持期間や整合性ルールが、コードと tests だけでは確定できない。
- このタスクで回す確認:
  - OrcaConnectionConfigStoreTest、OrcaPatientSyncServiceTest、関連 ORCA smoke set を実行する。
- 対応プロンプト: `D04`

## [ ] D05 License 保存の置換を試み、根拠不足ならここで止める

- フェーズ: 3. 設定と状態保存を近代化する
- 目安: 120分
- 依存: D04
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/system/license/FileLicenseRepository.java
  - 関連呼び出し元
  - 必要なら state repository / migration
- 目的: license.properties を jboss.home.dir に置く前提をなくす。ただし業務ルールが不明なら無理に進めない。
- 具体作業:
  1. LicenseRepository の利用元を洗い出し、保存・読込の業務ルールをコードから把握する。
  2. D03/D04 の state repository に移せるなら置換する。
  3. もし有効期限・秘密情報・運用手順が不明で安全に置換できないなら、blocker-log.md に理由と必要な人間判断を残して停止する。
- 完了条件:
  - 置換できた場合は FileLicenseRepository が不要になる。
  - 置換できない場合も、曖昧なまま先へ進まない。
- このタスクで止める条件:
  - ライセンス保存仕様がコードから読み解けず、置換先を決める根拠がない。
- このタスクで回す確認:
  - ライセンス関連の最小テスト。無ければ呼び出し元の smoke set を実行する。
- 対応プロンプト: `D05`

## [ ] E00 Attachment 設定の読み込み規則を一本化する

- フェーズ: 4. 添付保存と受信処理を刷新する
- 目安: 60分
- 依存: D04
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageConfigLoader.java
  - server/server-modernized/config/attachment-storage.sample.yaml
  - server/server-modernized/config/server-modernized.env.sample
- 目的: attachment-storage.yaml 固定パス頼みをやめ、env / secret / DB 設定の優先順位を一つにする。
- 具体作業:
  1. AttachmentStorageConfigLoader の DEFAULT_CONFIG_PATH 依存を外す。
  2. 設定値の優先順位を env → secret / managed config → sample fallback など 1 本に決める。
  3. sample ファイルとドキュメントをその規則に合わせて直す。
- 完了条件:
  - 固定の /opt/jboss/config/attachment-storage.yaml が必須ではなくなる。
- このタスクで止める条件:
  - 設定の正しい優先順位をコードと docs だけでは決められない。
- このタスクで回す確認:
  - AttachmentStorageManagerTest または設定系の最小テストを実行する。
- 対応プロンプト: `E00`

## [ ] E01 S3 static credentials をやめる

- フェーズ: 4. 添付保存と受信処理を刷新する
- 目安: 90分
- 依存: E00
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageManager.java
  - server/server-modernized/src/main/java/open/dolphin/storage/image/ImageStorageManager.java
  - 関連設定ファイル / tests
- 目的: access key / secret key をコード近傍で固定しない。
- 具体作業:
  1. AttachmentStorageManager と ImageStorageManager から StaticCredentialsProvider + AwsBasicCredentials を除去する。
  2. デフォルト provider chain、env、または secret 管理から取得する形へ寄せる。
  3. 必要な設定項目だけを sample に残し、不要な access key / secret key の必須扱いを外す。
- 完了条件:
  - S3 認証が static credentials 前提でなくなる。
- このタスクで止める条件:
  - 実行環境の想定 provider が一切分からず、安全な既定値を決められない。
- このタスクで回す確認:
  - AttachmentStorageManagerTest、画像/添付の smoke set を実行する。
- 対応プロンプト: `E01`

## [ ] E02 添付と画像の I/O をストリーミングへ寄せる

- フェーズ: 4. 添付保存と受信処理を刷新する
- 目安: 120分
- 依存: E01
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageManager.java
  - server/server-modernized/src/main/java/open/dolphin/storage/image/ImageStorageManager.java
  - 関連モデル / tests
- 目的: 大きいファイルで byte[] を丸ごと抱える経路を減らす。
- 具体作業:
  1. download / upload の happy path で IoUtils.toByteArray や RequestBody.fromBytes を使っている箇所を洗い出す。
  2. InputStream / streaming body を使う経路へ置き換える。
  3. 本当に byte[] が必要な境界だけを最小限残し、残した理由を current-findings.md に書く。
- 完了条件:
  - 添付と画像の主要 I/O が全件 byte[] 前提ではなくなる。
- このタスクで止める条件:
  - AttachmentModel / SchemaModel の API 契約が byte[] 固定で、周辺を広く直さないと安全に置換できない。
- このタスクで回す確認:
  - AttachmentStorageManagerTest、PatientImagesResourceTest、関連 smoke set を実行する。
- 対応プロンプト: `E02`

## [ ] E03 PvtService から受信処理の境界を切り出し、再生テストを足す

- フェーズ: 4. 添付保存と受信処理を刷新する
- 目安: 90分
- 依存: E02
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/mbean/PvtService.java
  - server/server-modernized/src/main/java/open/dolphin/session/PVTServiceBean.java
  - server/server-modernized/src/test/java/open/dolphin/mbean/PVTBuilderTest.java
  - server/server-modernized/src/test/java/open/dolphin/session/PVTServiceBeanClinicalTest.java
  - 必要なら replay 用 test utility
- 目的: まず socket loop と業務処理を分離し、受信内容を再生できるようにする。
- 具体作業:
  1. PvtService の accept loop・データ読取・PVTServiceBean への受け渡し境界を分ける。
  2. PVT 入力サンプルから処理を再生するテストを追加する。
  3. この段階では socket 自体を外へ出し切らなくてよい。境界の抽出を優先する。
- 完了条件:
  - PvtService の内部責務が分かれ、受信内容を socket なしで再生できる。
- このタスクで止める条件:
  - PVT 処理の入力フォーマットがコードだけでは再現し切れず、再生テストを作れない。
- このタスクで回す確認:
  - PVTBuilderTest、PVTServiceBeanClinicalTest、関連 PVT smoke set を実行する。
- 対応プロンプト: `E03`

## [ ] E04 socket accept loop を worker-facing 入口へ移す

- フェーズ: 4. 添付保存と受信処理を刷新する
- 目安: 120分
- 依存: E03
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/mbean/PvtService.java
  - 必要なら新設 worker / ingestion package
  - 関連 tests / docs
- 目的: 本体アプリが ServerSocket を直接抱える形をやめる準備を終える。
- 具体作業:
  1. E03 で切った境界を使い、socket accept loop を worker-facing entrypoint へ押し出す。
  2. 本体側は受け取ったメッセージを処理するサービスへ寄せる。
  3. build 構造上、新しい worker 入口を安全に置けない場合は、blocker-log.md に理由を書いて停止する。
- 完了条件:
  - PvtService から ServerSocket の責務が外れるか、外せない理由が blocker として明確になる。
- このタスクで止める条件:
  - 今の build / packaging では worker 入口の配置を安全に定義できない。
  - socket accept loop を外すと、現行の起動方式と整合しないが根拠ある代替がない。
- このタスクで回す確認:
  - PVT 関連 smoke set と、A01 で決めた compile / package の最小確認を実行する。
- 対応プロンプト: `E04`

## [ ] F00 MeterRegistryProducer を単純化できるか判断し、無理なら止める

- フェーズ: 5. 仕上げと後続整理
- 目安: 90分
- 依存: E04
- 対象:
  - server/server-modernized/src/main/java/open/dolphin/metrics/MeterRegistryProducer.java
  - 関連 docs / tests
- 目的: JNDI fallback と OTLP sweeper の複雑さを減らしたいが、運用根拠が薄いなら無理に触らない。
- 具体作業:
  1. 今の target runtime で必要な registry 取得方法をコードと docs から確認する。
  2. 安全に単純化できる根拠がある場合だけ、JNDI fallback / OTLP sweeper を減らす。
  3. 根拠が薄い場合は blocker-log.md に『運用前提不明のため保留』と書いて停止する。
- 完了条件:
  - 単純化できた場合は実装が短くなる。できない場合は保留理由が明確になる。
- このタスクで止める条件:
  - 運用環境の Micrometer / OTLP 前提がコードと docs だけでは確定できない。
- このタスクで回す確認:
  - metrics 関連の最小 compile と smoke check を行う。
- 対応プロンプト: `F00`

## [ ] F01 Flyway 正本/ミラー整合、最小回帰、残ブロッカー一覧を閉じる

- フェーズ: 5. 仕上げと後続整理
- 目安: 90分
- 依存: F00
- 対象:
  - server/server-modernized/tools/flyway/sql/**
  - server/server-modernized/src/main/resources/db/migration/**
  - server/server-modernized/docs/modernization/automation-progress.md
  - server/server-modernized/docs/modernization/blocker-log.md
  - server/server-modernized/docs/modernization/current-findings.md
- 目的: ここまでの修正を、人がすぐ引き継げる形で閉じる。
- 具体作業:
  1. Flyway の正本とミラーでファイル名・内容差分がないか確認する。
  2. A02 で決めた smoke set を通し、結果を automation-progress.md にまとめる。
  3. 未解決 blocker、source 不足で保留した項目、次の人間判断が必要な項目を blocker-log.md に整理する。
  4. current-findings.md を最終状態へ更新し、残件を短くまとめる。
- 完了条件:
  - 引き継ぎに必要な進捗、実行結果、残ブロッカーが docs/modernization にまとまっている。
- このタスクで止める条件:
  - smoke set の多くが未実行で、現状の安全性を説明できない。
- このタスクで回す確認:
  - A02 の smoke set を群ごとに実行し、結果を記録する。
- 対応プロンプト: `F01`
