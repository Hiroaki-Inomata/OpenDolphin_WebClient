# major-category rerun checklist result

- RUN_ID: 20260218T064107Z-cmd_20260218_01_sub_28-major-category-rerun-r3
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T06:41:11.976Z
- fixedOrder: prescription -> injection -> test -> procedure -> charge
- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).
- overall: fail
- passCount/failCount: 0/5
- failure context: logs/failure-context.json

|order|category|HTTP|totalCount|request|queryEnabled|candidate|selected|reflected|status|traceId|blockedAt|screenshot|
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
|1|prescription|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/01-prescription-error.png|
|2|injection|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/02-injection-error.png|
|3|test|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/03-test-error.png|
|4|procedure|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/04-procedure-error.png|
|5|charge|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/05-charge-error.png|

## event-chain diagnosis
- single root cause (sub_26): verification scenario used keyword/observation conditions that did not align with live snapshot data, so non-selection was misread as API non-fire.
- minimal fix (sub_28): enforce per-category fresh load and capture request+response+requestfailed; verify input/debounce/queryEnabled/requestSeen before candidate selection.

## logs
- logs/master-responses.ndjson
- logs/console.log
- logs/failure-context.json