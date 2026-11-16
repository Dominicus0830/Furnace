# 데이터 픽서 `Furnace.server().dataFixer()` (T-199, 2026-11-16)

옛 판(DataVersion) 데이터를 이 서버의 판(1.21.1 = **3955**)으로 올린다. 바닐라 DataFixerUpper 의 이 서버 구현이고, 구조물 템플릿 읽기와 **같은 고침 표**(바닐라 `DataFixers` 순서)를 쓴다.

```java
NbtDataFixer fixer = Furnace.server().dataFixer();
String block = fixer.fixBlockName("minecraft:grass", 1631);        // minecraft:short_grass
String item  = fixer.fixItemName("minecraft:rose_red", 1631);      // minecraft:red_dye
NbtCompound state = fixer.fixBlockState(oldState, 1631);           // {Name, Properties} 사본
```

| 메서드 | 다루는 것 |
|---|---|
| `fixBlockName` / `fixItemName` | 이름(이름공간 없으면 `minecraft:` 를 붙여 봄) |
| `fixBlockState` | `{Name, Properties}` |
| `fixItemStack` | `id`·`Count`/`count`·`tag`/`components` |
| `fixBlockEntity` | `id` 가 있는 블록 엔티티(표지판 글 등) |
| `fixEntity` | `id` 가 있는 엔티티(탑승자 포함) |

## 지원 범위
- `fromVersion` 이 `minSupportedVersion()`(1452, 1.13 평탄화 직후) ~ `currentVersion()`(3955) 일 때만 고친다. 올리는 목표는 늘 3955 다(중간 판으로 올리는 기능 없음).
- 1451 이하(1.12 숫자 ID)는 고치지 않는다. 숫자 ID → 1.13 이름은 플러그인(예: FAWE `legacy.json`)이 자기 표로 1631 까지 올린 뒤 부른다.

## 못 고치는 판
예외 없이 입력 그대로(이름) 또는 **사본**(NBT)을 돌려준다. 이미 3955 인 데이터도 같다. 고쳤는지 알고 싶으면 먼저 `supports(fromVersion)` 로 확인한다.

## 스레드·입력
- 어느 스레드에서나 부를 수 있다(상태 없음, 고침 표는 읽기 전용, 월드·플레이어 상태를 건드리지 않음). 서버가 데이터를 읽기 전에는 부르지 말 것.
- NBT 입력은 절대 건드리지 않고 사본을 고쳐 돌려준다.
- 기본 구현은 `UnsupportedOperationException` 이라, 이 서버가 아닌 `Furnace` 구현에서는 쓸 수 없다.

## 호환 계층과의 관계
Paper 플러그인이 쓰는 NMS `DataFixers.getDataFixer().update(type, Dynamic, from, to)` 는 `to` 가 3955 이고 type 이 block_name·item_name·block_state·item_stack·block_entity·entity 일 때 이 픽서로 올린다(그 밖은 그대로 + 처음 한 번 경고). 설명: `docs/api/compat-paper.md`, 기록: `docs/feature-parity/context-notes/2026-11-16-t199-datafix.md`.
