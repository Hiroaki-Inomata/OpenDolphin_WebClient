# major-category rerun checklist result

- RUN_ID: 20260218T063900Z-cmd_20260218_01_sub_28-major-category-rerun
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T06:39:04.399Z
- fixedOrder: prescription -> injection -> test -> procedure -> charge
- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).
- overall: fail
- passCount/failCount: 1/4
- failure context: logs/failure-context.json

|order|category|HTTP|totalCount|request|queryEnabled|candidate|selected|reflected|status|traceId|blockedAt|screenshot|
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
|1|prescription|200|1|yes|yes|yes|yes|yes|pass|-|-|screenshots/01-prescription.png|
|2|injection|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/02-injection-error.png|
|3|test|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/03-test-error.png|
|4|procedure|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/04-procedure-error.png|
|5|charge|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/05-charge-error.png|

## event-chain diagnosis
- single root cause (sub_26): verification step used non-matching keywords for current server dataset and only waited on response, so it misclassified as non-fire when no candidate path continued.
- minimal fix (sub_28): use server-hit keyword set and capture request+response+requestfailed in one hook; assert debounce/queryEnabled/requestSeen before candidate selection.

## logs
- logs/master-responses.ndjson
- logs/console.log
- logs/failure-context.json