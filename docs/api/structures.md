# 구조물 템플릿 (W4 / 불만 표 43번)

바닐라 구조물 NBT(`.nbt`, 구조물 블록이 쓰고 읽는 그 파일)로 영역을 저장하고 다시 놓고, `/locate structure` 처럼 가까운 구조물을 찾는 API다. 입구는 `Furnace.server().structures()`.

## 빠른 예

```java
var st = server().structures();
World w = ...;

// 1) 영역 저장: min 에서 4x3x4 칸을 test:house 로 (월드 폴더 generated/test/structures/house.nbt)
st.save(w, "test:house", min, 4, 3, 4, SaveOptions.DEFAULT.withAuthor("me"))
  .thenAccept(r -> log(r.status() + " " + r.info()));

// 2) 다시 놓기: 시계 90도 돌려서, 엔티티는 빼고
st.place(w, "test:house", at, PlaceOptions.DEFAULT.withRotation(StructureRotation.CLOCKWISE_90).withEntities(false))
  .thenAccept(r -> log(r.status() + " " + r.min() + ".." + r.max()));

// 3) 찾기: 가장 가까운 마을(태그)
st.locate(w, List.of("#minecraft:village"), blockPos, 100)
  .thenAccept(o -> o.ifPresent(loc -> log(loc.structureId() + " @ " + loc.x() + "," + loc.z())));
```

## 템플릿 저장소

- 월드 폴더 하나에 저장소 하나다(오버월드·네더·엔드·추가 월드가 같이 쓴다). 구조물 블록과 `/place template` 이 쓰는 저장소와 같으므로, 구조물 블록이 저장한 템플릿을 API 로 놓을 수 있고 API 로 저장한 것을 구조물 블록으로 불러올 수 있다.
- 찾는 순서: 메모리 저장소 → `generated/<네임스페이스>/structures/<경로>.nbt` → 켜진 데이터 팩의 `data/<네임스페이스>/structure/<경로>.nbt` → 내장(minecraft). 네임스페이스를 생략하면 `minecraft:`.
- `templates(world)` 는 generated + 데이터 팩의 이름이다(아직 파일로 안 쓴 메모리 템플릿은 빠진다). `templateInfo`·`exportTemplate`·`exportTemplateFile`(gzip `.nbt` 내용)·`importTemplate`·`importTemplateFile`·`deleteTemplate` 로 다룬다.
- 가져오기는 DataVersion 이 옛것이면 파일을 읽을 때와 같은 구조물 데이터 픽서(Q8)를 거친다. 모양이 틀린 NBT 는 `IllegalArgumentException`.
- 지우기는 `generated/` 파일과 메모리 전용 템플릿만 지운다. 데이터 팩·내장 템플릿은 못 지우므로(처음 읽어 메모리에 올라왔을 뿐이면 `false`), 같은 이름이 거기 있으면 그것이 다시 읽힌다.
- 이름은 바닐라 규칙대로 `generated/<네임스페이스>/structures/` 안으로만 풀린다(`a:../../b/x` 처럼 다른 폴더로 나가는 이름은 `INVALID_NAME`/`IllegalArgumentException`).

## 놓기 (`place`)

- 템플릿 원점(최소 x·y·z 모서리)이 `origin` 이다. 회전·대칭은 이 점 둘레다(바닐라 `/place template`·구조물 블록과 같다). 결과 `PlaceResult` 에 놓은 영역 상자(`min`~`max`, 회전·대칭 뒤)가 들어 있다.
- 구조물 블록 불러오기·`/place template` 과 **같은 배치기**(프로세서, 물 처리, 블록 엔티티 NBT, 엔티티, 모양 갱신, 상자 전리품 시드)를 쓴다. `BlockPlaceEvent` 같은 플레이어 이벤트는 거치지 않는다(`World.setBlock` 과 같다).
- `PlaceOptions`: 회전·대칭, 엔티티 포함(기본 **참** — `/place template` 과 같다. 구조물 블록은 기본 거짓), 완전성 `integrity`(0..1, 구조물 블록의 완전성 = BlockRot 프로세서), `seed`(0 이면 지금 시각 — 바닐라와 같다), `palette`(-1 이면 바닐라처럼 난수로 고른다).
- **청크는 알아서 올린다**: 영역과 그 바로 바깥 한 칸(가장자리 블록의 모양 갱신이 읽는 칸)을 덮는 청크를 임시 티켓으로 붙잡아 모두 올린 뒤, 시작 청크를 가진 region 스레드에서 한 번에 놓는다. 영역이 여러 region 에 걸쳐 있으면 region 이 합쳐지기를 잠깐(최대 약 2초) 기다리고, 끝내 한 region 에 모이지 못하면 아무것도 놓지 않고 `NOT_LOADED`. 더 길게 붙잡아야 하면 `chunkTickets()` 로 먼저 티켓을 건다.
- 한 번에 놓을 수 있는 크기는 약 19x19 청크(`/place template` 과 같은 한계지만, 바깥 한 칸의 청크까지 세므로 경계에서 한 청크 더 엄격하다). 넘으면 `TOO_LARGE`. 월드 높이 밖이면 `OUT_OF_WORLD`.
- 결과 상태: `PLACED`, `TEMPLATE_NOT_FOUND`, `EMPTY_TEMPLATE`, `OUT_OF_WORLD`, `NOT_LOADED`, `TOO_LARGE`, `INVALID_PALETTE`, `FAILED`. 안 놓은 상태에서는 월드가 바뀌지 않는다.

## 저장 (`save`)

- `min` 에서 `min + size - 1` 까지의 영역을 템플릿으로 만든다(구조물 블록의 저장 모드와 같은 `fillFromWorld`: 구조물 빈 칸은 빼고, 블록 엔티티 NBT 를 함께 담는다). 한 축 1..48.
- `SaveOptions`: 엔티티 포함(기본 거짓 — 구조물 블록의 "엔티티 무시" 기본값), 작성자(파일에는 안 쓰이고 메모리 템플릿에만 남는다 — 바닐라와 같다), `toDisk`(기본 참. 거짓이면 메모리 저장소만 갱신한다 — 구조물 블록의 레드스톤 저장과 같다).
- 파일 쓰기는 임시 파일에 쓴 뒤 옮기며 region 스레드 밖(비동기 실행기)에서 한다. 쓰기만 실패하면 `WRITE_FAILED`(메모리에는 들어 있다).
- 결과 상태: `SAVED`, `INVALID_NAME`, `INVALID_SIZE`, `OUT_OF_WORLD`, `NOT_LOADED`, `WRITE_FAILED`.

## 찾기 (`locate`)

- `/locate structure` 와 **같은 계산**(`StructureLocator.findNearest`)이다. 구조물 id(`minecraft:village_plains`)나 태그(`#minecraft:village`)를 여럿 줄 수 있고, 태그면 `StructureLocation.structureId()` 에 실제 찾은 종류가 들어 있다.
- 반경은 청크 단위 1..100(`/locate` 와 같은 100 이 최대, 범위 밖 값은 거절하지 않고 이 범위로 자른다). 틱 스레드 밖 전용 스레드에서 계산한다. 청크를 만들거나 불러오지 않는다.
- 이 월드의 생성기가 구조물을 만들지 않거나(평지 …) 반경 안에 없으면 `empty`. 모르는 이름은 `IllegalArgumentException`.
- 데이터 팩이 새로 정의한 구조물 종류는 아직 찾지 못한다(`/locate` 와 같은 한계 — 내장 구조물 표 기준).

## 스레드

`place`·`save`·`locate` 는 아무 스레드에서 부르고 `CompletableFuture` 로 받는다(region 스레드에서 미완료 상태로 `get()`·`join()` 하면 `IllegalStateException` 으로 거부된다 — `thenAccept` 로 잇는다). 템플릿 읽기(`place` 안)는 비동기 실행기에서 한다. `templates`·`hasTemplate`·`templateInfo`·`export*`·`import*`·`deleteTemplate` 은 부른 스레드에서 바로 파일 입출력을 할 수 있으니 region 스레드에서 쓰지 않는다.
