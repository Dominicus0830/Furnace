## 2026-11-14 시작 로그 분석: FAWE·ProtocolLib·잡음 (startup-log)

도미니코 시작 로그(빌드 a242b1af, Windows, Java 26, LuckPerms 5.5.91·Vault 1.7.3·ProtocolLib 5.4.0·FAWE 2.16.2)를 분류하고 고칠 수 있는 것을 고쳤다. 같은 jar 4개로 이 저장소에서 다시 띄워 확인했다.

### 분류
| 로그 | 분류 | 처리 |
|---|---|---|
| FAWE `BlockTypesCache.<clinit>` AIOOBE (Index 1 out of bounds for length 1), LegacyMapper·BlockTypes 실패, "Failed to load biomes via adapter", "doesn't support Tags" | 플러그인 기대(NMS 어댑터 `fawe.v1_21_R1` 이 안 올라옴) | 블록 표·레지스트리 문제가 아니다. 어댑터 후보를 다 실패하면 `BukkitBlockRegistry` 가 빈 목록을 돌려 값이 1개뿐이 되고, `ReservedIDs` 루프(237행)가 터진다. v1_21_R1 어댑터는 `<clinit>` 의 `DataComponentPatch.CODEC` 에서 먼저 막히고 뒤로 `ChunkHolder.wasAccessibleSinceLastSave` 리플렉션·`PaperweightAdapter` 생성자 등이 줄 서 있다(FAWE 보고서 664 심볼 중 330 없음). N 계열 NMS 어댑터 작업으로 넘긴다. |
| FAWE `Could not pass event WorldInitEvent` (`NoSuchElementException`, `WorldEditPlugin.setupWorldData:290`) | 플러그인 기대 | 이번 수정으로 `WorldInitEvent` 가 오면서 새로 보인다. 어댑터가 없을 때 Paper 에서도 같은 길로 같은 예외가 난다(어댑터가 생기면 사라진다). 숨기지 않는다. |
| "Server reload detected"(FAWE·ProtocolLib) | Furnace 결함 | STARTUP 플러그인이 켜질 때 이미 월드가 보여서 생긴 오탐. Paper 순서(STARTUP 켜기 → 월드 하나씩 `WorldInitEvent` → `WorldLoadEvent` → POSTWORLD 켜기)로 맞췄다. |
| ProtocolLib `NoClassDefFoundError: org/bukkit/conversations/ConversationCanceller` (FILTER 명령) | Furnace 결함 | `org.bukkit.conversations` 전체를 새로 두고 플레이어 채팅·콘솔 입력을 대화로 돌리는 길을 연결. |
| ProtocolLib "unable to check for updates" SEVERE | Furnace 결함 | `org.spigotmc.SpigotConfig` 가 없어 Spigot 판별이 실패. 클래스를 둠. |
| ProtocolLib `EnumProtocol` 등 NMS 클래스 없음 경고 9줄, 파이프라인(`ServerConnectionListener`) 안내 | 잡음(리플렉션 기능 감지) | Paper 1.21.1 에도 없는 이름을 찾는 것. 켜기 동안 모아 INFO 한 줄로. 파이프라인 주입은 이미 Furnace 쪽 다리가 맡는다. |
| FAWE 다른 버전 어댑터(`v1_20_R3`… `v26_x`) 정의 경고 약 65줄 | 잡음 | 버전 고르기. DEBUG 로 내림(연도 버전 `v26_1` 이름도 다른 버전으로 인식). |
| `PlayerCommandSendEvent` 알리지 않음, `AsyncTabCompleteEvent` 없음 | Furnace 미구현 | 다음 후보(user-todo 엔트리). |
| 첫 실행 `server.properties` ERROR | 잡음이지만 바닐라와 같음 | 바닐라 1.21.1 `Settings.loadFromFile` 도 `Logger.error("Failed to load properties from file: {}")` 로 남긴다. 그대로 둔다. |

### 함정
- Paper 의 `getWorlds()` 는 STARTUP 플러그인 `onEnable` 에서 빈 목록이다. 이걸 보고 FAWE·ProtocolLib 은 "월드가 이미 있으면 리로드" 로 판단한다.
- FAWE 의 옛 리로드 경로는 `setupWorldData` 예외를 try/catch 로 삼켰지만 `WorldInitListener` 경로는 삼키지 않는다 — 어댑터가 없으면 Paper 에서도 SEVERE 한 줄.
- Vault `plugin.yml` 의 `load: startup`(소문자)도 STARTUP 이다.
