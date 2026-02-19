# major-category rerun checklist result

- RUN_ID: 20260218T060946Z-cmd_20260218_01_sub_18-major-category-rerun-r2
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T06:11:55.444Z
- fixedOrder: prescription -> injection -> test -> procedure -> charge
- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).
- overall: fail
- passCount/failCount: 0/5
- failure context: logs/failure-context.json

|order|category|HTTP|candidate|selected|reflected|status|traceId|blockedAt|screenshot|
|---:|---|---:|---:|---:|---:|---|---|---|---|
|1|prescription|-|no|no|no|fail|-|master_api|screenshots/01-prescription.png|
|2|injection|-|no|no|no|fail|-|master_api|screenshots/02-injection.png|
|3|test|-|no|no|no|fail|-|master_api|screenshots/03-test.png|
|4|procedure|-|no|no|no|fail|-|master_api|screenshots/04-procedure.png|
|5|charge|-|no|no|no|fail|-|master_api|screenshots/05-charge.png|

## fixed failure logs
- logs/master-responses.ndjson
- logs/console.log
- logs/failure-context.json
