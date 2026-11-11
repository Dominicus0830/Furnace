# FastAsyncWorldEdit 2.16.2 를 Furnace 에 올리기 — 1단계 (2026-11-11)

도미니코의 첫 실행 로그(FAWE 2.16.2+eef5a6f Paper jar)에서 출발했다. 막혔던 곳은 둘이었다.

1. `WorldEditPlugin.onLoad` 가 "You are attempting to run the Paper FAWE jar on a Spigot server" 를 던졌다.
2. `org.bukkit.event.player.PlayerInteractEvent` 가 없어 `BrushListener`·`WorldEditListener` 등록이 통째로 실패했다.

## 1. 원인과 고친 것

| 막힌 곳 | 원인(FAWE 바이트코드·원문 2.16.2 대조) | 고친 것 |
|---|---|---|
| Paper 판별 | `PaperSupport.PAPER` = `Class.forName("com.destroystokyo.paper.PaperConfig")` 또는 `io.papermc.paper.configuration.Configuration` 이 있는가. jar 매니페스트 `FAWE-Plugin-Jar-Type: mojang` 인데 Paper 가 아니면 `IllegalStateException`. | 호환 계층에 `io.papermc.paper.configuration.Configuration`(상수만) 추가. PaperLib 도 같은 방식이라 다른 Paper 용 jar 에도 같은 효과. |
| 리스너 등록 | Bukkit 은 리스너 클래스의 메서드 서명을 한꺼번에 읽어, 처리기 하나의 이벤트 클래스가 없으면 그 리스너 전체가 빠진다. FAWE 가 쓰는 이벤트 37종 중 26종이 없었다. | 26종 추가. 10종은 Furnace 서버 이벤트와 이었고(PlayerInteract·ItemHeld·BlockPhysics·FromTo·Burn·Ignite·Redstone·LeavesDecay·FurnaceBurn·FurnaceSmelt/BlockCook), 15종은 클래스만 두고 처리기를 달면 "아직 알리지 않는다" 경고를 남긴다(`docs/api/compat-paper.md`). `AsyncTabCompleteEvent` 는 LuckPerms 스레드가 맡는다. |
| onLoad 다음 장애물 | `CommandRegistration` 이 `Server#getHelpMap().registerHelpTopicFactory`, `BukkitServerInterface` 가 `org.bukkit.BlockChangeDelegate` 를 쓴다. | `HelpMap`·`HelpTopic`·`HelpTopicFactory`·`CompatHelpMap`, `BlockChangeDelegate` 추가. |

## 2. 어떻게 쟀나

사용자 jar 는 컨테이너에서 받을 수 없어(Modrinth·GitHub 릴리스·enginehub 저장소 차단) Maven Central 의 `FastAsyncWorldEdit-Bukkit/Core/Libs-*` 2.16.2 와 런타임 의존을 한 jar 로 묶고 매니페스트를 Paper jar 처럼(`FAWE-Plugin-Jar-Type: mojang`) 만들어 `liveProbe` 로 헤드리스 서버에 올렸다. 실제 Paper jar 와 다른 점: 버전 어댑터(`adapter.impl.fawe.v1_21_R1` 등)가 없고, `jlibnoise`·`jchronic` 은 enginehub 에만 있어 빈 껍데기로 대신했고(노이즈 브러시·시간 해석만 영향), bStats 는 ASM 으로 옮겨 실었다. 이 시험 jar 와 껍데기는 저장소에 넣지 않았다.

결과(이 PR 뒤):

```
[FastAsyncWorldEdit] Loading … → onLoad 성공(Paper 판별 통과, 플랫폼 등록)
[FastAsyncWorldEdit] Enabling … → 명령 등록, WEPIF, BrushListener·WorldEditListener 등록
PlayerCommandSendEvent 처리기를 등록했지만 Furnace 서버는 아직 이 이벤트를 알리지 않는다
Failed to register events for …AsyncTabCompleteListener (AsyncTabCompleteEvent — LuckPerms 스레드)
Server reload detected …   (아래 3-2)
This FastAsyncWorldEdit version does not fully support your version of Bukkit.   (아래 3-1)
```

## 3. 남은 것

### 3-1. 편집에는 버전 어댑터가 꼭 있어야 한다 (결정 필요)

FAWE 는 블록 읽기·쓰기를 모두 `BukkitImplAdapter`(= NMS 어댑터)로 한다 — 예: `BukkitWorld#get(chunkX, chunkZ)` 가 `getBukkitImplAdapter().get(...)` 을 바로 부른다(어댑터가 없으면 `NullPointerException`). Bukkit API 로 돌아가는 길이 없다. 1.21.1 용 `v1_21_R1.PaperweightFaweAdapter` 는 청크 섹션·팔레트(`LevelChunkSection`·`PalettedContainer`)·`ServerLevel` 상속 등 등급 C 심볼(사용자 로그: 미지원 340개, 상속 거부 56개)을 쓴다. 고르는 길:

- **A. Furnace 가 FAWE 용 어댑터를 직접 제공**(추천): FAWE 의 `IBukkitAdapter`/`BukkitImplAdapter` 를 Furnace 블록·청크 API 위에 구현해, FAWE 가 실릴 때 그 플러그인 로더에 붙인다(`-Dworldedit.bukkit.adapter` 와 같은 "지정 후보" 경로). 편집이 Furnace region 스레드 규칙을 지키게 만들 수 있다. FAWE 인터페이스가 판마다 바뀌어 지원 판을 고정해야 한다.
- **B. NMS shim 을 늘려 원래 어댑터를 돌린다**: 청크 내부 구조가 Furnace 와 달라(등급 C) 정확하게 만들 수 없다. 추천하지 않는다.
- **C. 여기서 멈춘다**: FAWE 는 켜지지만 블록을 바꾸는 명령은 실패한다.

### 3-2. `load: STARTUP` 순서

FAWE 는 `load: STARTUP` 이다. Paper 는 STARTUP 플러그인을 월드를 불러오기 **전에** 켜고(`Bukkit.getWorlds()` 가 비어 있음), FAWE 는 그때 `WorldInitEvent` 를 기다린다. Furnace 는 모든 플러그인을 월드 준비 뒤에 켜므로 FAWE 가 "Server reload detected" 경고를 내고 월드 데이터 준비를 바로 한다(동작은 하지만 경고는 Paper 와 다르다). 서버 기동 순서를 바꾸는 일이라 따로 다룬다.

### 3-3. 아직 없는 Bukkit API (Central jar 기준)

`compatReport -PextraJars=…` 로 잰 FAWE 의 Bukkit 계열 요구 500개 중 없는 것 141개(충족 71.8%). 없는 클래스 37개 — 많이 부르는 순: `TreeType`, `block.Biome`, `AsyncTabCompleteEvent`(다른 스레드), `Effect`, `Registry`, `PermissionAttachment`, `Tag`, `Player.Spigot`, `PermissionAttachmentInfo`, `Datapack`·`DatapackManager`, `UnsafeValues`(다른 스레드), `WorldCreator`, `Chest`, `Tameable`, `BiomeProvider`, `PermissibleBase`, `TileState`, 엔티티 하위 타입 여럿. 대부분은 해당 명령(나무 심기 `//forest`, 바이옴 `//setbiome`, 권한 첨부 등)을 부를 때만 `NoClassDefFoundError`/`NoSuchMethodError` 로 드러나고 켜지기는 막지 않는다. 바이옴·태그·레지스트리는 H4 묶음 C(값 타입)와 겹친다.

## 4. 시험

`InteractAndBlockEventsTest`(Paper 판별, 공기 클릭 모양, 두 결과 → 취소 합치기, 블록·화로 값 되돌려 쓰기, 안 알리는 이벤트 목록), `EventBridgeTest`(이어진 35종이 처리기에 닿는지).
