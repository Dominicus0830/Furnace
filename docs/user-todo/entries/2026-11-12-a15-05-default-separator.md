# A15-05 addDefault 기본값 트리의 경로 구분자 (2026-11-12, PR #254 검토) — 결정됨: 둘 다 지원

플러그인이 `options().pathSeparator('/')` 처럼 구분자를 바꾼 뒤 `addDefault` 를 쓰면, Paper 는 기본값을 언제나 '.' 로 쪼개 저장합니다(Paper 쪽 버그에 가깝습니다). 값 읽기는 같지만 copyDefaults 로 저장한 파일 모양과 contains·getKeys 결과가 다릅니다.
예: `addDefault("groups/default/x", 1)` 저장 → Paper `groups/default/x: 1`(한 줄 키), Furnace `groups: default: x: 1`(중첩).

- [x] A. Furnace 동작: 개발자가 의도한 구분자로 저장 — **기본값**.
- [x] B. Paper 와 똑같이: `furnace.properties` 의 `plugins.compat.config.paper-default-separator=true` 로 켠다. 기본값 트리를 '.' 로 쪼개 저장 파일·contains·getKeys·`getDefaults()` 구분자까지 Paper 와 같다.

**결정(도미니코 11-12 "둘다 지원"):** 위 둘 다. 새 서버 설정 키 하나(기본 false)로 고른다. 구현·시험: `A15DefaultSeparatorOptionTest`(Paper 시나리오 10·11·14·26·31·48), 서버 쪽 `PluginFormatSpiTest#paperDefaultSeparatorOptionIsPassedOnlyWhenOn`.
