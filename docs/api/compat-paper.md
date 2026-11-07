# Bukkit·Paper 플러그인 호환 어댑터 (H1)

`furnace-compat-paper` 모듈은 `plugin.yml`(또는 `paper-plugin.yml`)로 만든 Bukkit·Paper 플러그인 jar 를 Furnace 위에서 로드·실행한다. 이 문서는 **1차(H1)** 의 범위다: 로더, `org.bukkit` API 의 일부, 이벤트 24종. 명령·스케줄러(H2), 인벤토리·아이템·엔티티 어댑터(H3), 실제 플러그인 시험(H4)과 NMS·CraftBukkit(H5~H7)은 뒤 묶음이다.

## 켜고 끄기

`furnace.properties` 의 `plugins.compat.bukkit`(기본 켜짐). 끄면 전처럼 Bukkit jar 를 "건너뛴다"는 경고만 남긴다. 서버 단일 jar(`fatJar`)에 이 모듈이 들어 있다. 모듈이 클래스패스에 없으면(다른 방식으로 서버를 붙일 때) 켜져 있어도 어댑터가 없는 것과 같다.

## 어디에 넣나

`plugins/` 와 `plugins/bukkit-plugins/` 둘 다 읽는다(설명 파일이 `furnace-plugin.*` 이면 Furnace 플러그인, `plugin.yml`·`paper-plugin.yml` 이면 Bukkit 플러그인). 두 설명 파일이 모두 있으면 Furnace 쪽이 이긴다. 플러그인 데이터 폴더는 `plugins/<이름>/`(Bukkit 과 같다).

## 구조

서버는 `PluginFormat`(furnace-api) 서비스 인터페이스만 안다. 호환 모듈이 `META-INF/services` 로 `CompatFormat` 을 내놓는다.

- `CompatFormat` — 설명 파일 해석. `paper-plugin.yml` 의 `dependencies.server.<이름>.load(BEFORE/AFTER/OMIT)·required` 를 Furnace 의 `depend/softdepend/loadbefore` 로 옮긴다. `api-version` 이 1.21.1 보다 새로우면(예 1.21.4) 로드를 거절한다(서버의 `ApiVersion` 검사와 같은 뜻). `api-version` 이 없으면 경고 후 로드(옛 플러그인).
- `BukkitPluginAdapter` — Bukkit `JavaPlugin` 한 개를 감싸는 `FurnacePlugin`. `onLoad/onEnable/onDisable` 를 그대로 잇고, 꺼질 때 `HandlerList.unregisterAll` 을 한다.
- `EventBridge`·`Mappings` — Furnace 이벤트 ↔ Bukkit 이벤트. Bukkit 처리기가 등록될 때 해당 Furnace 이벤트를 (플러그인, 우선순위)마다 버스에 구독한다. Furnace 이벤트 하나에 Bukkit 이벤트 객체는 하나만 만들어 나눠 쓰고, 처리기가 바꾼 값(취소·메시지·피해량 등)은 Furnace 이벤트에 되돌려 쓴다.
- `Compat*` — `Player/World/Block/Entity/Server/Chunk/CommandSender` 의 Bukkit 보기. 값은 호출 때마다 Furnace 객체에서 읽는다(복사해 두지 않는다).

우선순위(LOWEST~MONITOR), `ignoreCancelled`, 취소 전달은 Furnace 버스가 지킨다. 같은 우선순위의 Furnace 고유 플러그인과는 등록 순이다.

## 지원하는 이벤트 (24)

| Bukkit | Furnace | 되돌려 쓰는 값 |
|---|---|---|
| `PlayerJoinEvent` / `PlayerQuitEvent` | 같은 이름 | 입·퇴장 메시지 |
| `PlayerKickEvent` | `PlayerKickEvent` | 사유, 취소 |
| `AsyncPlayerChatEvent` | `AsyncChatEvent` | 메시지, 서식(`setFormat`), 취소 |
| `PlayerMoveEvent` / `PlayerTeleportEvent` | 같은 이름 | 취소 |
| `PlayerRespawnEvent` | 같은 이름 | 부활 위치 |
| `PlayerChangedWorldEvent`, `PlayerGameModeChangeEvent`, `PlayerToggleSneak/Sprint/FlightEvent` | 같은 이름 | 취소 |
| `PlayerCommandPreprocessEvent` | 같은 이름 | 명령 문자열, 취소 |
| `PlayerLevelChangeEvent`, `PlayerExpChangeEvent` | 같은 이름 | 경험치 양 |
| `BlockBreakEvent` | `BlockBreakEvent` | 취소 (플레이어가 깬 것만) |
| `EntityDamageEvent` | 같은 이름 | 피해량, 취소 |
| `PluginEnableEvent` / `PluginDisableEvent` | 같은 이름 | — (Bukkit 플러그인만 알린다) |
| `ServerCommandEvent` | 같은 이름 | 명령 문자열, 취소 |
| `ServerLoadEvent` | 같은 이름 | — |
| `WorldLoadEvent`, `WorldUnloadEvent`, `ChunkLoadEvent`, `ChunkUnloadEvent` | 같은 이름 | 월드 내림 취소(`WorldUnloadEvent` 만) |

플러그인이 만든 자기 이벤트는 `PluginManager#callEvent` 로 그대로 돈다(Furnace 이벤트와 이어지지 않는 순수 Bukkit 이벤트).

## 지원하지 않는 것과 실패 방식

Furnace 이벤트가 알리지 않는 값은 지어내지 않는다. 읽기는 정직한 기본값(예: 순간이동 원인 `UNKNOWN`), 쓰기는 `UnsupportedFeature`(= `UnsupportedOperationException`)로 무엇이 없는지 말하며 던진다. 예: `PlayerMoveEvent#setTo`, `AsyncPlayerChatEvent#getRecipients()` 바꾸기(받는 사람마다 다른 서식은 `AsyncChatEvent#renderer` 가 대신한다).

아직 없는 `org.bukkit` 영역: 인벤토리·`ItemStack`·`Inventory`(H3), 명령 실행기·스케줄러(`getScheduler`, `Command`; H2), 엔티티 종류별 하위 인터페이스, 나머지 수백 개 이벤트, `Bukkit.getOnlinePlayers` 이외의 대부분의 서버 보조(권한 첨부는 `Player#hasPermission` 만). 없는 클래스를 부르면 플러그인 쪽에서 `NoClassDefFoundError` 가 나 그 플러그인만 실패한다.

## Material

`org.bukkit.Material` 은 1.21.1 의 블록 레지스트리와 아이템 레지스트리의 합집합이다. 생성기 `tools/compat/gen-bukkit-material.py` 가 `furnace-server` 의 `blocks/1.21.1.txt`·`items/1.21.1.txt`·`items/properties_1.21.1.txt` 로 만든다. 레지스트리가 바뀌면 다시 돌려 커밋한다(`ApiValueTypesTest.materialMatchesTheRegistries` 가 어긋남을 잡는다). Bukkit 의 옛 이름(`LEGACY_*`)·세대 변환 이름은 두지 않았다.

## 스레드

Bukkit 은 메인 스레드 하나를 가정한다. 이 1차는 Furnace 이벤트가 불리는 스레드(region·global·채팅은 비동기) 그대로 Bukkit 처리기를 부른다. `Block#getType` 같은 월드 읽기는 소유 region 스레드에서만 안전하다. 메인 스레드로 모아 주는 일은 H2(스케줄러)의 몫이다.

## 시험

`./gradlew -PskipOracle=true :furnace-compat-paper:test` — 헤드리스 서버(`furnace-test`)에 시험용 `plugin.yml` jar 를 올려 로드 순서·설정·이벤트 24종 전달·쓰기 되돌림·우선순위·취소·미지원 오류를 확인한다. 서버 쪽 SPI 는 `PluginFormatSpiTest`.

## 법적 메모

`org.bukkit` 클래스의 공개 이름·서명은 호환을 위해 필요한 만큼만 새로 썼다(Bukkit 소스를 복사하지 않았다). 라이선스 판단은 `docs/user-todo/entries/2026-11-07-h1.md` 의 결정 항목.
