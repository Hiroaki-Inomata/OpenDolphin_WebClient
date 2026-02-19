# major-category rerun checklist result

- RUN_ID: 20260218T064159Z-cmd_20260218_01_sub_28-major-category-rerun-r4
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T06:42:20.965Z
- fixedOrder: prescription -> injection -> test -> procedure -> charge
- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).
- overall: fail
- passCount/failCount: 4/1
- failure context: logs/failure-context.json

|order|category|HTTP|totalCount|request|queryEnabled|candidate|selected|reflected|status|traceId|blockedAt|screenshot|
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
|1|prescription|-|-|no|no|no|no|no|fail|-|ui_operation|screenshots/01-prescription-error.png|
|2|injection|200|1|yes|yes|yes|yes|yes|pass|-|-|screenshots/02-injection.png|
|3|test|200|1|yes|yes|yes|yes|yes|pass|-|-|screenshots/03-test.png|
|4|procedure|200|1|yes|yes|yes|yes|yes|pass|-|-|screenshots/04-procedure.png|
|5|charge|200|1|yes|yes|yes|yes|yes|pass|-|-|screenshots/05-charge.png|

## event-chain diagnosis
- single root cause (sub_26): verification scenario used keyword/observation conditions that did not align with live snapshot data, so non-selection was misread as API non-fire.
- minimal fix (sub_28): use order-dock quick-add ids + request/response/requestfailed hooks + per-category fresh load to remove false negatives.

## logs
- logs/master-responses.ndjson
- logs/console.log
- logs/failure-context.json