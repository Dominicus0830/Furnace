# 접속 방어 (작업 묶음 N2: 행 102·103·104) — 2026-11-06

Paper/Spigot 불만 표 102(접속 단계별 패킷 속도·크기·시간 초과), 103(아이템·책·명령 길이 검증), 104(PROXY 프로토콜 v2) 구현 기록.
**기본값은 전부 지금까지의 동작이고, 관측 결과는 바닐라와 같다.** 바닐라보다 조이거나 새 입구를 여는 값은 `furnace.properties` 에서 직접 켠다.

## 1. 설정 키 (`furnace.properties`)

| 키 | 기본값 | 뜻 |
|---|---|---|
| `connection.<단계>.packets-per-second` | 200 | 그 단계에서 초당 회복되는 패킷 수(토큰 버킷). `0` = 그 단계는 제한 없음. 단계: `handshake` `status` `login` `configuration` `play` |
| `connection.<단계>.packets-burst` | 400 | 순간 버스트 허용 수(1 이상). 다 쓰면 `Too many packets` 로 끊고 경고 로그 |
| `connection.<단계>.max-frame-bytes` | handshake 1024, status 64, login 1 MiB+7, configuration 131,077, play 2 MiB | 그 단계에서 받는 프레임 최대 길이. 길이만 보고 본문을 버퍼링하기 전에 끊는다. 2 MiB(프로토콜 최대)를 넘길 수 없다(넘기면 조정 경고) |
| `connection.<단계>.timeout-seconds` | login 30, 나머지 0(꺼짐) | 그 단계에 머물 수 있는 총 시간. 초과하면 끊는다(login 은 바닐라 `slow_login`, 나머지는 `disconnect.timeout`). play 에는 없음 |
| `connection.read-timeout-seconds` | 30 | 아무것도 안 보내는 소켓을 정리하는 읽기 타임아웃(바닐라 `ReadTimeoutHandler(30)`) |
| `limits.chat-length` / `limits.command-length` | 256 | 채팅·명령 문자열 길이. 바닐라 한도(256)보다 **조이기만** 가능(1~256). 넘으면 바닐라 디코드 실패와 같이 연결 종료 |
| `limits.item.display-name` / `item-name` / `lore-line` / `book-page` / `book-author` | 0(꺼짐 = 바닐라) | 클라이언트가 보낸 아이템(크리에이티브 칸·컨테이너 클릭)의 이름·설명 줄·책 쪽·저자 글자 수 상한(Paper `item-validation` 대응). 상자·묶음 안에 든 아이템도 검사. 넘으면 디코드 실패와 같이 연결 종료. `book-page` 는 책 편집 패킷의 쪽에도 적용 |
| `proxy-protocol` | false | `true` 면 모든 접속이 PROXY 프로토콜(v1·v2) 헤더를 먼저 보내야 하고, 헤더의 주소가 접속 주소가 된다(밴·`prevent-proxy-connections`·로그·플러그인 `getAddress`) |
| `proxy-protocol.trusted` | 빈 값(모두 허용) | 헤더를 보내도 되는 앞단 주소(쉼표로 구분한 IP/CIDR, 숫자 주소만). 목록 밖에서 온 헤더는 거부 |

`server.properties` 의 바닐라 `rate-limit`(기본 0 = 끔)도 이제 실제로 동작한다: 바닐라 `RateKickingConnection` 과 같은 평균(`avg = lerp(0.75, 이번 1초의 수, avg)`, 1초마다 검사)이 상한을 넘으면 `disconnect.exceeded_packet_rate` 로 끊는다. 모든 단계에 적용(바닐라와 같음). 기본 버킷(위 `packets-per-second`/`burst`)과는 따로 동작한다.

## 2. 결정과 근거

- **기본 속도 제한은 그대로 둔다.** 바닐라는 `rate-limit=0` 이라 제한이 없지만, Furnace 에는 이미 모든 단계에 걸린 버킷(버스트 400·초당 200)이 있었다. 이를 되돌리면 방어가 줄어들므로 기본값으로 유지하고, 단계마다 바꿀 수 있게만 했다. 정상 클라이언트는 이 한도에 닿지 않는다.
- **로그인·구성 단계 프레임 기본 상한을 줄였다.** 이전에는 핸드셰이크·상태 외에는 2 MiB 까지 받았다. 바닐라가 받는 가장 큰 패킷(로그인 질의 응답 1 MiB, 구성 custom_payload 채널 32767자×3B + 페이로드 32767)까지만 받도록 줄였다. 바닐라 정상 클라이언트는 이보다 큰 패킷을 보내지 않으므로 결과가 같다.
- **단계 시간 제한 기본값**: 바닐라에 로그인 30초(`slow_login`)와 읽기 타임아웃 30초만 있으므로 같게 두었다. 구성 단계에서 30초 안에 한 번씩 패킷을 흘려 무한정 붙잡는 접속(Folia #454 류)을 막으려면 `connection.configuration.timeout-seconds` 를 켠다. 큰 리소스팩을 받는 클라이언트는 구성 단계가 30초 넘게 걸릴 수 있으니 그 서버는 넉넉히 잡을 것.
- **구성 단계 패킷을 netty 스레드에서 막지 않기(#454·#520 점검)**: 구성 단계 처리(`ConnectionHandler.handleConfiguration`)를 훑었다. 이벤트 루프에서 하는 일은 메모리 안의 패킷 조립·전송뿐이고(레지스트리·태그 전송), 디스크·잠금 대기는 없다. 플레이어 데이터 읽기는 이미 전용 실행기로 빠져 있다(D-14). `Known Packs` 응답은 연결당 1회, `Finish Configuration` 확인은 보내기 전이면 거부, 확인 뒤에는 플레이 단계로 넘어가 같은 패킷이 재처리되지 않아 `FinishConfiguration` 스팸(#520)은 증폭되지 않는다. 남은 위험(구성 단계 **플러그인 훅**이 netty 스레드를 막는 문제)은 작업 묶음 N1 의 전용 실행기가 맡는다. 이 묶음은 속도·시간 제한으로 스팸·정체 접속을 끊는 데까지다.
- **명령 길이(#4099 "긴 명령 킥")**: 256자 초과 명령을 보내면 바닐라 *클라이언트*가 인코드 단계에서 스스로 예외를 내며 끊는다(서버가 고칠 수 없음). 서버는 바닐라와 같이 256 을 상한으로 두고, 더 조이는 값만 허용한다. 서명된 채팅·명령을 "무시"로 넘기면 서명 체인이 어긋나 어차피 킥되므로 무시 옵션은 두지 않았다.
- **잘못된 텍스트 컴포넌트**: 아이템 디코더(`ItemStackCodecs`)가 이미 바닐라처럼 JSON·NBT 텍스트를 검증해 `CodecException` 으로 거부하고, 호출 지점이 디코드 실패로 연결을 끊는다(시험으로 확인). 잘못된 UUID 의 `show_entity` hover(Paper #7013)는 서버가 그 UUID 를 해석하지 않아 크래시 경로가 없다.
- **PROXY 프로토콜**: netty `HAProxyMessageDecoder` 를 파이프라인 맨 앞에 두고, 첫 바이트가 헤더(`P`/`0x0D`)가 아니면 netty 가 줄바꿈을 기다리는 동안 붙잡히지 않도록 바로 거부한다. `LOCAL`(앞단 상태 점검)과 UNKNOWN 은 실제 소켓 주소를 쓴다. v1 텍스트 헤더도 같은 코드로 받는다. 신뢰 목록이 비어 있으면 누구나 헤더로 주소를 속일 수 있어 기동 때 경고한다.

## 3. 시험

- `ConnectionLimitsTest`(설정 파싱·기본값·CIDR·바닐라 rate-limit 평균·아이템 글자 수 검사·깨진 텍스트 컴포넌트)
- `ConnectionDefenseTest`(EmbeddedChannel: 단계별 버킷·프레임 상한·명령 길이·PROXY v1/v2/LOCAL/IPv6·헤더 없음·신뢰 밖 거부·주소 우선순위)
- `ConnectionDefenseSocketTest`(실제 소켓: 핸드셰이크·로그인·구성 시간 초과, 기본값에서 구성 단계가 열려 있음, 바닐라 rate-limit 킥, 단계별 초과 프레임 닫힘, 쓰레기 바이트 뒤에도 서버 정상, PROXY 헤더 뒤 상태 조회, 헤더 없는 직접 접속 즉시 거부, 꺼져 있을 때 헤더는 쓰레기)
- `ConnectionInputTest.itemValidation_longCustomName_kicksOnlyWhenEnabled`(실제 크리에이티브 슬롯 패킷: 기본은 받고, 켜면 끊김)
- 실제 HAProxy/nginx 앞단은 이 환경에 없어 못 돌렸다 → `docs/user-todo.md` 1장.
