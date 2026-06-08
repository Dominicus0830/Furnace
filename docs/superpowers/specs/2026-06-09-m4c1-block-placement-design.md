# M4c-1 — 크리에이티브 블록 설치 설계

**목표:** 크리에이티브 모드서 우클릭으로 블록 설치 — M4a 파괴의 짝. 설치된 블록은 교차region 브로드캐스트되고 M4b autosave로 영속된다.

**범위:** 최소 + 면(face) 세분화 + 플레이어 충돌 거부. 방향성(통나무 축·계단 방향)은 기본 state(정확 속성은 M4d). 인벤토리 UI/아이템스택/블록엔티티는 M4c-2/M4c-3.

**부모 설계서:** `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` §140 (M4c).

## 배경 / 현 상태

- M4a: `Region.breakBlock(bx,by,bz)` = chunkAt.setBlock(AIR)+dirty + 구독자에 `BlockUpdate`. 교차region 브로드캐스트 = `World.chunkSubscribers` 레지스트리.
- M4b: dirty 청크 autosave/eviction/shutdown 저장. 설치도 dirty 세팅하면 자동 영속.
- `BlockRegistry`: 블록명→default state id(`defaultStateId`/`stateIdOrAir`). state id→블록명 역맵(`nameOf`).
- **아이템 레지스트리 없음.** `registries/1.21.1.txt`는 설정단계 동기 레지스트리만(minecraft:item 미포함). 설치는 Set Creative Mode Slot이 **숫자 item id**를 보내므로 item id→name 매핑 필요.

## 아키텍처

M4a 파괴 인프라 역방향. 클라가 우클릭(`use_item_on`) → ConnectionHandler가 든 아이템→블록 state 해석 + 설치 위치 계산 → 소유 region에 마샬 → `Region.placeBlock`이 플레이어 충돌 검사 후 설치 + `BlockUpdate` 브로드캐스트. dirty 세팅으로 M4b가 영속.

## 컴포넌트

### 1. ItemRegistry (신규)
`world/ItemRegistry.java` + 리소스 `resources/items/1.21.1.txt`(라인 `<name> <numericId>`, datagen `reports/registries.json`의 `minecraft:item` 엔트리서 추출 — blocks/1.21.1.txt와 동일 distill 방식).
- `static ItemRegistry load(String resourcePath)`
- `String nameOf(int itemId)` — 숫자 id→아이템명(미지원 id는 `"minecraft:air"`).

### 2. 든-아이템 추적 (ConnectionHandler 필드 — Netty 스레드 소유)
입력 패킷(Set Held Item·Set Creative Mode Slot·Use Item On)이 전부 한 커넥션의 Netty 스레드(`channelRead0`)서 처리되므로, 든-아이템 상태는 ConnectionHandler에 두면 동기화 불필요(스레드 로컬).
- `int[] hotbar = new int[9]` — 각 핫바 슬롯 아이템 id(0=air).
- `int selectedSlot` (0~8).
- `heldBlockState()` 헬퍼 = `hotbar[selectedSlot]` → ItemRegistry.nameOf → BlockRegistry.stateIdOrAir.
- Player에는 필드 추가 안 함. region엔 해석된 stateId만 넘김(아래 4).

### 3. ConnectionHandler 패킷 처리 (1.21.1 serverbound play; ID는 datagen packets.json서 확정)
- **Set Held Item (serverbound):** Short slot(0~8) → `this.selectedSlot = slot`.
- **Set Creative Mode Slot:** Short slot, 그다음 Slot. Slot = VarInt itemCount; itemCount>0이면 VarInt itemId 읽음(components는 무시=프레임 잔여 안 읽음). slot이 36~44(핫바)면 `hotbar[slot-36] = itemId`(itemCount==0이면 air=0). (그 외 슬롯은 무시 — 핫바만 추적.)
- **Use Item On:** VarInt hand, BlockPos blockPos, VarInt face(0~5), Float cursorX/Y/Z, Boolean insideBlock, (1.21.1: Boolean worldBorderHit 추가 필드 datagen 확인), VarInt sequence. hand!=0(off-hand) 무시. face 법선으로 설치 위치 계산.

### 4. 설치 위치 + 아이템→블록 해석 (ConnectionHandler)
- 면 법선: face 0=down(y-1),1=up(y+1),2=north(z-1),3=south(z+1),4=west(x-1),5=east(x+1). 설치 위치 = blockPos + 법선.
- `heldBlockState()` = hotbar[selectedSlot] → `ItemRegistry.nameOf` → `BlockRegistry.stateIdOrAir(name)`. air(0)면(설치 불가 아이템: 검·음식 등) 설치 안 함(return).
- 설치 위치를 소유 region에 마샬: `RegionGrid.regionPos(px,pz)` → `region.placeBlock(px,py,pz,stateId)`.

### 5. Region.placeBlock(bx,by,bz,stateId)
- `world.assertOwns(pos)`.
- **플레이어 충돌 거부:** 설치 블록 AABB `[bx,bx+1]×[by,by+1]×[bz,bz+1]`가 region 내 어떤 플레이어 바운딩박스(가로 0.6=±0.3, 높이 1.8)와 겹치면 설치 취소(return). (교차region 플레이어는 경계 근접 시 드묾 — region 내만 검사, 최소 범위.)
- 겹침 없으면 `chunkAt(cp).setBlock(bx&15,by,bz&15,stateId)`(dirty 자동) + 구독자에 `new BlockUpdate(bx,by,bz,stateId)`.
- breakBlock과 동일 구조(상태값만 air→stateId).

## 데이터 흐름

1. 클라 creative 인벤서 블록 선택 → Set Creative Mode Slot(핫바) → ConnectionHandler `hotbar[slot]=itemId`.
2. 핫바 선택 변경 → Set Held Item → `selectedSlot`.
3. 우클릭 → Use Item On → ConnectionHandler가 heldBlockState 해석 + 설치위치 계산 → 소유 region에 `placeBlock(pos,stateId)` 마샬 → BlockUpdate 교차region 브로드캐스트(M4a 구독레지스트리) → dirty → M4b autosave 영속.

## 테스트

- `ItemRegistryTest`: 리소스 로드, nameOf 매핑, 미지원 id→air.
- `BlockPlaceTest`(World+EmbeddedChannel/region): placeBlock이 빈 자리 설치+dirty 세팅+구독자 BlockUpdate; 플레이어 AABB 겹치는 위치 설치 거부.
- 면 법선 계산 단위(6면 → 올바른 인접 위치).
- **수동 verify:** 크리에이티브 접속, 핫바 블록 선택, 여러 면에 설치, 발밑/머리위 설치 시 자기 위치엔 안 박힘, 재시작 후 설치 유지(autosave-ticks=200).

## 범위 밖

- 방향성 블록 정확 속성(통나무 축·계단/문 방향) = M4d.
- 아이템스택 수량/소비, 서바이벌 인벤, 컨테이너/블록엔티티 = M4c-2/3.
- 블록 업데이트 전파(물/모래 낙하 등) = M5.
- off-hand, 블록 우클릭 상호작용(상자 열기 등) = 후속.
