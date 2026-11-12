# A15-05 addDefault 기본값 트리의 경로 구분자 (2026-11-12, PR #254 검토)

플러그인이 `options().pathSeparator('/')` 처럼 구분자를 바꾼 뒤 `addDefault` 를 쓰면, Paper 는 기본값을 언제나 '.' 로 쪼개 저장합니다(Paper 쪽 버그에 가깝습니다). 값 읽기는 같지만 copyDefaults 로 저장한 파일 모양과 contains·getKeys 결과가 다릅니다.
예: `addDefault("groups/default/x", 1)` 저장 → Paper `groups/default/x: 1`(한 줄 키), Furnace `groups: default: x: 1`(중첩).

- [ ] A. Furnace 동작 유지(추천): 개발자가 의도한 구분자로 저장. Paper 와 파일 모양만 다름. (지금 PR #254 의 상태)
- [ ] B. Paper 와 똑같이: 기본값 트리를 '.' 로 쪼갬. 저장 파일·contains·getKeys 까지 Paper 와 같음.
