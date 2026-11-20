# T-200 아이템 구성 요소 코덱 (2026-11-20)

FAWE `PaperweightFaweAdapter#adapt(BaseItemStack)`(`//wand`·브러시 아이템)와 Paper 의 아이템 NBT 왕복이 쓰는 길:
`registryAccess().createSerializationContext(NbtOps.INSTANCE)` → `DataComponentPatch.CODEC.parse/encodeStart` → `ItemStack#applyComponents/getComponentsPatch`.

## 바닐라 대조 (1.21.1 서버 jar 를 로컬에서 받아 Mojang 이름으로 되돌려 읽음 — 저장소에는 안 넣음)
- `NbtOps`: 메서드 목록·오류 문구·리스트 모으기(첫 값이 byte/int/long 이면 배열 태그, 다른 종류가 섞이면 `{"": 값}` 으로 감싼 묶음 리스트, 읽을 때 벗김)·`mergeToMap` 오류·`convertTo` 를 옮겼다. 바닐라 `NbtRecordBuilder` 대신 DFU 기본 `mapBuilder()` 를 쓴다(결과 태그 같음).
- `DelegatingOps`(net.minecraft.resources)·`RegistryOps`(만들기·withParent·owner·getter·injectRegistryContext): 바닐라 메서드 목록 그대로. 코덱 조합 `retrieveGetter/Element` 는 RecordCodecBuilder 대역이 없어 뺐다.
- `DataComponentPatch.CODEC`: 저장 꼴 `{"minecraft:x": 값, "!minecraft:y": {}}`, 저장 안 하는(transient) 종류는 쓰지 않고 읽기는 오류, 모르는 키 `No component with type: 'x'`, 지움 값이 묶음이 아니면 `Not a map`. 값 변환·검사는 서버의 컴포넌트 코덱(`ComponentPatch.fromCodec/toNbt`, 기존 구현)이 한다.
- `ItemStack#getComponentsPatch`: 비었으면(개수 0 포함) EMPTY. `applyComponents`: 값은 `set`, 지움은 `remove` 규칙(기본값과 같으면 패치에서 뗌).

## 구조
호환 계층은 공개 API 만 본다 → `ItemSupport` 에 `encodeComponentPatch`·`decodeComponentPatch`·`applyComponentPatch` 와 값 객체 `ItemComponentPatch` 를 더했다(서버 구현 `ItemSupportImpl`). shim 은 서버가 준 NBT 꼴을 받은 ops 의 값으로 옮기기만 한다(`NbtOps.convertTo`) — JsonOps 등 아무 ops 로도 된다.

## 알려진 차이·함정
- **넣은 순서**: 바닐라 패치는 넣은 순서(Reference2ObjectArrayMap), Furnace 스택은 키 정렬(TreeMap)로 들고 있어 값 → 지움 순서로 쓴다. NBT 묶음은 순서 없음이라 FAWE 에선 영향 없음, JSON 으로 쓰면 순서가 다를 수 있다.
- **verifyComponentsAfterLoad**: 바닐라 `applyComponents` 는 끝에 머리 프로필 확인을 부른다. Furnace 는 그 확인을 스택이 놓이는 칸의 쓰기 지점(서버 `ProfileFill`)에서 하므로 shim 의 `applyComponents` 는 안 부른다.
- `applyComponentsAndValidate`·`applyComponents(DataComponentMap)` 는 안 넣었다(FAWE 가 안 씀).
- `NbtOps` 등 DFU 를 구현하는 클래스는 DFU 가 없으면 올라가지 않는다 — 그 라이브러리를 쓰는 플러그인이 있을 때만 서버가 묶음을 붙인다(`CompatLinkTest` 가 이 세 클래스를 건너뜀).
- DFU 컴파일 대역(`src/dfu`)을 진짜 8.0.16 의 서술자와 맞게 늘렸다(`DataResult` 는 인터페이스+`Error` 최종 클래스, `DynamicOps` 전 메서드 …).

## 확인한 것
- 같은 시험(`NmsComponentCodecTest`, 15개)을 대역과 **진짜 DFU 8.0.16**(`-PdfuJar=<jar>`, 로컬 전용·CI 안 씀)에서 모두 통과.
- 진짜 `JsonOps` 로 쓰고 읽기 왕복도 임시로 확인(커밋 안 함): `{"minecraft:repair_cost":5,"minecraft:custom_data":{…},"!minecraft:max_stack_size":{}}`.
- 확인 못 한 것(가정): 실제 FAWE jar 로 `//wand` 를 줘 보는 접속 시험은 안 했다(`docs/user-todo/entries/2026-11-20-t200-fawe-wand.md`).
