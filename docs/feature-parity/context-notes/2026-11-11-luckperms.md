## 2026-11-11 LuckPerms 를 Bukkit 호환 계층에서 켜며 배운 것 (luckperms)

- 실제 jar 재현이 빠르다: `LIVE_PROBE_SEED=<폴더>` 를 주면 `liveProbe` 가 그 폴더를 임시 서버 폴더에 복사한다 — LuckPerms 처럼 플러그인이 스스로 내려받는 의존(`plugins/LuckPerms/libs`)을 미리 채워 둘 수 있다(이 환경은 Central 이 429 를 자주 준다).
- 서버 제공 라이브러리 감지는 바깥 jar 만 훑으면 LuckPerms(본체가 `luckperms-bukkit.jarinjar`) 같은 로더 jar 의 의존을 놓친다 — 안쪽 jar 도 훑게 했다(깊이 2).
- `CraftPlayer` 의 상위 생성자(`CraftHumanEntity`)가 `new PermissibleBase(this)` 를 부르면 그 안에서 `isOp()` 가 불린다. 이때 `CraftPlayer.handle` 은 아직 비어 있으므로 상위 클래스(`CraftEntity`)에 든 핸들을 쓴다(`CraftPlayer#player()`).
- `PermissibleBase` 의 Furnace 연결은 **정확히 그 클래스일 때만** 켠다. 하위 클래스(LuckPermsPermissible·DummyPermissibleBase·MonitoredPermissibleBase)는 생성자에서 `super(player)` 를 부르지만 Bukkit 그대로의 동작을 기대한다. 하위 클래스까지 Furnace 에 묻게 하면 "Furnace 판정 → 바꿔 끼운 객체 → Furnace 판정" 재귀가 생긴다.
- LuckPerms 는 명령을 자기 스레드에서 비동기로 돌리고 콘솔·RCON 발신자에게 보낼 말을 콘솔로 낸다 — RCON 응답이 비는 것은 Furnace 문제가 아니다(봇 시험 6번).
- LuckPerms 는 권한 없는 사람이 `/lp info` 를 쳐도 "Running LuckPerms vX." 한 줄을 보낸다(권한 있으면 " by Luck." 과 Platform·Storage 정보). 시험에서 "거부"는 Platform 줄이 없는 것으로 본다.
- 시험 서버(`FurnaceTestServer`)는 이름 캐시가 없어 `op Nora` 가 바닐라처럼 **소문자 이름의 오프라인 UUID**(`OfflinePlayer:nora`)를 OP 로 만든다(바닐라 `ProfileCache.get` 이 소문자로 찾고 없으면 그 소문자로 오프라인 프로필을 만든다). 접속한 플레이어를 OP 로 만들려면 `op @a[name=Nora]`.
- `PlayerLoginEvent` 를 Furnace `PlayerCreateEvent` 에 이었기 때문에 바닐라 접속 거부를 Bukkit 플러그인이 뒤집을 수 없다 — 남은 차이로 문서화(compat-luckperms-2026-11-11.md 1번).
