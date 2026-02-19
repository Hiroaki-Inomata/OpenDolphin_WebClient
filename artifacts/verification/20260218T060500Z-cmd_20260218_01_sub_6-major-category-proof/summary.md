# cmd_20260218_01_sub_6 major category recheck

- RUN_ID: 20260218T060500Z-cmd_20260218_01_sub_6-major-category-proof
- Base URL: http://localhost:5173
- Data state: OK

|category|status|HTTP|traceId(header)|traceId(body)|totalCount|selected|reflected|request|evidence|
|---|---|---:|---|---|---:|---:|---:|---|---|
|prescription|pass|200|b16f9096-8489-4875-a43b-ceb98b95817e|-|1|yes|yes|http://localhost:5173/orca/master/generic-class?keyword=%E4%B8%AD%E6%9E%A2&page=1&size=20|screenshots/prescription_pass.png|
|injection|pass|200|b16f9096-8489-4875-a43b-ceb98b95817e|-|1|yes|yes|http://localhost:5173/orca/master/generic-class?keyword=%E4%B8%AD%E6%9E%A2&page=1&size=20|screenshots/injection_pass.png|
|procedure|fail|-|-|-|-|no|no|-|screenshots/procedure_error.png|
|test|fail|-|-|-|-|no|no|-|screenshots/test_error.png|
|charge|fail|-|-|-|-|no|no|-|screenshots/charge_error.png|