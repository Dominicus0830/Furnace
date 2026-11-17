# A15-05 경로 구분자 (2026-11-12)

`options().pathSeparator(c)` 가 `'.'` 말고도 되도록 했다(옛 Furnace 는 `UnsupportedOperationException` 이라, 점이 든 키(권한 노드·`minecraft:stone` 류·도메인)를 다루는 플러그인이 `onEnable` 에서 구분자를 바꾸면 켜지지 않았다). 시험 `A15PathSeparatorParityTest`(호환), `ConfigSectionTest#segmentPathsTreatEachStepAsOneKey`(API).

- **구조**: Furnace API `ConfigSection` 에 키 단계 목록을 받는 `getAt/containsAt/setAt/removeAt/getCommentsAt/setCommentsAt/getInlineCommentAt/setInlineCommentAt/addDefaultAt/pathSegments` 를 더했다. 단계 하나가 곧 키 하나(점이 든 키가 그대로 한 단계)이고, 점 경로의 "literal 키 먼저 찾는" 너그러운 대체는 없다(단계가 섹션이 아니면 없는 것). 기본 구분자 `'.'` 는 옛 점 경로 코드를 그대로 쓴다(동작 변화 없음).
- **호환 `MemorySection`**: 루트 옵션의 구분자가 `'.'` 가 아니면 경로를 그 문자로 나눠(빈 단계는 건너뛴다 — 아래 R4) `*At` 로 넘긴다. `getKeys(true)`·`getCurrentPath()` 도 그 문자로 잇는다. 구분자는 조회 때마다 루트 옵션에서 읽으므로 설정을 만든 뒤에 바꿔도 된다.
- **기본값 설정**: Paper 처럼 `getDefaults()` 는 `setDefaults` 로 받은 객체를 그대로 돌려준다(그쪽 구분자 유지). `addDefault` 로 만들어진 기본값 트리를 감싼 보기는 이쪽 구분자를 물려받는다. 기본값 구역을 찾는 `getDefaultSection` 은 Paper 와 같이 `getCurrentPath()`(이쪽 구분자)로 기본값 설정에 묻는다 — 두 설정의 구분자가 다르면 Paper 와 마찬가지로 기본값 구역을 못 찾는다.
- **안 한 것**: 구분자를 바꾸기 전에 `'.'` 로 쓴 경로 데이터를 다시 해석하지 않는다(Paper 도 안 한다). `'.'` 모드의 YAML 읽기는 점이 든 키를 literal 키로 둔다(Paper 는 읽을 때 쪼갠다 — 원래 있던 차이, 후속). 사용자 지정 구분자일 때만 읽기에서 그 구분자로 쪼갠다(아래 R5).

## 독립 검토 반영 (PR #254 검토, 같은 날)

검토는 Paper `org.bukkit.configuration` 원문을 그대로 컴파일해 같은 시나리오 143개를 양쪽에서 돌려 비교했다(보고서 `furnace/reviews/pr254-config-path-separator.md`). 이 PR 이 새로 연 사용자 지정 구분자 길의 차이를 아래처럼 고쳤고, 재현 시험은 `A15PathSeparatorReviewTest` 에 들어 있다.

- **R1** `addDefaults(Configuration)` 의 MemorySection 빠른 길(점 경로로 이어 다시 나눔)은 두 설정 모두 `'.'` 일 때만 탄다. 아니면 Paper 처럼 `getValues(true)` 를 `addDefault` 로 하나씩 옮긴다 — `'/'` 설정의 `a.b/c` 기본값이 `a→b→c` 로 들어가 사라지던 문제.
- **R2** `YamlConfiguration.saveToString`(copyDefaults)의 합치기는 점이 든 키도 literal 한 키로 다뤄(`getAt(List.of(k))`) 구역끼리 안쪽까지 합친다. 점 든 구역 아래 기본값이 저장에서 빠지던 문제(`'.'` 모드에서 YAML 로 읽힌 literal 점 키 구역도 같이 고쳐진다).
- **R3** `contains`·`isSet` 은 Paper 처럼 `get` 과 같은 길로 정한다(`contains(p, ignoreDefault) = (ignoreDefault ? get(p, null) : get(p)) != null`). 기본값 설정의 구분자가 달라도 `get` 과 어긋나지 않는다(`getConfig()` 의 jar 기본값은 `'.'`, 플러그인은 `'/'` 인 흔한 경우).
- **R4** 빈 단계: 이전에 쓴 "빈 이름 구역을 만드는 것은 Paper 와 같다"는 **틀렸다**. Paper 는 빈 단계를 건너뛴다(`set("/a")`=`a`, `a//b`=`a/b`). 끝 구분자는 조회면 그 구역(구역이 아니면 null), 쓰기·`createSection` 은 중간 구역을 만든 뒤 `IllegalArgumentException`, 빈 경로 쓰기도 `IllegalArgumentException`. `ConfigPaths.parse` 가 이 규칙을 한곳에서 해석하고 주석 읽기·쓰기도 같은 단계를 쓴다. **`'.'` 길의 같은 차이(`set("a.",1)`, `get(".p.q")`)는 원래 있던 것이라 그대로 뒀다** — 후속 후보.
- **R5** 사용자 지정 구분자일 때 YAML 읽기(`loadFromString`)가 구분자가 든 키를 쪼갠다(Paper `fromNodeTree` → `createSection`/`set`). 주석·머리말은 옮기고, `==` 직렬화 객체 구역 안쪽과 끝 구분자로 끝나는 키는 쪼개지 않는다. `createSection(path, map)` 도 맵 키를 같은 구분자로 쪼개 넣는다.
- **R6**(결정 항목) `addDefault` 로 만든 기본값 트리의 구분자: Paper 는 언제나 `'.'` 로 쪼갠다(버그에 가깝다). Furnace 는 이쪽 구분자를 유지한다(추천 안). 도미니코 결정 대기 — `docs/user-todo/entries/2026-11-12-a15-05-default-separator.md`.
- **원래 있던 차이(후속)**: `getConfigurationSection().getParent()` 가 실제 부모가 아님, 기본값에만 있는 구역을 꺼낼 때 Paper 는 자기 데이터에 빈 구역을 만듦, `MemorySection.createPath` 공개 정적 메서드 없음, `'.'` 로 읽은 뒤 구분자를 바꾸는 경우의 키 재해석.
