# cmd_20260218_01_sub_3g 401 trace analysis (gunshi)

## Objective
Track 401 traces in `server-modernized` logs, identify where auth values that differ from the connected account are injected, and produce traceId-based outcome comparison before/after attempted fixes.

## Evidence
- Request header capture: `request_header_capture.json`
- Request replay script: `trace_master_headers.mjs`
- Captured request trace outcome: `request_trace_outcome.tsv`
- Audit rows (selected traces): `audit_failure_rows.tsv`
- Before/after trace comparison: `trace_outcome_comparison.tsv`
- Server log extract: `server_log_extract.log`

## Key Findings
1. **Auth values are injected in frontend order master search path**
   - `web-client/src/features/charts/orderMasterSearchApi.ts:100` hardcodes `ORCA_MASTER_USER` default to `1.3.6.1.4.1.9414.70.1:admin`.
   - `web-client/src/features/charts/orderMasterSearchApi.ts:103` hardcodes `ORCA_MASTER_PASSWORD` default to `21232f297a57a5a743894a0e4a801fc3`.
   - `web-client/src/features/charts/orderMasterSearchApi.ts:217` always sends `headers: buildMasterAuthHeaders()` to `/orca/master/*`.
   - Captured wire request (`request_header_capture.json`) confirms:
     - `Authorization: Basic ...` decodes to `1.3.6.1.4.1.9414.70.1:admin:21232f297a57a5a743894a0e4a801fc3`
     - `X-Facility-Id: 1.3.6.1.4.1.9414.70.1`
   - This differs from the connected Charts session actor (`1.3.6.1.4.1.9414.72.103:doctor1`) seen in concurrent audit events.

2. **Filter-side Basic parser fails for composite username format**
   - `server-modernized/src/main/java/open/dolphin/rest/LogFilter.java:376` uses `decoded.indexOf(':')` (first colon split).
   - For `Authorization` payload `facility:user:password`, this yields `rawUser=facility`, `rawPass=user:password`, causing auth failure and `REST_UNAUTHORIZED_GUARD` with actor `anonymous`.
   - Replayed trace `6cd6eb20-e1fa-4713-a7c7-9e3fe3b3f42a` demonstrates this exact failure (`request_trace_outcome.tsv`).

3. **Master-resource auth expects fixed master credentials by default**
   - `server-modernized/src/main/java/open/orca/rest/OrcaMasterAuthSupport.java:13-14` defines defaults `1.3.6.1.4.1.9414.70.1:admin` / `21232...`.
   - `OrcaMasterAuthSupport.java:36-46` compares request credentials against those expected values.

## Trace Outcome Comparison (before/after)
Comparison table is in `trace_outcome_comparison.tsv`.

Summary:
- Keyword retest traces remain `REST_UNAUTHORIZED_GUARD / FAILURE / actor=anonymous` before and after attempted fixes.
- UI-triggered generic-class trace changed from:
  - **before** `68da5dbc-...`: `REST_ERROR_RESPONSE` with actor `1.3.6.1.4.1.9414.72.103:doctor1` (http_401)
  - **after** `6a185227-...`: `REST_UNAUTHORIZED_GUARD` with actor `anonymous`, `facilityIdHeader=1.3.6.1.4.1.9414.70.1`
- This indicates auth injection shifted toward hardcoded master headers while filter authentication still rejects the composed Basic payload.

## Injection Point (answer)
The mismatch value injection point is **frontend master search request construction**:
- `web-client/src/features/charts/orderMasterSearchApi.ts` (`buildMasterAuthHeaders` + forced header injection at request callsite).

At runtime, this injected account then interacts with filter parsing behavior in:
- `server-modernized/src/main/java/open/dolphin/rest/LogFilter.java` (`decoded.indexOf(':')`), producing 401 unauthorized guard traces.
