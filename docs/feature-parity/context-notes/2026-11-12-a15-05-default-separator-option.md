# A15-05 후속: addDefault 기본값 구분자 옵션 (2026-11-12)

도미니코 결정 "둘 다 지원"(R6)에 따라 `plugins.compat.config.paper-default-separator`(기본 false)를 추가했다. 서버 `PluginSettings` 가 켠 것만 compatOptions 로 넘기고(안 적은 것·false 는 빠짐), 호환 계층은 `ConfigPaths.paperDefaultSeparator()` 로 읽는다(서버가 없으면 false — 설정만 쓰는 헤드리스 시험·도구).

켜면(사용자 지정 구분자일 때만 의미가 있다):
- `addDefault(path)`: Paper `createPath(this, path)` — 구역의 현재 경로 + 루트 구분자 + path 를 루트 기본값 설정에 `ConfigSection.addDefault`('.' 로 쪼갬)로 넣는다. 구역 단위 `addDefault("d.e")`(시나리오 26)도 같은 길이다.
- `getDefaults()` 래퍼의 구분자는 '.'(꺼짐: 이쪽 구분자를 물려줌).
- 값 조회의 기본값(`defaultOf`)은 Paper `getDefault` 처럼 `defaults.get(createPath(this, path))`. 구역 경로로 기본값 구역을 찾는 길과 다르다 — 시나리오 26 에서 구역 `s.t` 의 `get("d.e")` 는 `s` → `t/d` → `e` 로 찾는다.
- copyDefaults 저장·getKeys 는 기본값 트리가 이미 '.' 로 쪼개져 있어 코드를 바꾸지 않아도 Paper 와 같다(저장 병합이 literal 키 기준이라서).

기대값은 PR #254 검토의 Paper 설정 클래스 실행 결과(`oracle-results-cb6a091b.tsv` Paper 열)에서 옮겼다.
