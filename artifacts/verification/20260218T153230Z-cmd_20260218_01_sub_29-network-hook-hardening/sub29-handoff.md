# cmd_20260218_01_sub_29 handoff for ashigaru2

- Added network-capture hardening to `major-category-rerun-check.mjs`:
  - request-first hook (`waitForRequest`)
  - request->response binding (`request.response()`)
  - response timeline fallback (`atMs` gating)
  - deterministic input trigger (`fill('')` -> `fill(keyword)`)
- Operational checklist prepared: `checklist.md` (same folder).

Use the checklist verbatim before rerunning sub_8 evidence capture.
