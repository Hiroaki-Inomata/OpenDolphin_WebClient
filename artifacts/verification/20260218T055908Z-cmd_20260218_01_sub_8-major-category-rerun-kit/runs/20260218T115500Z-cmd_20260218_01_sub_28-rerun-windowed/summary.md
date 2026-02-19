# major-category rerun checklist result

- RUN_ID: 20260218T115500Z-cmd_20260218_01_sub_28-rerun-windowed
- Base URL: http://localhost:5173
- executedAt: 2026-02-18T11:56:34.446Z
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

## triage (environment vs ui)

|category|suspectedCause|failureReason|retryDecision|requestStartedAt|requestEndedAt|durationMs|
|---|---|---|---|---|---|---:|
|prescription|ui|ui_disabled|retry_recommended|-|-|-|
|injection|ui|ui_disabled|retry_recommended|-|-|-|
|test|environment|timeout|retry_recommended|-|-|-|
|procedure|environment|timeout|retry_recommended|-|-|-|
|charge|environment|timeout|retry_recommended|-|-|-|

## fixed failure logs
- logs/master-responses.ndjson
- logs/master-diagnostics.ndjson
- logs/console.log
- logs/failure-context.json
