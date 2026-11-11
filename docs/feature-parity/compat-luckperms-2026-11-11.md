# LuckPerms 를 Bukkit 호환 계층에서 켜기 (2026-11-11)

도미니코의 기동 로그에서 LuckPerms 5.5.91 이 `NoSuchMethodError 'org.bukkit.UnsafeValues org.bukkit.Bukkit.getUnsafe()'` 로 켜지지 못했다.
실제 jar(LuckPerms-Bukkit-5.5.91, 안쪽 `luckperms-bukkit.jarinjar`)로 재현하며 막힌 곳을 차례로 고쳤다. 지금은 Paper 와 같은 기동 로그로
켜지고(`Successfully enabled`), Vault 와 함께 두면 `Registered Vault permission & chat hook.` 까지 나온다. ProtocolLib 은 이 문서 끝과
`docs/feature-parity/protocollib-bridge-plan-2026-11-11.md`.

## 무엇을 더했나

| 막힌 곳(순서대로) | 고친 것 |
|---|---|
| `Bukkit.getUnsafe()` 없음(번들 adventure-platform 이 부른다) | `UnsafeValues`(데이터 버전 3955·프로토콜 번호·api-version 검사·주 월드 이름·엔티티 번호) + `CraftMagicNumbers` |
| guava `ImmutableList` 없음 — 서버 제공 라이브러리 감지가 안쪽 jar 를 못 봄 | `ServerLibraries` 가 `.jar`·`.jarinjar` 안쪽 jar 도 2단까지 훑는다 |
| `Server.getOnlineMode()` 없음 | `getOnlineMode`·`getOfflinePlayer(UUID/String)`·`getOperators`, `CraftOfflinePlayer`. 서버 API 에 `Furnace#onlineMode()` |
| `PermissibleBase`·`PermissionAttachment(Info)`·`PermissionRemovedExecutor` 없음, `CraftHumanEntity.perm` 없음 | Bukkit 과 같은 필드 이름·종류·검사 문구의 권한 체계(아래), `Permissible` 의 첨부 메서드 |
| `AsyncPlayerPreLoginEvent`·`PlayerLoginEvent`·`RemoteServerCommandEvent` 없음 → 처리기 클래스 등록 실패 | 세 이벤트와 다리, `RemoteConsoleCommandSender`. 서버 API 에 `RemoteServerCommandEvent`·`RemoteConsoleSender`, `PlayerCreateEvent#disallow` |
| `Player#updateCommands`·`locale()` 없음(권한이 바뀌어도 명령 트리를 안 다시 보냄) | 둘 다 더했다 |

## 권한 체계의 모양

- `CraftHumanEntity` 의 `protected final PermissibleBase perm` 이 정확히 `PermissibleBase` 로 만들어진다. LuckPerms 는 로그인(LOWEST) 때 이 필드를
  리플렉션으로 자기 하위 클래스(`LuckPermsPermissible`)로 바꾸고, 옛 객체의 클래스가 정확히 `PermissibleBase` 가 아니면 "custom permissible" 경고를 낸다.
  `PermissibleBase.attachments`·`permissions`, `PermissionAttachment.permissions` 도 같은 이름·종류다(LuckPerms·DummyPermissibleBase 가 바꿔 끼운다).
- **두 권한 세계 잇기.** Furnace 플러그인·Furnace 명령은 Furnace 의 권한 판정(`PlayerHandle#hasPermission`)을, Bukkit 플러그인은 `CraftPlayer#hasPermission` 을 묻는다.
  - 바꿔 끼우지 않았을 때(정확히 `PermissibleBase`): Bukkit 쪽 판정이 Furnace 판정에 묻는다. Bukkit 첨부는 같은 플러그인의 Furnace 첨부로 거울처럼 옮겨진다
    (다시 계산할 때 맞춘다) — 그래서 Bukkit 플러그인이 준 권한을 Furnace 플러그인·명령 트리도 본다. 유효 권한 목록(`getEffectivePermissions`)은 Bukkit 규칙대로 Bukkit 첨부와 기본값으로 계산한다.
  - 바꿔 끼웠을 때(LuckPerms): 서버 API 의 새 `PermissionOverride` 를 호환 계층이 하나 걸어 두었다가, 그 플레이어의 Furnace 판정도 바꿔 끼운 객체에 맡긴다.
    Bukkit 서버에서 모든 `hasPermission` 이 그 객체로 가는 것과 같다. 하위 클래스는 Furnace 쪽에 묻지 않는다(무한 재귀를 막고, LuckPerms 가 기대하는 Bukkit 그대로의 동작).

## 접속 이벤트 순서

Bukkit 순서 그대로: `AsyncPlayerPreLoginEvent`(가상 스레드, `isAsynchronous()==true`) → `PlayerLoginEvent`(Player 있음) → `PlayerJoinEvent`.
Bukkit `PlayerLoginEvent` 는 Player 객체가 있어야 하는데, Furnace 는 Player 를 구성 단계가 끝난 뒤 만든다. 그래서 그것이 처음 생기는 `PlayerCreateEvent`
(플레이 패킷을 하나도 보내기 전)에 잇는다. 거부하면 월드에 넣지 않고 그 사유로 끊는다 — 접속·퇴장 이벤트도, 플레이어 데이터 저장도 없다.

## Paper 와 다른 점(남은 차이)

(독립 검토에서 고친 것: 재접속한 CraftPlayer 가 권한 구독에서 빠지던 것 — CraftBukkit 처럼 세션까지 보는 `equals`/`hashCode` 와 옛 객체 `retire()`; 허용 상태에서 미리 넣은 사전 로그인 사유가 지워지던 것; 모르는 Plugin 객체의 첨부가 재계산을 깨뜨리던 것; 생성 거부 뒤 도착한 플레이 패킷.)

1. **`PlayerLoginEvent` 의 처음 결과는 늘 ALLOWED.** Furnace 는 바닐라 접속 검사(밴·화이트리스트·인원 초과)를 로그인 단계에서 먼저 끝낸다. 그래서 바닐라가 막은
   접속을 Bukkit 플러그인이 이 이벤트로 허용할 수는 없다(예약 자리·밴 우회 플러그인). Furnace 쪽 `PlayerLoginEvent`(이름·UUID 만, 로그인 단계)로는 된다.
   후속 후보: Bukkit 처리기가 있을 때 바닐라 거부를 생성 시점까지 미뤄 Bukkit 이벤트에 그 결과로 보이기.
2. 로그인 거부의 끊기 패킷이 로그인 단계가 아니라 플레이 단계 `Disconnect` 다(사유 문자는 같고, 클라이언트 화면 제목이 다를 수 있다 — 가정, 실제 클라이언트 확인 필요).
3. `getRealAddress()` 는 `getAddress()` 와 같다(프록시 포워딩 전 소켓 주소를 따로 들지 않는다).
4. `getOfflinePlayer(String)` 은 online-mode 에서도 Mojang 에 묻지 않는다. 서버가 모르는 이름이면 오프라인 UUID 를 준다(Paper 는 막고 기다리며 묻는다).
   `CraftOfflinePlayer` 는 캐시하지 않는다(같음은 UUID 로 — CraftBukkit 과 같다).
5. **바닐라 명령의 권한 노드.** Furnace 의 바닐라 명령은 OP 레벨만 보고 `minecraft.command.*` 노드를 보지 않는다. LuckPerms 로 OP 아닌 플레이어에게
   `minecraft.command.gamemode` 를 주어도 `/gamemode` 는 안 된다(CraftBukkit 은 된다). 후속 후보로 남긴다.
6. LuckPerms 의 콘솔·명령 블록 감시 주입(CraftBukkit `ServerCommandSender.perm`)은 그 클래스가 없어 LuckPerms 가 조용히 건너뛴다(LuckPerms 의 verbose 가 콘솔 판정을 못 본다).
7. `RemoteServerCommandEvent` 는 RCON 연결 스레드에서 불린다(Paper 는 주 스레드). 콘솔의 `ServerCommandEvent` 도 콘솔 입력 스레드에서 부르는 기존 규칙과 같다.
   취소하면 빈 응답을 돌려준다(Paper `DedicatedServer.runCommand` — 가정, Paper 원문 대조 필요).
8. 같은 UUID 로 이미 접속해 있던 세션은 로그인 단계에서 먼저 끊긴다. 그래서 Bukkit `PlayerLoginEvent` 로 새 접속을 거부해도 기존 세션은 이미 끊겼다(CraftBukkit 은 이벤트를 통과한 뒤 끊는다 — 독립 검토 지적, 확신 중).
9. Furnace 명령(`/op`·`/deop`)으로 OP 가 바뀌면 Bukkit 쪽 유효 권한 목록(`getEffectivePermissions`)·기본값 구독은 다음 재계산까지 낡는다. `hasPermission` 은 Furnace 판정에 묻으므로 맞다. LuckPerms 를 주입한 상태에서는 영향이 없다.
10. 꺼진 플러그인의 Bukkit 첨부는 Furnace 쪽에서는 떨어지지만(서버가 플러그인 첨부를 걷는다) Bukkit 쪽 목록에는 남는다.
11. 없는 멤버: Paper Adventure 쪽(`kickMessage(Component)` 등)·`PlayerProfile`, `Player#spigot()`. 부르면 `NoSuchMethodError`.

## 확인

- 실제 jar: `LIVE_PROBE_SEED=<LuckPerms/libs 를 미리 채운 폴더> ./gradlew -PskipOracle=true :furnace-compat-paper:liveProbe -Pjar=LuckPerms -PextraJars=<jar 폴더> -PlibraryCache=<캐시>`
  → `Successfully enabled`, 오류 없음.
- 봇 접속 시험 `tools/bot-test/round-lp/lp.js` 9/9: 접속(주입 경고 없음), 권한 없을 때 `/lp info` 거부, 콘솔 `lp user Alice permission set luckperms.info true` 뒤 허용,
  RCON 으로 LuckPerms 명령(결과는 콘솔 로그 — LuckPerms 가 콘솔·RCON 발신자 메시지를 콘솔로 낸다, Paper 와 같은 동작), RCON 바닐라 명령 응답, 권한을 다시 뺀 뒤 거부, 퇴장 때 주입 해제 예외 없음.
- 시험: `BukkitPermissionsTest`(첨부·자식 권한·Furnace 쪽 반영·바꿔 끼운 객체의 Furnace 판정·로그인 거부·RCON 이벤트·getUnsafe·오프라인 플레이어),
  `LoginPhaseEventsTest.createDisallowDisconnectsBeforeJoinWithoutQuitOrSave`, `PluginSystemTest.permissionOverrideDecidesFirstAndNullFallsBack`,
  `RconCoreTest.remoteServerCommandEventCanRewriteOrCancel`, `ServerLibrariesBootstrapTest.markerInsideNestedJarIsFound`.

## ProtocolLib 5.4.0 의 지금 상태

`onLoad` 의 `PacketRegistry` 가 `net.minecraft.network.ConnectionProtocol`(Spigot 이름 `EnumProtocol`)을 찾지 못해 실패하고, 그 뒤 `onEnable` 이
"does not support plugin reloaders" 를 찍고 스스로 꺼진다. 이 배너는 `onLoad` 실패의 결과로 보인다(Paper 도 `onLoad` 가 던진 플러그인을 켜려고 하는지는 가정 — 원문 대조 필요).
도미니코 결정(단계별 바닐라 네트워크 다리)에 따른 계획: `docs/feature-parity/protocollib-bridge-plan-2026-11-11.md`.
