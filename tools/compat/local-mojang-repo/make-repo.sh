#!/usr/bin/env bash
# libraries.minecraft.net 에 닿지 않는 곳(샌드박스 등)에서 호환 모듈을 빌드하려고, 바닐라 1.21.1 서버(번들러) jar 안의
# Mojang 라이브러리를 로컬 Maven 저장소 모양으로 꺼낸다. 꺼낸 jar 는 Mojang 것이므로 저장소 밖에 두고 커밋하지 않는다.
# 쓰는 법: make-repo.sh <minecraft_server.1.21.1.jar> <출력 폴더>
set -euo pipefail
bundler="$1"; out="$2"
case "$(cd "$(dirname "$out")" 2>/dev/null && pwd)/" in
  "$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null)"/*) echo "출력 폴더는 저장소 밖이어야 한다: $out" >&2; exit 1;;
esac
list=$(unzip -p "$bundler" META-INF/libraries.list)
for coord in com.mojang:authlib com.mojang:datafixerupper com.mojang:brigadier; do
  line=$(printf '%s\n' "$list" | awk -v c="$coord" '$2 ~ "^"c":" {print; exit}')
  [ -n "$line" ] || { echo "libraries.list 에 없음: $coord" >&2; exit 1; }
  sha=$(echo "$line" | cut -f1); gav=$(echo "$line" | cut -f2); path=$(echo "$line" | cut -f3)
  art=$(echo "$gav" | cut -d: -f2); ver=$(echo "$gav" | cut -d: -f3)
  mkdir -p "$out/$(dirname "$path")"
  unzip -p "$bundler" "META-INF/libraries/$path" > "$out/$path"
  echo "$sha  $out/$path" | sha256sum -c --quiet -
  printf '<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.mojang</groupId><artifactId>%s</artifactId><version>%s</version></project>\n' \
    "$art" "$ver" > "$out/${path%.jar}.pom"
  echo "$gav -> $out/$path"
done
