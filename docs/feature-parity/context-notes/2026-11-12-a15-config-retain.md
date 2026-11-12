# A15 후속: MemorySection.set 이 직렬화 객체 참조를 보관 (2026-11-12)

PR #253 검토 R2 — Paper `MemorySection.set` 은 값 객체를 그대로 `map` 에 넣고 `get` 은 그 참조를 돌려준다. Furnace 는 set 하는 순간 맵으로 바꾸고 get 마다 새로 되살렸다.

## 한 것 (독립 검토 #260 의 방안 B 로 정리)
- 직렬화 객체는 **구역이 아니라 트리의 잎 값**이다. `ConfigSection.Opaque { Object lower(); }`(공개 인터페이스 하나)를 잎으로 두면 트리는 참조 그대로 보관·복사하고, 키 나열·경로 해석이 안쪽으로 내려가지 않는다.
  호환 계층은 `ConfigObject`(Opaque 구현)로 `ConfigurationSerializable` 을 감싸 `set`·`addDefault`·`addDefaults` 에 넣고, `MemorySection.out` 이 풀어 준다.
- `lower()`(= `serialize()` + `==` 별칭)는 **쓸 때만** 부른다. `ConfigFormat.write` 는 불투명 값이 있으면 쓰기용 사본을 만들어 풀고, 원본 트리는 읽기만 한다(비동기 저장 중 다른 스레드 읽기에서 CME 가 나지 않는다).
- 읽어 온 YAML 의 `==` 구역(별칭이 등록된 것)은 `ConfigValues.reviveTree` 가 읽을 때 한 번 안쪽부터 되살려 잎으로 바꾼다 — Paper YamlConstructor 처럼 `get` 마다 같은 참조. 되살리지 못하면 구역 그대로.
- 결과로 Paper 와 같아진 것: 객체 아래 `set("o.x")`·`createSection("o.sub")` 는 객체를 구역으로 덮음, `getKeys(true)`·`getValues(true)` 가 객체 안으로 안 내려감, 객체 아래 경로·주석 조회는 없음, set 때 `serialize()` 를 안 부름(저장 때만),
  `copyDefaults` 저장이 기본값 키를 객체에 섞지 않음, 기본값(`addDefault`·`addDefaults`·`setDefaults`)도 참조 보관, `ConfigSection.copy()` 가 참조를 옮김.
- `ConfigSection` 의 공개 추가는 `Opaque` 하나다(이전 안의 attach/attachment/forEachAttached/replaceChildren 은 없앴다).

## 알려진 차이
- **ConfigurationSerializable 만** 참조를 보관한다. `List`·`Map`·`ConfigurationSection` 을 set 하면 여전히 저장 꼴 사본이다(Paper 는 참조 그대로라 set 뒤 리스트를 고치면 보인다 — 검토 R14, 후속).
- 등록하지 않은 `==` 별칭이 든 YAML 은 Paper 가 읽기 실패(InvalidConfigurationException)지만 Furnace 는 구역으로 두고 계속 읽는다(R14).
- `serialize()` 가 트리가 모르는 타입(byte[] 등)을 돌려주면 쓸 때 IllegalArgumentException 이다(Paper 는 `!!binary` 로 씀, R12). set 때가 아니라 쓸 때 난다.
- `==` 키를 `'=='` 로 따옴표 쳐서 쓴다(R15), 빈 경로 set 의 예외 문구(R17), 객체가 구역으로 바뀔 때 키 주석이 남는 점(R13)은 기존과 같다.

## 시험
`A15ConfigRetainTest`(같은 참조·set 뒤 변경 반영·저장 시점 값·등록 안 한 클래스 등), `A15ConfigObjectLeafTest`(독립 검토의 재현 11개: 객체 아래 createSection, 저장이 트리를 안 고침, copyDefaults, 깊은 키, 객체 아래 경로·주석, serialize 호출 횟수,
기본값 참조, setDefaults 객체, 구역 복사, 읽은 객체 참조 유지), API `ConfigOpaqueTest`.
