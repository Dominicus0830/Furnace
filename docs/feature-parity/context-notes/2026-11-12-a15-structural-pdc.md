# A15-06 마무리 — PDC 목록·컨테이너 배열·바이트 직렬화 (2026-11-12)

A15 후속의 마지막 묶음. 회귀 시험: `A15PersistentDataParityTest`(ItemPdc 직접 + 헤드리스 서버의 엔티티 컨테이너). 원문 대조는 Paper 1.21.1 사본(Youer 1.21.1 브랜치)의 `PersistentDataType`·`ListPersistentDataType`·`ListPersistentDataTypeProvider`·`CraftPersistentDataTypeRegistry`·`CraftPersistentDataContainer`·`PaperPersistentDataContainerView`.

- **PLAN 수정**: "목록 종류는 Furnace API 확장이 필요하다"고 적었으나 이미 있었다. `furnace-api` 의 `PersistentDataType.list(원소)` 와 서버 `NbtPersistentData`(NBT 리스트로 읽고 쓴다, 빈 목록은 어느 원소 종류로도 읽힌다)가 커밋 d4585a65 에서 들어와 있었고, 빠진 것은 호환 계층의 Bukkit 쪽 연결뿐이었다. 서버·API 변경은 없다.
- **`PersistentDataType.LIST`**(`ListPersistentDataTypeProvider`: bytes·shorts·integers·longs·floats·doubles·booleans·strings·byteArrays·integerArrays·longArrays·dataContainers·`listTypeFrom`)와 `ListPersistentDataType#elementType`. `TAG_CONTAINER_ARRAY`(@Deprecated)는 NBT 로는 compound 의 목록이라 `LIST.dataContainers()` 로 넣은 값을 읽어도 된다(Paper 와 같다).
- **저장 종류 결정**: 목록은 원소의 *기본* 종류로 따진다(불리언 목록은 byte 목록, 그래서 `LIST.bytes()` 로도 읽힌다). 빈 목록은 NBT 에 원소 종류가 없으므로 어떤 목록 종류로도 `has`·`get` 이 된다.
- **복사 의미**: `set` 은 값을(목록·배열·중첩 컨테이너) 그 자리에서 복사하고, `get` 도 복사본을 돌려준다. Paper 는 `set` 때 곧바로 NBT 로 직렬화하므로 꺼낸 값·넣은 값을 나중에 고쳐도 저장분이 안 바뀐다. 옛 구현은 배열을 그대로 들고 있었다.
- **`serializeToBytes` / `readFromBytes(bytes[, clear])`**: `PersistentDataContainerView`(io.papermc.paper.persistence)를 새로 두고 `PersistentDataContainer` 가 잇는다. 바이트는 Paper 와 같은 압축 없는 이름 없는 compound(종류 10, 빈 이름, 항목, 0). 항목 순서는 Paper 가 쓰는 해시 맵 순서를 따랐다. 서버의 `NbtIo.read` 로 되읽는 시험이 있다. `readFromBytes` 는 꼴이 틀리면 `IOException`(루트가 compound 가 아님·길이 초과·너무 깊은 중첩).
- **안 한 것(알려진 차이)**: 목록의 목록(`LIST.listTypeFrom(LIST.x())`)은 저장 시 `IllegalArgumentException`, 바이트에서는 `IOException` — Furnace API 가 중첩 목록을 갖지 않는다(Paper 는 `TAG_LIST` 원소로 허용). 호환 계층이 읽지 못하는 값 종류(다른 서버 플러그인이 쓴 목록의 목록 등)가 든 컨테이너는 `serializeToBytes` 가 `IOException`, `copyTo` 는 `UnsupportedFeature` 를 던진다(조용히 빠뜨리지 않는다).

## 독립 검토 반영 (PR #257 검토)
- **R1 항목 순서**: Paper 1.21.1 의 `CompoundTag` 는 패치 Reduce-memory-footprint-of-CompoundTag 로 `java.util.HashMap` 이 아니라 fastutil `Object2ObjectOpenHashMap(8, 0.8f)` 이다. `customDataTags`(HashMap) 순회 순서로 여기에 put 하고 그 `keySet` 순서로 쓴다. `PaperCompoundOrder`(검토자가 실제 fastutil 과 5000가지 대조해 만든, 서버 `ReferenceOrder.LongSet` 과 같은 방식의 순서 흉내)로 같은 순서를 낸다. 바이트를 해시·비교하는 플러그인이 Paper 와 같은 값을 본다. 시험 `serializeToBytesKeepsPapersCompoundKeyOrder`. 남는 차이: Paper `customDataTags` 는 한 번 커진 용량이 줄지 않으므로 키를 많이 넣었다 지운 컨테이너는 순서가 다를 수 있다(무시).
- **R2 원시 키**: `getKeys` 는 Paper 처럼 `split(":", 2)` 가 둘로 나뉠 때만 키로 낸다(콜론 없는 키는 빠지고 형식이 틀리면 `IllegalArgumentException`). `ItemPdc→ItemPdc` `copyTo` 는 원시 키 그대로 복사한다. 단 Furnace 서버 쪽 `PersistentDataType.checkKey` 가 콜론 없는·대문자 키를 거부하므로 그런 키는 메모리 컨테이너(아이템 메타)에서만 산다 — 엔티티에 적용하면 거부된다(알려진 차이).
- **R4**: `readFromBytes(…, clear=true)` 는 Paper 처럼 파싱 전에 비운다(잘린 바이트에 실패해도 비어 있다). 예외는 **`IOException` 을 유지**한다 — Paper 는 잘린 입력에 `ReportedNbtException`(검사되지 않는 예외)을 내지만 선언된 시그니처에 맞고 더 안전하다.
- **알려진 차이로만 남긴 것**: R3 `TAG_CONTAINER_ARRAY` 는 Paper 가 어떤 목록에도 `has` 참이고 비 compound 원소를 빈 컨테이너로 읽는데 이쪽은 compound 목록만 읽는다(옛 종류). R5 `[종류 8, 길이 0]` 처럼 원소 종류가 적힌 빈 목록은 이쪽이 어떤 목록 종류로도 읽는다(Paper 자신의 바이트는 빈 목록을 늘 종류 0 으로 쓴다). R6 `get` 이 돌려주는 목록은 고칠 수 있는 `ArrayList`(Paper 는 `Lists.transform` 보기).
- **후속 후보**: `ItemStack#getPersistentDataContainer()`(`PersistentDataContainerView`)·`OfflinePlayer#getPersistentDataContainer()` 이음, 원시형(`int.class`) primitive 를 받는 사용자 종류, 목록의 목록.
