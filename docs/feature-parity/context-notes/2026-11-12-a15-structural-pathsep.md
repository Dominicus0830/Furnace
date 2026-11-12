# A15-05 경로 구분자 (2026-11-12)

`options().pathSeparator(c)` 가 `'.'` 말고도 되도록 했다(옛 Furnace 는 `UnsupportedOperationException` 이라, 점이 든 키(권한 노드·`minecraft:stone` 류·도메인)를 다루는 플러그인이 `onEnable` 에서 구분자를 바꾸면 켜지지 않았다). 시험 `A15PathSeparatorParityTest`(호환), `ConfigSectionTest#segmentPathsTreatEachStepAsOneKey`(API).

- **구조**: Furnace API `ConfigSection` 에 키 단계 목록을 받는 `getAt/containsAt/setAt/removeAt/getCommentsAt/setCommentsAt/getInlineCommentAt/setInlineCommentAt/addDefaultAt/pathSegments` 를 더했다. 단계 하나가 곧 키 하나(점이 든 키가 그대로 한 단계)이고, 점 경로의 "literal 키 먼저 찾는" 너그러운 대체는 없다(단계가 섹션이 아니면 없는 것). 기본 구분자 `'.'` 는 옛 점 경로 코드를 그대로 쓴다(동작 변화 없음).
- **호환 `MemorySection`**: 루트 옵션의 구분자가 `'.'` 가 아니면 경로를 그 문자로 나눠(빈 단계도 유지) `*At` 로 넘긴다. `getKeys(true)`·`getCurrentPath()` 도 그 문자로 잇는다. 구분자는 조회 때마다 루트 옵션에서 읽으므로 설정을 만든 뒤에 바꿔도 된다.
- **기본값 설정**: Paper 처럼 `getDefaults()` 는 `setDefaults` 로 받은 객체를 그대로 돌려준다(그쪽 구분자 유지). `addDefault` 로 만들어진 기본값 트리를 감싼 보기는 이쪽 구분자를 물려받는다. 기본값 구역을 찾는 `getDefaultSection` 은 Paper 와 같이 `getCurrentPath()`(이쪽 구분자)로 기본값 설정에 묻는다 — 두 설정의 구분자가 다르면 Paper 와 마찬가지로 기본값 구역을 못 찾는다.
- **안 한 것**: 구분자를 바꾸기 전에 `'.'` 로 쓴 경로 데이터를 다시 해석하지 않는다(Paper 도 안 한다). YAML 읽기는 구분자와 상관없이 점이 든 키를 literal 키로 둔다.
