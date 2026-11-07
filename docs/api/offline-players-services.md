# 오프라인 플레이어·접근 목록·서비스 레지스트리 (B6)

불만 표 85번(오프라인 플레이어 데이터·영속 데이터 편집), 86번(OP 레벨·밴·화이트리스트 API), 87번(서비스 레지스트리)을 받는 API 다.
바닐라 동작은 바뀌지 않는다. 모두 `Furnace` 파사드에서 얻는다.

## 1. 오프라인 플레이어 — `Furnace.offlinePlayer(UUID)`

```java
OfflinePlayer op = server().offlinePlayer(uuid);          // 모르는 UUID 도 받는다(name() 은 null)
op.editPersistentData(c -> c.set("shop:coins", PersistentDataType.INTEGER, 7))
  .thenAccept(done -> { /* true: 고쳤다, false: 데이터 파일이 없다 */ });
```

- 이름 찾기: `offlinePlayerCached("steve")` — 접속 중인 사람과 `usercache.json` 에 있는 이름만 보고 없으면 null. 인증 서버에 묻지 않으니 어느 스레드에서도 막히지 않는다.
- 데이터 파일이 있는 모든 사람: `knownPlayers()`(디렉터리를 읽어 비동기).
- 읽기: `hasPlayedBefore()`, `readNbt()`(전체 플레이어 NBT 사본), `persistentData()`(BukkitValues 사본). 데이터가 없으면 null 로 끝난다.
- **쓰기는 영속 데이터(BukkitValues)만** 열어 둔다. 위치·인벤토리·체력을 파일에서 고치는 길은 일부러 막았다(바닐라 `/data` 가 플레이어 NBT 를 못 쓰는 것과 같고, 접속자 메모리와 파일이 어긋난다).
- 접속 중이면 `editPersistentData` 는 접속자의 소유 스레드로 넘어간다(접속자 메모리가 정본이라 파일을 고쳐도 퇴장 저장이 덮어쓰기 때문). 아니면 `playerdata/<uuid>.dat` 를 읽어 고쳐 쓰고 이전 파일은 `.dat_old` 로 남긴다.
  파일 잠금 안에서 한 번 더 접속 여부를 보고, 그 사이 접속했으면 접속자 쪽으로 다시 보낸다.
- 파일이 없는 사람은 만들지 않는다(`false`). 편집 함수가 예외를 던지면 아무것도 쓰지 않고 그 예외로 끝난다.
- 한계: 접속 처리 중 "데이터를 읽었지만 아직 접속자 목록에 오르기 전"의 아주 짧은 틈에 들어온 편집은 그 접속자의 퇴장 저장에 덮일 수 있다.

## 2. OP 레벨·밴·화이트리스트

`OfflinePlayer` 한 사람 단위:

| 메서드 | 바닐라에서 같은 일 |
|---|---|
| `opLevel()` / `isOp()` / `setOpLevel(0~4)` | ops.json 의 level. 0 은 `/deop`. 접속 중이면 권한 레벨 패킷과 명령 트리를 다시 보내고, deop 후 `enforce-whitelist` 규칙대로 내보냄 |
| `isBanned()` / `banEntry()` / `ban(reason, expires, source)` / `unban()` | `/ban`·`/pardon`. 접속 중이면 "banned" 로 내보냄 |
| `isWhitelisted()` / `setWhitelisted(boolean)` | `/whitelist add·remove`. 뺄 때 `enforce-whitelist` 규칙 적용 |

이름을 모르는 UUID 에 쓰려 하면 `IllegalStateException`(목록 파일에 이름이 같이 들어가기 때문). 읽기는 된다.

서버 전체: `Furnace.access()` → `AccessLists` — 화이트리스트 켜기/끄기·다시 읽기, 목록(`operators()`·`whitelisted()`·`bannedPlayers()`·`bannedIps()`), `banIp`·`unbanIp`(IP 문자열만, 접속 중인 그 IP 를 내보냄).
목록 파일(`ops.json`·`banned-players.json`·`banned-ips.json`·`whitelist.json`)이 정본이고 바뀌면 바닐라 명령처럼 바로 쓴다. 만료된 밴은 읽을 때 걸러진다.

## 3. 서비스 레지스트리 — `Furnace.services()`

Bukkit `ServicesManager`·Vault 식으로 플러그인끼리 이름 대신 **인터페이스**로 만난다.

```java
// 제공자
server().services().register(EconomyService.class, myEconomy, this, ServicePriority.NORMAL);
// 소비자
EconomyService eco = server().services().load(EconomyService.class);   // 없으면 null
```

- 우선순위가 높은 제공자가 `load` 에 걸리고, 같으면 먼저 올린 쪽이다. `registrations(Class)` 는 그 순서의 전체 목록.
- 제공 플러그인이 꺼지면(핫 리로드 포함) 그 플러그인이 올린 서비스는 자동으로 풀린다. 꺼진 플러그인이 올리면 `IllegalStateException`.
- 올림·풀림은 `ServiceRegisterEvent`·`ServiceUnregisterEvent`(비동기 이벤트, 부른 스레드에서 발화)로 알린다. 소프트 의존 플러그인이 경제 플러그인이 나중에 켜지는 걸 기다릴 때 쓴다.
- 서비스 인터페이스는 제공자와 소비자가 같이 보는 클래스로더(보통 API 모듈)에 있어야 한다. 아래 표준 인터페이스는 `furnace-api` 에 있다.

### 표준 인터페이스 (제공자는 플러그인, 코어는 구현을 싣지 않는다)

| 인터페이스 | 용도 |
|---|---|
| `PermissionService` | UUID 로 권한 노드·그룹·접두사를 묻는다(오프라인 포함). 쓰기(`set`·`addToGroup`)는 기본 미지원(false) |
| `EconomyService` | 계정·잔액·인출·입금·이체. 금액은 `BigDecimal`, 실패는 예외가 아니라 `Result`(`transfer` 의 기본 구현은 입금 실패 시 인출을 되돌림) |
| `PlaceholderService` + `PlaceholderExpansion` | `%식별자_인자%` 풀이. 확장은 서비스에 올리고 소비자는 `resolve`·`resolveOne` |

세 인터페이스만 표준으로 정했다. 더 필요하면(채팅 접두사 전용·로깅 등) 플러그인이 자기 인터페이스를 같은 방식으로 올리면 된다.
