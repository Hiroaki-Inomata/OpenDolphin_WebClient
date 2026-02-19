#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:5173}"

# Login API candidates (/api/user/{facilityId}:{userId})
curl -sS -D api_user_doctor.headers.txt -o api_user_doctor.body.json \
  -u 'doctor1:doctor2025' \
  -H 'X-Facility-Id: 1.3.6.1.4.1.9414.72.103' \
  "$BASE/api/user/1.3.6.1.4.1.9414.72.103:doctor1"

curl -sS -D api_user_ormaster.headers.txt -o api_user_ormaster.body.json \
  -u 'ormaster:change_me' \
  -H 'X-Facility-Id: 1.3.6.1.4.1.9414.10.1' \
  "$BASE/api/user/1.3.6.1.4.1.9414.10.1:ormaster"

# ORCA master generic-class (legacy headers expected by proxy)
curl -sS -D generic_class.headers.txt -o generic_class.body.json \
  -H 'userName: 1.3.6.1.4.1.9414.70.1:admin' \
  -H 'password: 21232f297a57a5a743894a0e4a801fc3' \
  "$BASE/orca/master/generic-class?keyword=%E3%82%A2%E3%83%A0&page=1&size=50"
