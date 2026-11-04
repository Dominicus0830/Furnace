# BungeeCord 레거시 IP 포워딩 (2026-11-03)

근거: `paper-spigot-gap-2026-11-03.md` 6장 2번(PR #15). 사용자가 지원을 결정한 항목이 아니라 후보 항목이라 **기본은 꺼짐**이다.

**가정:** 아래 Spigot 동작·문구·패턴은 Spigot BungeeCord 지원 패치를 기억으로 옮긴 것이다. 이 컨테이너에서는 hub.spigotmc.org 가 막혀(프록시 403) 원문과 대조하지 못했다. BungeeCord 가 보내는 주소 칸 형식은 봇 시험에서 그대로 흉내 냈다.

## 동작 (Spigot 1.21.1 `ServerHandshakePacketListenerImpl.beginLogin`·`ServerLoginPacketListenerImpl.initUUID` 와 같게)
- 설정: `furnace.properties` 의 `bungeecord=true|false`(기본 false, 그 밖의 값은 기동 실패). `velocity-secret` 과 함께 켜면 기동 실패.
- 켜져 있으면 핸드셰이크 주소 칸을 `\0` 으로 나눈다. 칸이 3·4개이고 둘째 칸이 `[0-9a-f\.:]{0,45}` 이면
  실제 주소 = 둘째 칸(포트는 프록시 연결의 포트), UUID = 셋째 칸(대시 있어도 됨), 넷째 칸 = 속성 JSON 배열(`name`·`value`·`signature`).
  이름이 `\w{0,16}` 에 맞지 않는 속성은 버린다. 형식이 맞지 않으면 `If you wish to use IP forwarding, please enable it in your BungeeCord config as well!` 로 끊는다.
- 꺼져 있는데 위 형식이 들어오면 `Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?` 로 끊는다(Spigot 과 같음).
- 판정 순서: 프로토콜 버전 검사 → 포워딩 해석. 로그인은 `online-mode=false` 일 때 이름은 클라이언트가 보낸 것, UUID·속성은 프록시 값.
  `online-mode=true` 면 Spigot 처럼 암호화·세션 인증을 그대로 한다(BungeeCord 뒤에서는 실패하므로 기동 때 경고).
- 실제 주소는 Velocity 와 같은 `forwardedAddress` 를 쓴다 → IP 밴·`AsyncPlayerPreLoginEvent`·`PlayerLoginEvent`·접속 로그가 실제 IP 를 본다.
- 주소 칸 상한: 켜져 있으면 Short.MAX_VALUE 자, 핸드셰이크 프레임 98,317B. 꺼져 있으면 바닐라 255자·1024B 그대로.

## Spigot 과 다른 점
- 둘째 칸은 IP 리터럴만 받는다(IPv4 점 네 칸, IPv6). Spigot 은 `new InetSocketAddress(호스트, 포트)` 라 패턴에 맞는 이름(`abc` 등)이면 DNS 를 조회한다 — 이벤트루프에서 DNS 를 타지 않으려고 거절한다. 프록시는 늘 IP 리터럴을 보낸다.
- UUID·JSON 을 읽지 못하면 Spigot 은 예외로 연결이 끊기고(내부 오류 문구), Furnace 는 위의 "If you wish…" 문구로 끊는다.
- 꺼져 있을 때 주소 칸은 바닐라처럼 255자다. Spigot 은 늘 Short.MAX_VALUE 자를 읽어서, 꺼진 서버에 속성까지 실은 긴 포워딩이 오면 Spigot 은 "Unknown data…" 문구, Furnace 는 프레임 상한 초과로 그냥 끊긴다.
- Paper 의 `proxies.bungee-cord.online-mode`(프록시가 정품 인증을 했다고 볼지, 플러그인용 표시)는 없다.

## 시험
- 단위: `BungeeForwardingTest`(형식·속성 이름 거름·IPv6·잘못된 IP/UUID/JSON), `BungeeLoginTest`(소켓 로그인: Login Success 의 UUID·255자 넘는 textures 속성, 실제 IP 로 IP 밴, 직접 접속 거절, 꺼짐 + 포워딩 거절, Velocity 동시 사용 거부), `ConfigLoaderTest`(기본 꺼짐·값 검사·동시 사용 거부).
- 오프라인 서버 + mineflayer 봇(4.39.0, `fakeHost` 로 BungeeCord 와 같은 주소 칸을 보냄, `tools/bot-test` 방식):
  | 상황 | 결과 |
  |---|---|
  | `bungeecord=true`, 포워딩 접속(IP 203.0.113.5, Notch UUID, 616자 textures) | 입장. 봇 UUID·탭 목록 UUID = `069a79f4-…`, 탭 목록 스킨 속성 수신, `/data get entity Notch UUID` = `[I; 110787060, 1156138790, -1514210135, 238594805]`, 로그 `Notch[/203.0.113.5:…] logged in` |
  | `/ban-ip 203.0.113.5` 후 같은 IP 로 재접속 | `multiplayer.disconnect.banned_ip.reason` 로 거절(접속 중이던 Notch 도 킥) |
  | 다른 실제 IP(198.51.100.7) | 입장 |
  | 직접 접속(포워딩 없음) | "If you wish to use IP forwarding…" 로 거절 |
  | `bungeecord=false`, 포워딩 주소 | "Unknown data in login hostname…" 로 거절. 평범한 접속은 오프라인 UUID 로 입장 |
  | `bungeecord=true` + `velocity-secret=abc` | 기동 실패(설정 오류 문구) |
  | 오프라인 모드 기동 로그 | Spigot 과 같은 BungeeCord 방화벽 경고 2줄 |
- 실제 BungeeCord 프록시로는 확인하지 못했다(`docs/user-todo.md` 접속 시험 표).

## 함께 고친 것
- 로그인 30초 타이머가 이미 끊긴 연결(밴·포워딩 거절)에도 `Took too long to log in` 을 한 번 더 찍던 것 — 채널이 살아 있을 때만 끊는다.
