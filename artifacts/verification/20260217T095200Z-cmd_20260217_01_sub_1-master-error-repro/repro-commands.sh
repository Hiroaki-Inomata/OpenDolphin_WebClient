#!/usr/bin/env bash
set -euo pipefail
BASE='http://localhost:5173'
AUTH_USER='1.3.6.1.4.1.9414.70.1:admin'
AUTH_PASS='21232f297a57a5a743894a0e4a801fc3'

curl -sS -D generic_class.headers.txt -o generic_class.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/generic-class?keyword=%E3%82%A2%E3%83%A0&page=1&size=50"
curl -sS -D material.headers.txt -o material.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/material?keyword=%E3%82%AC%E3%83%BC%E3%82%BC"
curl -sS -D youhou.headers.txt -o youhou.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/youhou?keyword=%E6%9C%9D%E9%A3%9F"
curl -sS -D kensa_sort.headers.txt -o kensa_sort.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/kensa-sort?keyword=%E8%A1%80%E6%B6%B2"
curl -sS -D etensu_category2.headers.txt -o etensu_category2.body.json -H "userName: ${AUTH_USER}" -H "password: ${AUTH_PASS}" "$BASE/orca/master/etensu?keyword=%E8%85%B9&category=2"
