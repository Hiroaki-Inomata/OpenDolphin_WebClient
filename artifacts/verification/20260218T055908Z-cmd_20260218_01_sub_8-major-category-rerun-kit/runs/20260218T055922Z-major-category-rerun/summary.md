# major-category rerun checklist result

- RUN_ID: 20260218T055922Z-major-category-rerun
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T06:00:41.214Z
- fixedOrder: prescription -> injection -> test -> procedure -> charge
- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).
- overall: fail
- passCount/failCount: 0/5
- failure context: logs/failure-context.json

|order|category|HTTP|candidate|selected|reflected|status|traceId|blockedAt|screenshot|
|---:|---|---:|---:|---:|---:|---|---|---|---|
|1|prescription|-|no|no|no|fail|-|ui_operation|screenshots/01-prescription-error.png|
|2|injection|-|no|no|no|fail|-|ui_operation|screenshots/02-injection-error.png|
|3|test|-|no|no|no|fail|-|ui_operation|screenshots/03-test-error.png|
|4|procedure|-|no|no|no|fail|-|ui_operation|screenshots/04-procedure-error.png|
|5|charge|-|no|no|no|fail|-|ui_operation|screenshots/05-charge-error.png|

## fixed failure logs
- logs/master-responses.ndjson
- logs/console.log
- logs/failure-context.json
