# A15 후속: MemorySection.set 이 직렬화 객체 참조를 보관 (2026-11-12)

PR #253 검토 R2 — Paper `MemorySection.set` 은 값 객체를 그대로 `map` 에 넣고 `get` 은 그 참조를 돌려준다. Furnace 는 set 하는 순간 맵으로 바꾸고 get 마다 새로 되살렸다.

## 한 것
- `ConfigSection`(API)에 `attach`/`attachment`/`forEachAttached`/`replaceChildren` 를 더했다 — 값 하나의 저장 꼴인 구역에 원래 객체를 붙여 둔다. 복사(`copy`)는 붙인 것을 옮기지 않는다.
- `MemorySection.set(path, ConfigurationSerializable)` 은 저장 꼴을 만들고 그 구역에 객체를 붙인다. `get`·`getValues`·`getSerializable`·`getObject` 가 같은 참조를 돌려주고,
  등록하지 않은 클래스도 메모리에서는 그 객체로 돌아온다(전에는 `MemorySection`). 객체 자리는 `getConfigurationSection` null·`isConfigurationSection` false(Paper 와 같다).
- `YamlConfiguration.saveToString` 이 쓰기 직전에 붙은 객체마다 `serialize()` 를 다시 불러 저장 꼴을 갱신한다 — set 뒤에 객체를 고쳐도 Paper 처럼 쓰는 순간의 값이 파일에 간다.
- `set("a.b", x)` 에서 `a` 가 보관 중인 객체면 새 구역으로 바꾼다(Paper 는 `a` 가 구역이 아니면 만들어 덮는다).
- `in()` 변환은 `org.domi.furnace.compat.paper.ConfigValues.lower` 로 옮겼다(MemorySection·YamlConfiguration 공용).

## 알려진 차이
- **ConfigurationSerializable 만** 참조를 보관한다. `List`·`Map`·`ConfigurationSection` 을 set 하면 여전히 저장 꼴 사본이다(Paper 는 참조 그대로라 set 뒤 리스트를 고치면 보인다).
  안쪽에 든 직렬화 객체(리스트 원소·다른 객체의 serialize 값)도 get 하면 되살린 새 객체다.
- `addDefault`·기본값 구역은 보관하지 않는다.
- `getKeys(true)` 가 보관한 객체 안쪽 키까지 내려가는 것은 그대로다(Paper 는 객체를 잎으로 본다).
- 파일에서 읽어 온 객체는 읽을 때마다 되살린 새 객체다(Paper 도 읽을 때 한 번 되살려 그 참조를 보관하지만, 같은 참조 비교에 기대는 플러그인은 드물다).

## 시험
`A15ConfigRetainTest`: 같은 참조·set 뒤 변경 반영·저장 시점 값, 등록하지 않은 클래스, 덮어쓰기·삭제·객체 아래 하위 키, 사용자 구분자, 읽어 온 YAML 되살리기.
