# 플레이어별 화면 덮어쓰기 — 가상 블록·바이옴, 개인 월드 보더, 장비 표시, 채팅 서식 (K3)

한 사람의 화면에만 다른 모습을 보이는 API다. 입구는 `Furnace.server().playerViews()`, 채팅은 `AsyncChatEvent.renderer(...)`.
서버 상태(진짜 블록·바이옴·장비·보더)는 그대로이고 **그 사람에게 나가는 패킷만** 바뀐다. 층이 하나도 없으면 바닐라와 똑같이 동작한다.
엔티티를 숨기거나 변장시키는 것은 [엔티티 보이기·숨기기·변장](entity-views.md), 월드에 없는 엔티티는 [가상 엔티티](virtual-entities.md)다.

## 빠른 예

```java
var views = server().playerViews();
World w = server().world(player.location().world());

// 1) 가상 블록 — 이 사람에게만 금 블록 (청크를 다시 받아도 유지)
views.setBlock(this, player, w, new BlockPos(10, 64, 10), BlockData.of("minecraft:gold_block"));
views.fillBlocks(this, player, w, new BlockPos(0, 63, 0), new BlockPos(15, 63, 15), BlockData.of("minecraft:glass"));
views.clearBlocks(this, player, w);           // 이 플러그인이 건 블록 층을 모두 걷고 진짜 블록을 다시 보낸다

// 2) 가상 바이옴 — 이 사람 화면의 풀·물 색, 하늘, 소리
views.setBiome(this, player, w, new BlockPos(10, 64, 10), "minecraft:desert");

// 3) 개인 월드 보더 — 이 사람에게만 지름 100 짜리 벽
PlayerBorder b = views.border(this, player);
b.center(0, 0);
b.size(100);
b.lerpSize(20, 30_000);                        // 30초에 걸쳐 20 으로 줄이기
b.enforce(true);                               // 서버 판정(보더 밖 피해·보더 밖 블록 상호작용)도 이 보더로

// 4) 장비 표시 — viewer 화면에서 target 이 다이아 투구를 쓴 것처럼
views.setEquipment(this, viewer, target, EquipmentSlot.HEAD, ItemStack.of("minecraft:diamond_helmet"));

// 5) 채팅 서식 — 받는 사람마다 다른 줄
events.subscribe(this, AsyncChatEvent.class, e -> e.renderer((source, name, message, viewer) ->
        Text.text(viewer instanceof Player v && v.hasPermission("staff") ? "[" + source.name() + "] " : "").append(message)));
```

## 가상 블록·바이옴 — 층(layer)

- **청크를 다시 받아도 유지된다.** Bukkit `sendBlockChange` 는 한 번 보낸 변경이라 청크를 다시 보내면(재접속, 시야 밖에 갔다 옴, 차원 이동) 사라진다. 여기서는 **서버가 그 사람에게 보내는 패킷에 층을 덧입힌다**: 청크 데이터(`level_chunk_with_light`), 블록 변경(`block_update`, `section_blocks_update`), 바이옴(`chunks_biomes`).
- **진짜 블록이 바뀌어도 층이 이긴다.** 같은 칸에 서버가 블록 변경을 보내도 그 사람 화면에는 층의 블록이 남는다. 층을 걷으면(`clearBlock` …) 서버가 **그 순간의 진짜 블록**을 읽어 보낸다.
- 층은 **월드(차원)별**이다. 그 사람이 다른 월드로 가면 이 월드의 층은 닿지 않고, 돌아오면 다시 덧입힌다.
- 층은 **그 접속 동안만** 산다(다시 접속하면 없다 — 접속 이벤트에서 다시 건다). 건 플러그인 이름으로 남아 **플러그인이 꺼지면 함께 거둔다**(핫 리로드 때 남지 않게).
- 같은 칸에 두 플러그인이 걸면 마지막으로 건 쪽이 주인이다. `clearBlock(plugin, …)` 은 자기 것만 걷는다.
- `fillBlocks` 는 한 번에 `PlayerViews.MAX_FILL`(65536) 칸까지. 바이옴은 4×4×4 칸 단위다(`fillBiome` 은 칸 수로 MAX_FILL/64).
- 알 수 없는 블록·속성·바이옴이면 `IllegalArgumentException` (기존 층은 그대로). 월드 높이 밖은 무시한다.
- **한계**:
  - 서버는 진짜 블록으로 충돌·이동·낙하·상호작용을 판정한다. 층으로 만든 바닥을 밟으면 서버가 되돌릴 수 있고, 층 블록을 클릭하면 서버는 진짜 블록에 반응한다(클라이언트는 층 모양으로 예측한다). 눈속임용이다.
  - 빛은 진짜 블록 기준 그대로다(층 블록이 빛을 내도 어둡게 보인다). 블록 엔티티(상자 내용·간판 글자)는 진짜 블록의 것이 와도 층 블록이 그 종류가 아니면 클라이언트가 무시한다.
  - 바이옴 층은 색·하늘·안개·소리·날씨 입자 같이 **클라이언트가 바이옴으로 정하는 것**만 바꾼다. 서버의 스폰·날씨·작물 성장은 진짜 바이옴이다.

## 개인 월드 보더 — `PlayerBorder`

- `border(plugin, viewer)` 는 서버 보더의 지금 값으로 시작해 viewer 에게 보내고, 이후 값을 바꿀 때마다 `initialize_world_border` 로 그 사람에게만 보낸다(보간 중이면 남은 시간과 함께).
- 개인 보더가 있는 사람에게는 **서버 보더의 변경(명령·플러그인)이 가지 않는다.** 접속·차원 이동 때 서버가 보내는 보더 초기화도 개인 보더 상태로 바꿔 보낸다(보더는 월드가 아니라 접속에 붙는다).
- `enforce(true)` 면 서버 판정도 이 보더를 쓴다: **보더 밖 피해**(블록당 피해·안전 거리는 `damagePerBlock`·`damageSafeZone`)와 **보더 밖 블록 부수기·놓기 막기**. 기본은 보이는 것만(클라이언트는 자기 보더를 벽으로 예측하지만 서버는 진짜 보더로 이동을 검증한다).
- `resetBorder(viewer)` 는 개인 보더를 걷고 서버 보더를 다시 보낸다. 보더를 만든 플러그인이 꺼지면 자동으로 걷는다.

## 장비 표시

- `setEquipment(plugin, viewer, target, slot, item)`: viewer 화면에서 target(몹·플레이어)의 장비 칸을 `item` 으로 보인다(빈 스택이면 비어 보인다). target 의 진짜 장비·방어력·손에 든 것은 그대로다.
- 진짜 장비가 바뀌어도 덮어쓴 칸은 유지된다(서버의 `set_equipment` 를 같은 칸이면 덮어쓴 값으로 바꿔 보낸다). target 이 시야에 새로 들어올 때(스폰)도 덮어쓴 값이 따라간다 — 서버가 빈 장비라 따로 안 보내도 스폰 뒤에 덮어쓴 칸을 보낸다.
- `clearEquipment` 는 그 칸의 **진짜 장비를 그 엔티티를 가진 스레드에서 읽어** 다시 보낸다.
- 자기 자신의 장비(viewer == target)는 클라이언트가 인벤토리로 그리므로 바뀌지 않는다. 변장 중인 엔티티가 살아 있지 않은 모습이면 장비를 보내지 않는다. 몸 칸(말 갑옷)은 다루지 않는다.
- 엔티티 번호(서버 실행 동안 안 바뀐다)에 묶는다. 대상이 영영 사라져도 그 접속이 끝나면 함께 사라진다.

## 채팅 서식 — `AsyncChatEvent.renderer`

- `renderer(ChatRenderer)` 를 정하면 **받는 사람마다** `render(source, sourceDisplayName, message, viewer)` 가 불려 그 사람에게만 가는 줄을 만든다. `viewer` 는 접속한 플레이어이거나 콘솔 로그용 콘솔이다(콘솔은 한 번).
- 렌더러를 정한 채팅은 **서명 없는 시스템 메시지**로 간다(바닐라 `unsigned-as-system` 과 같은 길): 채팅 신고 대상이 되지 않고, 채팅을 숨긴 사람은 받지 않고, 서명 체인에는 영향이 없다. 정하지 않으면 지금과 똑같이 서명된 채팅이다.
- 불리는 스레드는 이벤트를 부른 연결 스레드다 — 오래 걸리는 일은 하지 않는다. 렌더러가 예외를 던지면 그 사람에게는 바닐라 서식(`ChatRenderer.vanilla()`)으로 보내고 로그를 남긴다.
- 서식을 바꾸지 않고 내용만 바꾸려면 기존처럼 `message(...)`.

## 구조 (서버)

- 층 저장소는 접속(서버 `Player`)마다 `entity.view.ViewLayers`: `BlockLayer`(월드별·청크별 불변 맵을 갈아 끼움), `EquipmentLayer`, 개인 `WorldBorder`. 층이 없으면 `active` 가 거짓이라 패킷마다 volatile 읽기 하나다.
- 덧입히기는 연결의 `ViewRewriter`(K2 와 같은 지점, 플러그인 패킷 리스너보다 앞)가 한다. `ChunkBodyRewriter` 는 청크 본문을 섹션 단위로 풀어 **덮을 칸이 있는 섹션만** 다시 인코딩하고(비공기 수도 다시 센다) 나머지 바이트·블록 엔티티·빛은 그대로 옮긴다. 덧입힌 본문은 공유 압축 프레임(`SharedChunkFrames`)에 올리지 않고 그 사람에게만 압축한다.
- **`ConnectionHandler.send` 도 같은 `ViewRewriter` 를 거친다.** 접속 직후의 스폰 청크·차원 이동·보더 초기화는 `Player.send` 가 아니라 이 길로 나가기 때문이다(그전에는 K2 의 차원 추적도 이 패킷들을 못 봤다 — 이제 `LoginPlay`·`Respawn` 을 지나며 현재 차원을 안다).
- 걷을 때 진짜 값은 그 칸을 가진 region 스레드에서 읽는다(`submitAtOwnedChunk`). 올라와 있지 않은 청크는 건너뛴다(올라올 때 진짜 청크가 간다).

## 성능

- 층이 없는 접속: 패킷마다 `ViewLayers.active()` 의 volatile 읽기 두어 개와 `instanceof` 몇 번(전부 이벤트 루프). 청크 공유 프레임 경로(`SharedChunkFrames`)는 그대로다.
- 층이 있는 청크: 그 청크를 보낼 때만 한 번 풀고 다시 인코딩한다(섹션 24개 중 덮은 섹션만). 시험에서 청크 하나 다시 쓰기는 한 자릿수 ms 이하다.
