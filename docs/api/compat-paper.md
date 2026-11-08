# Bukkit·Paper 플러그인 호환 어댑터 (H1)

`furnace-compat-paper` 모듈은 `plugin.yml`(또는 `paper-plugin.yml`)로 만든 Bukkit·Paper 플러그인 jar 를 Furnace 위에서 로드·실행한다. 이 문서는 **1차(H1)** 의 범위다: 로더, `org.bukkit` API 의 일부, 이벤트 24종. 명령·스케줄러(H2), 아이템·인벤토리·블록·엔티티 어댑터(H3, 아래 "H3" 절), 실제 플러그인 시험(H4)과 NMS·CraftBukkit(H5~H7)은 뒤 묶음이다. CraftBukkit 어댑터(H6 — Bukkit 구현 클래스가 `org.bukkit.craftbukkit.*` 의 `CraftServer`·`CraftWorld`·`CraftEntity`·`CraftPlayer`·`CraftItemStack` 이고 `getHandle()` 이 NMS shim 을 준다, 버전 접미사 `v1_21_R1` 이름도 풀린다)는 [`compat-h6-2026-11-07.md`](../feature-parity/compat-h6-2026-11-07.md).

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

## 지원하는 이벤트 (25)

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
| `PlayerRegisterChannelEvent` / `PlayerUnregisterChannelEvent` (처리기 목록은 `PlayerChannelEvent`) | `PlayerChannelEvent` | — (연결 스레드) |

플러그인이 만든 자기 이벤트는 `PluginManager#callEvent` 로 그대로 돈다(Furnace 이벤트와 이어지지 않는 순수 Bukkit 이벤트).

## 지원하지 않는 것과 실패 방식

Furnace 이벤트가 알리지 않는 값은 지어내지 않는다. 읽기는 정직한 기본값(예: 순간이동 원인 `UNKNOWN`), 쓰기는 `UnsupportedFeature`(= `UnsupportedOperationException`)로 무엇이 없는지 말하며 던진다. 예: `PlayerMoveEvent#setTo`, `AsyncPlayerChatEvent#getRecipients()` 바꾸기(받는 사람마다 다른 서식은 `AsyncChatEvent#renderer` 가 대신한다).

아직 없는 `org.bukkit` 영역: 엔티티 종류별 하위 인터페이스, 커스텀 `Inventory` 생성(`createInventory`; 서버에 메뉴 API 연결 전이라 `UnsupportedFeature`), 나머지 수백 개 이벤트, `Bukkit.getOnlinePlayers` 이외의 대부분의 서버 보조(권한 첨부는 `Player#hasPermission` 만). 없는 클래스를 부르면 플러그인 쪽에서 `NoClassDefFoundError` 가 나 그 플러그인만 실패한다.

## Material

`org.bukkit.Material` 은 1.21.1 의 블록 레지스트리와 아이템 레지스트리의 합집합이다. 생성기 `tools/compat/gen-bukkit-material.py` 가 `furnace-server` 의 `blocks/1.21.1.txt`·`items/1.21.1.txt`·`items/properties_1.21.1.txt` 로 만든다. 레지스트리가 바뀌면 다시 돌려 커밋한다(`ApiValueTypesTest.materialMatchesTheRegistries` 가 어긋남을 잡는다). Bukkit 의 옛 이름(`LEGACY_*`)·세대 변환 이름은 두지 않았다.

## H3: 아이템·인벤토리·블록·엔티티·월드

설계·한계·Bukkit 과 다른 점은 `docs/feature-parity/compat-h3-2026-11-07.md`, 결정 항목은 `docs/user-todo/entries/2026-11-07-h3.md`.

- **아이템**: `ItemStack`(가변)·`ItemMeta`(`Damageable`·`Repairable` 포함, 복사 의미)·`ItemFlag`·`Enchantment`(42종 생성)·`PersistentDataContainer`(`PersistentDataType` 상수들, 아이템은 `custom_data/PublicBukkitValues`). 서버 `ItemStack`(불변 record)과는 `ItemSlot` 으로 잇는다.
- **인벤토리**: `Player#getInventory`(`PlayerInventory`: 핫바·주 칸·갑옷·왼손·`addItem`·`removeItem`·`getItemInMainHand` 등)는 칸을 거울로 비춘다. 소유 스레드가 아닌 곳의 쓰기는 예약되고 읽기에 겹쳐 보인다. `Bukkit#createInventory` 는 `UnsupportedFeature`.
- **블록**: `Block#setType/getBlockData/setBlockData/getRelative/getFace/getChunk`, `BlockData`(`Material#createBlockData`, 문자열 `matches`).
- **엔티티·월드**: `Entity#getType`(`EntityType` 130종 생성)·`getNearbyEntities`·`setCustomName`·엔티티 영속 데이터(소유 region 에서만 읽기), `World#spawnEntity/getNearbyEntities/getChunkAt`. 서버 API 에 동기 쌍 `World#spawnEntityNow`·`nearbyEntitiesNow`, `Entity#editPersistentDataNow`, `Player#heldSlot/setHeldSlot` 을 더했다.
- 생성 파일(`Material`·`EntityType`·`Enchantment`)은 `tools/compat/gen-bukkit-material.py`·`gen-bukkit-registries.py` 로 다시 만든다.
- 시험: `InventoryAdapterTest`·`BlockEntityWorldAdapterTest`·`KitPluginTest`(키트 플러그인 jar)·`RegistryValueTypesTest`.

## JavaPlugin 의 명령·스케줄러·서비스 (H4 다음 묶음 A)

`JavaPlugin#getCommand`(plugin.yml 의 `commands:` → `PluginCommand`, 기본 실행기는 플러그인 본체)·`getProvidingPlugin`·`getPlugin(Class)`, `Command`·`CommandMap`/`SimpleCommandMap`(명령 트리에 이어짐, `getKnownCommands()` 에서 지우면 트리에서도 빠짐), `BukkitScheduler`·`BukkitRunnable`·`BukkitTask`, `ServicesManager`, `Bukkit.getScheduler/getServicesManager/isPrimaryThread/getCommandMap/getPluginCommand`. 설계·Bukkit 과 다른 점 10가지: `docs/feature-parity/compat-javaplugin-wiring-2026-11-07.md`. 시험: `JavaPluginWiringTest`(시험 안에서 컴파일한 플러그인 jar).

## 플러그인 메시징 (H4 묶음 E)

`Bukkit.getMessenger()`(`Messenger`/`StandardMessenger`), `PluginMessageListener`, `Player`·`Server` 의 `sendPluginMessage`·`getListeningPluginChannels`, `"BungeeCord"` ↔ `bungeecord:main` 이름 바꿈, 채널 등록 이벤트. 받은 메시지 리스너는 전역 스레드("주 스레드")에서 돌고, 보내기는 클라이언트가 그 채널을 알렸을 때만 나간다(Bukkit 과 같다). 설계·Bukkit 과 다른 점: `docs/feature-parity/compat-h4e-messaging-2026-11-08.md`. 시험: `PluginMessagingTest`.

## 스레드 (H2 가 H1 에 넘긴 D1·D2 에 대한 결정)

Bukkit 은 메인 스레드 하나를 가정한다. 이 1차는 Furnace 이벤트가 불리는 스레드(region·global·채팅은 비동기) 그대로 Bukkit 처리기를 부른다. `Block#getType` 같은 월드 읽기는 소유 region 스레드에서만 안전하다.

- **D1(옛 "주 스레드" 직렬화): (c) 경쟁을 감수하고 문서화한다.** 옛 플러그인이 정적 필드·`HashMap` 을 여러 region 스레드에서 동시에 만지면 경쟁이 날 수 있다. `PrimaryThread.enter()` 로 처리기를 감싸 상호 배제하는 방식(a)은 교착 위험이 있어 이 PR 에서 하지 않았다. 실제 플러그인으로 문제가 확인되면(H4) 후속에서 정한다.
- **D2(기본 실행기·`JavaPlugin.getCommand`·`getScheduler`): 위 "JavaPlugin 의 명령·스케줄러·서비스" 절에서 해결했다.** `PluginCommand` 가 Bukkit 규칙대로 실행기를 만들 때 플러그인 본체로 정한다.

## 알아 둘 점

- `plugins.compat.bukkit` 이 기본 켜짐이라 예전에 경고 후 건너뛰던 Bukkit jar 가 이제 로드를 시도한다. 아직 없는 API 를 쓰는 플러그인은 로드에 실패해 `failures` 에 들어가므로, `plugins.fail-fast=true` 인 서버는 부팅이 멈출 수 있다(그럴 때는 `plugins.compat.bukkit=false`).
- 읽기 기본값이 있는 곳(정직한 기본값): `PlayerKickEvent#getLeaveMessage`(영어 고정 "<이름> left the game"; Furnace 킥 이벤트에 없는 값), `CraftPlayer#getLocale`(없으면 `en_us`), `CraftEntity#teleport` 는 비동기 텔레포트의 결과를 기다리지 않고 `true`, `isDead` 는 `!isValid()`(언로드된 엔티티도 죽음으로 본다). `paper-plugin.yml` 의 `load: OMIT` 인 `required: true` 의존은 순서도 강제된다(Paper 는 순서 없음).
- 이벤트 값 되돌려 쓰기: 처리기 전달 직전에 Furnace 의 현재 값을 Bukkit 이벤트에 맞추고(그래서 사이에 낀 Furnace 처리기의 변경이 보인다), 전달 뒤 달라진 값만 Furnace 에 쓴다.

## H4: 실제 플러그인으로 잰 호환 정도

실제 Bukkit·Paper API 로 컴파일된 jar(Central 의 실제 플러그인 helper·CommandAPI 와 흔한 플러그인 라이브러리 11종)를 바이트코드로 대조하고 plugin.yml jar 는 헤드리스 서버에 올려 잰 표·우선순위·한계는 `docs/feature-parity/compat-h4-2026-11-07.md`, 생성 대조표는 `docs/feature-parity/compat-h4-coverage-2026-11-07.md`. H4 측정 때 helper·CommandAPI 둘 다 켜지지 못했다(첫 장애물: `JavaPlugin.getProvidingPlugin`, Brigadier 클래스 없음). A 묶음 뒤 helper 의 첫 장애물은 Guava(`com.google.common.base.Supplier`, 서버 제공 라이브러리 B — 사용자 결정), CommandAPI 는 그대로 Brigadier. B 묶음(2026-11-08)부터 Paper 가 싣는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등)는 호환 플러그인이 쓸 때만 받아 공유 로더에 붙인다(`docs/api/plugin-libraries.md`) — 그 뒤 둘 다 `PluginMessageListener`(묶음 E)에서 멈췄고, E 묶음(2026-11-08) 뒤 helper 는 `SimplePluginManager`, CommandAPI 는 Spigot 이름 NMS(H7d)에서 멈춘다. 호환 API 를 더하는 묶음은 `./gradlew -PskipOracle=true :furnace-compat-paper:compatReport` 로 대조표와 기준표(`realworld-baseline.properties`)를 새로 쓴다 — 기준표 시험(`RealWorldCompatTest`)이 후퇴를 막는다.

## 시험

`./gradlew -PskipOracle=true :furnace-compat-paper:test` — 헤드리스 서버(`furnace-test`)에 시험용 `plugin.yml` jar 를 올려 로드 순서·설정·이벤트 25종 전달·쓰기 되돌림·우선순위·취소·미지원 오류를 확인한다. 서버 쪽 SPI 는 `PluginFormatSpiTest`.

## 격리 보장 (Bukkit·Paper 플러그인이 없으면 호환 클래스는 올라오지 않는다)

호환 모듈(`org.bukkit.*`·어댑터·ASM, 약 0.6 MB)은 서버 `fatJar` 에 함께 실리지만 서버 핵심과 얽히지 않는다.
- **모듈 방향:** `furnace-server` 와 `furnace-api` 는 호환 모듈에 의존하지 않는다(호환 모듈 → API 한 방향. 서버 `fatJar` 만 둘을 합친다). `CompatIsolationStaticTest` 가 서버·API 클래스 파일에 `org/bukkit/` 등 호환 패키지 이름이 없음을 확인한다.
- **지연 로딩:** 서버는 시작할 때 호환 어댑터(`PluginFormat`)를 만들지 않는다. `PluginFormats` 가 클래스패스에 `META-INF/services` 자원이 있는지만 보고(클래스를 읽지 않는다), Furnace 설명 파일이 없는 jar(= Bukkit·Paper 플러그인)를 처음 만났을 때 한 번만 `ServiceLoader` 로 만든다. 그 전에는 호환 모듈 클래스가 JVM 에 하나도 올라오지 않는다 — `plugins/` 가 비었거나 Furnace 플러그인뿐이거나 `plugins/bukkit-plugins/` 가 비어 있어도 마찬가지다. 단 설명 파일이 없는 다른 jar(라이브러리 jar 를 잘못 둔 경우 등 — 원래 오류 jar)가 `plugins/` 나 `plugins/bukkit-plugins/` 에 있으면 그 판정을 위해 어댑터가 올라온다. 호환 모듈 자원은 있는데 어댑터를 만들 수 없으면(깨진 호환 모듈) Bukkit jar 는 실패로 세지 않고 경고 후 건너뛴다.
- **모듈이 없어도 된다:** 호환 모듈이 클래스패스에 아예 없어도(서버를 호환 없이 붙일 때) 서버 관측 결과는 같다. Bukkit jar 가 있으면 "클래스패스에 없어 불러오지 않는다" 경고만 남긴다.
- **증거:** `CompatIsolationTest` 가 별도 JVM 을 `-Xlog:class+load` 로 띄워 서버 기동·접속·명령·종료 동안 올라온 클래스에 `org.bukkit.`·`org.domi.furnace.compat.`·`org.objectweb.asm.` 등이 없음을 센다(대조군: Bukkit 플러그인을 넣으면 `CompatFormat` 이 올라온다). `PluginFormatsLazyTest` 는 어댑터 생성 횟수를 센다.
- **남은 경계(알려 둠):** 서버 `fatJar` 의 클래스패스에는 호환 클래스가 있으므로 Furnace 네이티브 플러그인의 클래스로더(부모 우선)에는 보인다 — 네이티브 플러그인이 같은 이름의 클래스를 자기 jar 에 넣어도 서버 쪽이 이긴다. 정의는 `docs/user-todo/entries/2026-11-08-paper-api-isolation.md` 의 결정 항목.

## 법적 메모

`org.bukkit` 클래스의 공개 이름·서명은 호환을 위해 필요한 만큼만 새로 썼다(Bukkit 소스를 복사하지 않았다). `furnace-compat-paper` 는 GPL-3.0-only (확정, `docs/policy/licensing.md`). 이 문서는 법률 자문이 아니다.
