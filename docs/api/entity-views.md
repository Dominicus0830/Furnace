# 엔티티 보이기·숨기기 · 변장 (K2)

실제 월드의 엔티티와 플레이어를 **플레이어마다 다르게** 보이게 하는 API다. 입구는 `Furnace.server().entityViews()`, 자주 쓰는 셋은 `Player` 에도 있다(`hideEntity`·`showEntity`·`canSee`).
서버 상태(AI·충돌·피해·저장·명령 결과)는 그대로이고 **보는 사람에게 가는 패킷만** 바뀐다. 규칙이 하나도 없으면 바닐라와 똑같이 동작한다.
월드에 없는 엔티티(홀로그램·NPC)는 [가상 엔티티](virtual-entities.md) 쪽이다.

## 빠른 예

```java
var views = server().entityViews();

// 1) 숨기기 (Bukkit hideEntity / hidePlayer 와 같은 뜻)
viewer.hideEntity(this, zombie);          // viewer 화면에서 zombie 가 사라진다
viewer.hideEntity(this, otherPlayer);     // 플레이어는 탭 목록에서도 빠진다
viewer.showEntity(this, zombie);

// 2) 기본 숨김 + 몇 사람에게만 (Bukkit setVisibleByDefault)
views.setVisibleByDefault(this, questMob, false);
views.show(this, questTaker, questMob);

// 3) 변장: 모두에게 크리퍼로, 한 사람에게는 진짜 모습
views.disguise(this, player, Disguise.as("creeper").withCustomName(Text.text(player.name())).withCustomNameVisible(true));
views.disguise(this, player, teammate, Disguise.none());

// 4) 몹을 플레이어 모습(스킨)으로, 이름표 숨김
views.disguise(this, villager, Disguise.player("Guard", List.of(ProfileProperty.textures(value, signature))).withNameTagVisible(false));
```

## 숨기기 규칙

- 여러 플러그인이 숨기면 **모두가 거둬야** 보인다(플러그인마다 따로 센다). 자기 자신은 숨길 수 없다(무시).
- 기본 숨김은 그것을 건 플러그인이 하나라도 있으면 켜진다. 그때는 `show` 받은 사람만 본다.
- 보는 쪽 규칙은 **그 접속 동안만** 산다. 다시 접속하면 없다(Bukkit 과 같다 — 접속 이벤트에서 다시 건다). 대상 쪽(숨겨진 플레이어)이 나갔다 와도 다른 사람의 숨김은 남는다.
- 숨긴 플레이어는 그 사람의 탭 목록에서도 빠지고, 600틱마다 가는 핑 갱신에도 실리지 않는다. 숨긴 엔티티의 번호로 가는 패킷(피해·효과·애니메이션·데이터)은 추적하지 않으므로 가지 않는다. 엔티티가 내는 소리·입자는 위치 기준이라 그대로 들린다(Bukkit 과 같다).
- 플러그인이 꺼지면 그 플러그인이 건 숨김·기본 숨김·변장이 모두 거둬지고 화면도 다시 맞춘다(핫 리로드 때 남지 않게).

## 변장

- 전역 변장(`disguise(plugin, target, d)`)과 플레이어별 변장(`disguise(plugin, target, viewer, d)`)이 있다. 플레이어별이 앞서고, `Disguise.none()` 은 "이 사람에게는 진짜 모습"이다.
- 본인 화면은 바뀌지 않는다(클라이언트는 자기 자신을 엔티티로 받지 않는다).
- 모습이 바뀌면 보는 사람에게 **지웠다 다시 스폰**한다(한 번 깜박인다). 같은 모습이면 다시 보내지 않는다.
- 메타데이터는 두 종류에 **공통인 칸만** 넘긴다 — 칸 번호는 클래스 계층 순서로 붙고, 값 종류가 다른 칸을 보내면 클라이언트가 끊기기 때문이다.
  - 모든 엔티티 공통(0~7): 상태 비트(불·웅크림·달리기·투명·발광)·공기·이름·이름 보임·무음·무중력·자세·얼음.
  - 둘 다 살아 있으면(8~14): 손 상태·체력·효과 입자·화살·벌침·잠자리.
  - 둘 다 몹이면(15): 몹 비트(AI 없음·왼손잡이·공격 자세).
  - 같은 종류면 전부. 변장 종류에만 있는 칸(크리퍼 충전, 슬라임 크기 등)은 `withMetadata(칸, 값)` 으로 정한다. **칸 번호·값 종류는 마인크래프트 버전마다 다르다**(1.21.1 기준) — 틀리면 클라이언트가 끊긴다.
- 덮어쓴 칸은 진짜 엔티티 값이 바뀌어도 보내지 않는다. 스폰 바로 뒤에 덮어쓴 값을 한 번 보낸다.
- 살아 있지 않은 모습(눈덩이·아이템·표시 엔티티 …)으로 보이는 동안 속성(`update_attributes`)·장비 패킷은 빼고 보낸다(클라이언트가 끊기므로).
- 플레이어 모습: 변장마다 새 프로필 uuid 로 탭에 올리지 않는 `player_info_update` 를 먼저 보내고 스폰한다(스킨은 그 프로필의 "textures"). 지울 때·차원을 옮길 때 그 프로필을 걷는다. 진짜가 플레이어가 아니면 스킨 겉 레이어(모자·소매 등)를 모두 켜서 보낸다(`withMetadata(17, …)` 로 바꿀 수 있다). 이름표를 숨기면 보는 사람에게만 숨김 팀(`fdis_<번호>`)을 보낸다 — 같은 프로필 이름이 다른 팀에 있으면 클라이언트에서 옮겨진다.
- `withSpawnData(n)` 은 `add_entity` 의 data 칸(떨어지는 블록의 블록 상태 번호, 액자·그림 방향 등)이다.

## 한계

- 충돌 상자·클릭 판정은 클라이언트가 **변장 종류의 크기**로 한다(서버는 진짜 크기로 확인). 키 큰 몹으로 변장한 작은 엔티티는 빈 곳을 쳐도 맞지 않는다.
- 경험치 구슬은 스폰 패킷에 uuid 가 없어 변장되지 않는다. 엔더 드래곤 모습은 클라이언트가 조각마다 다음 번호를 쓰므로 다른 엔티티 번호와 겹칠 수 있다.
- 플러그인이 패킷 API 로 직접 보낸 패킷(`RawPacket`)은 바꾸지 않는다. 플러그인 패킷 리스너는 **바뀐 뒤의** 패킷을 본다.
- 변장한 플레이어가 내는 소리·입자는 진짜 종류 그대로다(서버가 내는 것이므로).

## 구조 (서버)

- 규칙: `server.entity.view.EntityViewRules` — 대상(엔티티 uuid)마다 불변 스냅샷, 바꿀 때 통째로 갈아 끼운다. 추적기·연결 이벤트 루프가 락 없이 읽는다. 규칙이 없으면 `active` 가 거짓이라 volatile 읽기 하나로 끝난다.
- 보이기: 몹·물건은 `EntityTracker.updatePlayer` 의 보임 조건에, 플레이어끼리는 `Region.updateVisibility`·`syncTabList` 에 규칙을 넣었다. 규칙이 바뀌면 몹은 엔티티 작업 대기열로 다음 틱에 `ChunkLevel.refreshViews`, 플레이어는 region 이 매 틱 다시 보고(탭은 `bumpRoster`) 모습 세대(`epochFor`)가 바뀌면 다시 스폰한다.
- 변장: `ViewRewriter`(연결마다, 이벤트 루프에서만) 가 스폰 때 그때 규칙으로 모습을 엔티티 번호에 묶고, 지울 때 푼다. 그 사이의 메타데이터·속성·장비는 묶인 모습 기준으로 거른다 — 규칙이 도중에 바뀌어도 클라이언트가 가진 엔티티와 어긋나지 않는다.
- 버전 중립: API(`Disguise`)는 종류 이름·값만 갖고, 메타데이터·프로필 패킷은 패킷 API 변환 계층(`Protocol767`)이 인코딩한다. 칸 갈래 표(`EntityKinds`)만 버전별이다.

## 성능 (2026-11-07, 4코어 컨테이너, `EntityViewTrackerBench`)

좀비 500 × 플레이어 20, 모두 서로 보이는 거리. "쌍" = 엔티티 × 플레이어 한 번.

| 규칙 | 플레이어 이동 보임 판정 (ns/쌍) | 변화 방송 + 연결 쓰기 (ns/쌍) |
|---|---|---|
| 기준 커밋(K2 전) | 18.2 / 20.5 | 203 / 222 |
| K2, 규칙 없음 | 18.7 / 20.3 | 205 / 220 |
| 다른 엔티티 1000개에만 규칙 | ~26 | ~220 |
| 모든 엔티티 기본 숨김 + 모두에게 보임(최악) | ~51 | ~220 |
| 모든 엔티티 전역 변장 | ~30 | ~226 |

규칙이 없으면 기준과 차이가 잡음 안이다(두 번 번갈아 잰 중앙값). 규칙이 많아도 쌍당 수십 ns 라 틱 예산(50ms)에 비해 작다.
다시 재기: `./gradlew :furnace-server:bench --tests '*EntityViewTrackerBench' -Dfurnace.bench.rules=none|unrelated|all|disguise`.
