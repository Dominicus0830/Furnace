# H4 묶음 E: Bukkit 플러그인 메시징 — 2026-11-08

helper·CommandAPI 가 라이브러리 장벽(묶음 B) 다음에 멈추던 `org.bukkit.plugin.messaging.PluginMessageListener` 를 채웠다.
이 묶음은 **플러그인 메시징만** 다룬다(측정 문서의 E 묶음 중 BossBar·Scoreboard 는 다음 묶음).

## 무엇을 넣었나

| Bukkit | 내용 |
|---|---|
| `Messenger`, `StandardMessenger` | 보내는·받는 채널 등록표, `dispatchIncomingMessage`, `validateAndCorrectChannel`·`validatePluginMessage`, 상수 `MAX_MESSAGE_SIZE`(1048576)·`MAX_CHANNEL_SIZE`(64) |
| `PluginMessageListener`, `PluginMessageListenerRegistration`, `PluginMessageRecipient`, `PluginChannelDirection` | |
| 예외 4종 | `ChannelNameTooLongException`·`ChannelNotRegisteredException`·`MessageTooLargeException`·`ReservedChannelException` |
| `Server#getMessenger/sendPluginMessage/getListeningPluginChannels`, `Bukkit.getMessenger()` | 서버는 모든 접속자에게 보낸다 |
| `Player#sendPluginMessage/getListeningPluginChannels` | `Player` 가 `PluginMessageRecipient` 를 잇는다 |
| `PlayerChannelEvent`, `PlayerRegisterChannelEvent`, `PlayerUnregisterChannelEvent` | CommandAPI 가 `PlayerRegisterChannelEvent` 리스너를 단다 |

Furnace API 에는 `PlayerChannelEvent`(`REGISTER`/`UNREGISTER`, 채널 하나에 하나) 하나를 더했다. 나머지는 이미 있던 `Furnace#registerPluginChannel`·`Player#sendPluginMessage`·`PluginMessageEvent` 위에 얹었다 — 새 패킷 경로는 없다.

## 바닐라·Paper 와 같게 맞춘 동작

- **채널 이름**: `"BungeeCord"` 는 등록·보내기에서 `"bungeecord:main"` 이 되고, 받은 `bungeecord:main` 메시지는 리스너에 `"BungeeCord"` 로 간다(`validateAndCorrectChannel` 이 두 방향으로 바꾸고, 조회가 한 번 더 바꿔 등록 키로 찾는 Bukkit 구조 그대로). `getListeningPluginChannels()` 도 `"BungeeCord"` 로 보인다. `:` 없음·대문자 → `IllegalArgumentException`, 64자 초과 → `ChannelNameTooLongException`. `"minecraft"` 를 담은 이름은 예약(`minecraft:brand` 만 예외).
- **보내기**(`CraftPlayer.sendPluginMessage`): 켜진 플러그인·보내는 채널로 등록·1 MiB 이하를 검사하고, **클라이언트가 그 채널을 `minecraft:register` 로 알렸을 때만** 보낸다. 프록시(BungeeCord·Velocity)는 `bungeecord:main` 을 알리므로 `"BungeeCord"` 메시지가 나간다. 바닐라 클라이언트에 직접 보낸 것은 조용히 버려진다.
- **받기**: 그 채널을 받는다고 등록한 플러그인의 리스너만 부른다. **전역 스레드("주 스레드")** 에서 부르고 `Bukkit.isPrimaryThread()` 가 true 다(Paper 는 custom_payload 를 `ensureRunningOnSameThread` 로 주 스레드에서 처리). 그 사이 나간 플레이어의 메시지는 버린다. 리스너 예외는 그 플러그인 로거에 경고로 남기고 다음 리스너로 간다. 같은 (플러그인·채널·리스너) 등록을 두 번 하면 `IllegalArgumentException`.
- **클라이언트에 알리기**: 받는 채널을 등록하면 Furnace 채널 등록으로 이어져 접속 직후 `minecraft:register` 에 실린다(CraftBukkit `sendSupportedChannels`). 플러그인의 마지막 리스너가 빠지면 Furnace 쪽 등록도 푼다.
- **플러그인이 꺼지면** 받는·보내는 등록을 모두 푼다(Bukkit `disablePlugin`).
- **채널 등록 이벤트**: 클라이언트가 실제로 새로 알린 채널마다 `PlayerRegisterChannelEvent`, 실제로 거둔 채널마다 `PlayerUnregisterChannelEvent`(`CraftPlayer.addChannel/removeChannel`). 처리기 목록은 `PlayerChannelEvent` 가 들고 있어, 그것을 받는 처리기는 둘 다 받고 하위 이벤트를 받는 처리기는 자기 것만 받는다. 구성 단계에서 알린 채널(모드 클라이언트·프록시)은 play 진입 때 `PlayerJoinEvent` 직전에 알린 순서대로 온다.

## Bukkit 과 다른 점 (관측 가능한 것)

1. **이벤트 스레드**: `PlayerRegister/UnregisterChannelEvent` 는 그 플레이어의 연결 스레드에서 불린다(Paper 는 주 스레드). 리스너가 월드를 만지면 소유 스레드 규칙에 걸린다 — 실제 사용(CommandAPI)은 플러그인 메시지를 보내기만 한다.
2. **실행 중 등록 알림**: 서버가 이미 접속한 사람에게도 새 받는 채널을 `minecraft:register` 로 바로 알린다(기존 Furnace 동작). CraftBukkit 은 접속 때 한 번만 보내는 것으로 기억한다(확인 못 함). 클라이언트가 더 일찍 알게 될 뿐 메시지 흐름은 같다.
3. **이상한 클라이언트 입력**: 64자 넘는 채널 메시지, 128개 넘는 채널 등록은 버리기만 한다. CraftBukkit 은 예외로 연결을 끊는다("Invalid custom payload!"/"Invalid payload REGISTER!" — 기억 기준, Paper 원문 대조 못 함). 바닐라·모드 클라이언트의 정상 흐름에서는 나오지 않는다.
4. **리스너 호출 묶음**: 메시지 하나를 플러그인마다 따로 전역 작업으로 넘긴다(플러그인 간 순서는 등록 순). Bukkit 은 한 번에 모든 리스너를 돈다 — 같은 틱 안이라 관측 차이는 없다.
5. **돌려주는 집합**: Guava `ImmutableSet` 대신 바꿀 수 없는 JDK 집합(호환 모듈이 Guava 를 볼 수 없다). 순서는 등록 순.

## 측정 (2026-11-08, `liveProbe -PlibraryCache`)

| jar | 없는 Bukkit 심볼 (전→후) | 다음 장벽 |
|---|---|---|
| helper 5.6.14 | 184 → 176 | `org.bukkit.plugin.SimplePluginManager` (켜기 단계) |
| CommandAPI 10.1.2 | 105 → 94 | `net.minecraft.world.level.IWorldReader`(Spigot 이름 NMS — H7d) |
| commandapi-bukkit-core 12.1.0 | 49 → 38 | |

대조표·기준표는 `compatReport` 로 다시 썼다(`compat-h4-coverage-2026-11-07.md`, `realworld-baseline.properties`).

## 시험

- `PluginMessagingTest`(헤드리스 서버 + 가짜 클라이언트): 이름 바꿈·검사, 보내는 채널 등록·크기·알린 클라이언트에만 보내기, 받기 스레드·예외 격리·등록 알림/해제, BungeeCord 양방향, 플러그인 끄기, 채널 등록 이벤트 순서(접속 알림 앞)·하위 이벤트 거르기.
- `EventBridgeTest`: 다리 짝 표에 `PlayerChannelEvent` 추가.
- 시험 도구: `FurnaceTestServer#join(name, configChannels...)`(구성 단계 `minecraft:register`), `TestPlayer#sendPluginMessage/registerChannels/unregisterChannels/pluginMessages`.
