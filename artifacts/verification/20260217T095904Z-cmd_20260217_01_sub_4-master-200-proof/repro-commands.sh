#!/usr/bin/env bash
set -euo pipefail
BASE='http://localhost:5173'
AUTH_USER='1.3.6.1.4.1.9414.70.1:admin'
AUTH_PASS='21232f297a57a5a743894a0e4a801fc3'
curl -sS -D generic_class.headers.txt -o generic_class.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/generic-class?keyword=%E4%B8%AD%E6%9E%A2&page=1&size=50"
curl -sS -D material.headers.txt -o material.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/material?keyword=%E5%8B%95%E8%84%88"
curl -sS -D youhou.headers.txt -o youhou.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/youhou?keyword=%E6%AF%8E%E9%A3%9F"
curl -sS -D kensa_sort.headers.txt -o kensa_sort.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/kensa-sort?keyword=%E8%A1%80%E6%B6%B2"
curl -sS -D etensu_category1.headers.txt -o etensu_category1.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/etensu?keyword=%E5%88%9D%E8%A8%BA&category=1"
