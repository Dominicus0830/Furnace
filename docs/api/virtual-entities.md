# 가상 엔티티 · 가짜 플레이어(NPC) · 가짜 탭 줄

월드에 없는 엔티티를 **패킷만으로** 가까운 플레이어 화면에 띄우는 API다(홀로그램·NPC·안내 몹). 입구는 `Furnace.server().virtualEntities()`.
[패킷 API](packets.md) 위에 쌓였고, 서버 상태(충돌·AI·저장·엔티티 수 상한·`/kill`)에 아무 영향이 없다. 엔티티 번호는 서버 엔티티와 겹치지 않게 같은 번호표에서 받는다.

## 빠른 예

```java
var ve = server().virtualEntities();

// 1) 가상 주민 + 클릭 잡기 (서버에 없는 엔티티여도 클라이언트는 클릭 패킷을 보낸다)
VirtualEntity shop = ve.spawn(this, "minecraft:villager", new Location("minecraft:overworld", 0, 64, 0));
shop.setCustomName(Text.text("상점"));
shop.setCustomNameVisible(true);
shop.onInteract(i -> openShop(i.player()));          // 그 플레이어의 스케줄러 스레드에서 불린다

// 2) 가짜 플레이어(NPC): 스킨은 "textures" 속성, 머리 위 이름은 기본으로 숨김
VirtualPlayer guard = ve.spawnPlayer(this, "Guard", List.of(ProfileProperty.textures(value, signature)), loc);
guard.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of("minecraft:iron_sword"));
guard.setSneaking(true);

// 3) 가짜 탭 줄 (그 플레이어 화면에만)
var line = ve.tabList(player).add(this, "서버 정보", List.of(), GameMode.CREATIVE, 0, Text.text("§6play.example.com"));
line.setLatency(5);
```

## 보이는 범위

- 전역 스레드가 **2틱마다** 한 번, 같은 월드이고 `trackingRange`(기본 48블록) 안인 플레이어에게 나타나게/사라지게 한다. 이미 보는 사람은 범위 + 4블록을 벗어나야 사라진다(경계에서 깜박이지 않게).
- 플레이어별 `show`/`hide`/`resetVisibility` 가 `setVisibleByDefault` 보다 우선한다. 바꾸면 바로 반영된다.
- 월드 이동·접속 끊김·재접속·차원 이동을 알아채 다시 보내거나 상태를 버린다(차원을 옮기면 클라이언트가 엔티티를 잃으므로).
- 비용: 엔티티 수 × 접속자 수 거리 비교가 2틱마다 한 번이다. 가상 엔티티가 없으면 작업은 바로 끝난다. 수천 개를 쓰는 용도라면 공간 색인이 필요하다(아직 없음).

## 스레드와 정리

- 모든 메서드는 어느 스레드에서 불러도 된다. 한 엔티티의 변경과 추적은 그 엔티티의 모니터 아래에서 순서대로 일어난다.
- `onInteract` 처리기는 **그 플레이어의 스케줄러 스레드**에서 불린다. 우클릭은 주 손만 한 번, 공격은 한 번 온다(보조 손·`INTERACT_AT` 은 걸러짐). 그 엔티티를 보고 있는 플레이어의 클릭만 전해진다.
- **플러그인이 꺼지면** 그 플러그인의 가상 엔티티·가짜 탭 줄이 자동으로 지워지고(화면에서도 사라짐) 처리기도 풀린다(핫 리로드 때 옛 클래스로더가 새지 않게).

## 가짜 플레이어(NPC)

- 클라이언트는 플레이어 엔티티를 그리려면 프로필 정보가 먼저 필요하다: 서버가 `player_info_update`(스킨 포함) → `add_entity` 순서로 보낸다. 사라질 때는 반대로 `remove_entities` → `player_info_remove`.
- 탭 목록에는 기본으로 올리지 않는다(`setListed(true)` 로 올림). 이름표는 숨김 팀(`fnpc_<번호>`)에 프로필 이름을 넣어 가린다. 같은 프로필 이름의 NPC 끼리는 이름표 설정이 함께 적용된다.
- 스킨은 엔티티 생성 때만 읽히므로 `setSkin` 은 보던 플레이어에게 지웠다 다시 만드는 식으로 반영된다.
- 웅크림은 상태 비트와 자세 둘 다 필요해 `setSneaking` 이 둘을 함께 보낸다. 그 밖의 자세는 `setMetadata(6, Value.ofPose(n))`.

## 가짜 탭 줄

- 그 플레이어의 **연결**에 묶인다(나가면 사라지고, 다시 접속하면 새로 더해야 한다). 플러그인이 꺼지면 그 플러그인이 더한 줄은 지워진다.

## 새로 생긴 패킷 래퍼

`SET_ENTITY_DATA`(칸 번호 + 값: byte·int·long·float·boolean·string·텍스트·옵션 텍스트·자세), `PLAYER_INFO_UPDATE`, `PLAYER_INFO_REMOVE` 가 타입 래퍼가 됐다(34종).
- `SET_ENTITY_DATA` 는 아이템·입자·NBT 같은 값 종류를 만나면 거기서 멈추고 `Unsupported` 로 남긴다 — 관찰·취소는 되지만 그 패킷을 바꿔 다시 보낼 수는 없다(원본이 그대로 간다).
- `PLAYER_INFO_UPDATE` 는 채팅 세션 초기화 비트가 든 패킷을 읽지 않는다(관찰·취소만).
- 장비(`set_equipment`)·팀(`set_player_team`)은 아직 타입 래퍼가 아니다. 가상 엔티티 서비스가 서버 내부 패킷으로 보낸다.

## 한계

- 서버가 모르는 엔티티라 AI·물리·충돌·피해가 없다. 움직임은 `teleport` 로만(상대 이동 패킷 없음).
- 메타데이터 칸 번호는 바닐라 엔티티 종류마다 다르다(`setMetadata` 는 그대로 보낸다). 편의 메서드는 공통 칸(상태 비트·이름·침묵·무중력)과 플레이어 겉 레이어(칸 17)만 다룬다.
- 화면에서 어떻게 보이는지(스킨·이름표·자세·장비)는 실제 클라이언트로 확인할 항목이다(`docs/user-todo.md`).
