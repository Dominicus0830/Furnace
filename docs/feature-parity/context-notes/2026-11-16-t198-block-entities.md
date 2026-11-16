## 2026-11-16 NMS 블록 엔티티 쓰기 함정 (T-198)

단계 문서: `../compat-nms-t198-block-entities-2026-11-16.md`.

- **바닐라 `Level#getBlockEntity` 는 IMMEDIATE 다.** 블록 엔티티가 빠진 상자 칸을 읽으면 빈 것을 새로 만든다. FAWE `//paste` 는 섹션을 바꾼 뒤 이것으로 블록 엔티티를 얻고 `loadWithComponents` 한다 — 서버가 CHECK 처럼 null 을 주면 내용물이 사라진다. 서버 `ChunkLevel#getBlockEntity` 를 IMMEDIATE 로 바꿨고 CHECK 는 `getBlockEntityIfPresent`.
- **FAWE 는 지운 타일을 지운 뒤에 저장한다**(`removeBlockEntity` → `storeTile(tile)` → `saveWithId`). 보기가 "지금 그 칸"을 읽으면 빈 값이 나온다. 보기는 서버 객체 하나에 묶고, 지워진 뒤에도 그 객체의 마지막 내용을 저장한다.
- **같은 칸 보기를 다시 쓰려면 손잡이가 같아야 한다.** 생성 NBT 손잡이는 호출마다 새 객체라 `equals` 를 열·칸으로 정의했다(아니면 보기가 매번 새로 생겨 FAWE 의 `getBlockEntity` == 비교가 깨진다).
- **다른 스레드의 IMMEDIATE 는 블록보다 먼저 올 수 있다.** 섹션 CAS 와 만들기가 같은 일지 묶음에 들어가면, 재생은 들여오기 전에 돈다 — 그 칸 블록이 아직 옛것이라 만들 수 없다. 만들기(와 같은 칸 뒤 작업)를 들여오기 뒤로 미룬다.
- **Paper 의 `Level#getBlockEntity` 는 틱 스레드 밖에서 null 이다.** 다른 스레드는 청크의 `getBlockEntity`(CHECK)로 읽는다. 다른 스레드가 보는 목록은 주인이 틱 끝에 낸 공개 판이라, 주인이 같은 틱에 놓은 것은 다음 틱부터 보인다(시험은 `server.tick(1)`).
- **서버 기본 `ChunkLevel` 은 상자 블록 엔티티를 만들지 않는다**(상자는 `RegionLevel#createBlockEntity` 의 자기 틀). 단위 시험에서 상자로 지연 생성을 보면 null — 통 같은 일반 종류로 본다.
