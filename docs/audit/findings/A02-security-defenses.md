# A02 보안·접속 방어·원격 관리 — 2차 전체 점검 조사 보고서 (2026-11-08)

- **기준 커밋**: `claude/quirky-wright-lra834` `8f8b188f` (PR #170 병합 시점, 계획서 기준 `6e8fec9a` 이후 문서만 바뀜)
- **조사관**: A02 조사 스레드 (Opus high). 코드는 고치지 않았다.
- **재현 시험**: `docs/audit/findings/A02-repro/*.java.txt` — 각 파일 첫 줄에 넣을 패키지가 있다. `.txt` 를 떼어 해당 패키지의 `src/test/java` 에 두고
  `gradle :furnace-server:test --tests '*A02*'` 로 돌린다. 기준 커밋에서 **실패하는 것이 재현**이다(통과하면 고쳐진 것).

## 조사한 범위
- `net/auth`: 세션 인증(`SessionService`), 암호화 응답(`EncryptionResponse`, 검증 토큰), BungeeCord 레거시(`BungeeForwarding`), Velocity 모던(`VelocityForwarding`, HMAC·메시지 번호·로그인 단계).
- `ConnectionLimits`·`TokenBucket`·`ReceiveRateWatch`·`ItemValidation`·`ProxyProtocolHandler`(+Sniffer), `FrameDecoder`·`CompressionDecoder` 의 길이·압축 해제 상한, 단계별 시간 제한.
- 클라이언트가 보내는 가변 길이 입력: 네트워크 NBT(`NbtVals.readNetwork`), 아이템 컴포넌트 패치, 문자열, 책·표지판·모루·명령·채팅, `custom_payload`·REGISTER 채널 수, 채팅 세션 키.
- `remote`: RCON(`RconServer`·`RconProtocol`·`RconLimits`), Query(`QueryServer`·`QueryHandler`).
- 권한 경계: 명령 트리 전송 필터(`CommandTreePacket`), 선택자 파싱 권한(`EntityArgument.parse(reader, source)`), 태그 질의(권한 2), 게임 마스터 블록(크리에이티브 + 권한 2), 명령 블록 `enable-command-block`.
- 파일 경로 입력: 구조물 템플릿 저장 경로(`TemplateManager.pathOf`), 라이브러리 좌표·체크섬(`PluginDescription.Library`, `LibraryResolver`), 백업(`WorldBackup`, 쓰기만), 월드 이름(`WorldCreator`, `WorldManager.storedCreator`).

## 조사하지 못한 범위 (이유)
- 실제 HAProxy·Velocity·BungeeCord 앞단과의 접속 — 컨테이너에 앞단이 없다(이미 `docs/user-todo.md` 1장 항목). 악성 클라이언트는 EmbeddedChannel 로 대신했다.
- 플러그인 권한 노드(Bukkit `minecraft.command.*`)와 OP 레벨의 대응 — A15 경계라 보지 않았다.
- 디스크에서 읽는 NBT(`NbtIo.read*`, `NbtIo.In`)에는 깊이·크기 한도가 전혀 없다(음수·거대 배열 길이도 그대로 할당). 손상·악성 지역 파일 입력이므로 A05 몫으로 넘긴다(아래 A02-01 제안에 같이 적음).
- 명령 파싱·자동완성의 CPU 비용 측정 — A17 몫(A02-07 참고).

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 1 | A02-01 |
| 높음 | 0 | |
| 중간 | 1 | A02-02 |
| 낮음 | 2 | A02-03, A02-04 |
| 참고 | 3 | A02-05, A02-06, A02-07 |

도미니코 결정이 필요한 항목은 없다(모두 바닐라 결과를 바꾸지 않고 고칠 수 있다) — user-todo 항목을 만들지 않았다.

---

## 항목

### A02-01 — 네트워크 NBT 한도(2 MiB)가 길이 음수 리스트 하나로 풀려, 32바이트 패킷으로 1 GiB 를 할당시킨다

| 칸 | 내용 |
|---|---|
| 심각도 | **치명** (원격 서비스 거부, 힙 고갈로 아무 스레드에서나 OutOfMemoryError) |
| 종류 | 보안 · 바닐라 차이 |
| 근거 | `item/NbtVals.java:120-128` TAG_LIST: `n = in.readInt()` 가 음수여도 `account(q, 4L * n)` 이 **남은 한도를 늘린다**(최대 +8 GiB). `NbtList` 는 용량 검사 없이 빈 리스트를 돌려준다. 같은 compound 안의 다음 태그 `TAG_INT_ARRAY`(`:143-149`)·`TAG_LONG_ARRAY`(`:151-157`)·`TAG_BYTE_ARRAY`(`:111-117`)는 늘어난 한도를 통과해 `new long[n]` 을 **본문을 읽기 전에** 할당한다. `account()`(`:97`)는 음수 바이트를 거부하지 않는다.<br>도달 경로: 생존 플레이어의 컨테이너 클릭 `ConnectionHandler.java:2045-2047`(0x0E, 바뀐 칸·들고 있는 아이템), 창작 칸 `:1961`(0x32) → `readSlot` `:4678` → `ItemStackCodecs.readStack` `:115` → `ComponentPatch.read` `:145` → `custom_data`(`PCodec.COMPOUND`) → `ComponentType.java:54` → `NbtVals.readNetwork`. 플러그인 패킷 리스너가 붙으면 `plugin/packet/Protocol767` 의 서버행 아이템 디코드도 같은 길을 탄다.<br>바닐라 1.21.1 `ListTag$1.loadList`(난독화 `uh$1`): `accountBytes(4, i)` 바로 뒤 `Lists.newArrayListWithCapacity(i)` 가 음수에서 `IllegalArgumentException` → 디코드 실패·연결 종료. 즉 바닐라는 같은 바이트에서 할당 없이 끊는다(`javap -c 'uh$1'` 로 확인). |
| 재현 | `A02NbtBombFlowTest.survivalContainerClick_allocatesGigabyteOnNetworkThread`: 플레이 단계 연결에 0x0E **32바이트** 한 개 → 네트워크 스레드 할당 **1024 MiB**, 처리 **615 ms**(이벤트루프 정지), 그 뒤 연결 종료. 실패.<br>`A02NbtQuotaReproTest`: `control_withoutNegativeList_quotaRejectsBeforeAllocation` 통과(한도 자체는 동작) / `negativeListLength_mustNotDisableQuota` 실패(19바이트 NBT 로 `int[1_000_000]` 할당 뒤 `IndexOutOfBoundsException`) / `negativeListLength_allocatesGigabyteFromThirtyBytes` 실패(`long[134_217_728]` = 1 GiB, 140~613 ms) / `reachableFromClientItemSlot` 실패(아이템 칸 디코드에서도 같음) / `concurrentClaimsExhaustHeap` 실패: 시험 힙 3 GiB 에서 동시 4개 → `[OutOfMemoryError, OutOfMemoryError, IndexOutOfBounds, IndexOutOfBounds]`.<br>영향: 패킷 속도 한도(초당 200) 안에서도 접속 몇 개로 힙을 바닥낼 수 있고, OOM 은 공격자 연결이 아니라 region 틱·청크 저장 스레드에서 터질 수 있다(서버 정지·저장 실패). `online-mode=false`·BungeeCord 서버에서는 누구나, 정품 서버에서도 계정 하나면 된다. |
| 제안 | `NbtVals.readPayload`: 리스트·배열 길이 `n < 0` 이면 `CodecException`(바닐라처럼 할당 전에 실패), `account()` 가 음수 바이트를 거부. 같은 구조인 디스크 리더 `NbtIo.readPayload`·`NbtIo.In` 에도 음수 길이 거부와 깊이 512(바닐라 `NbtAccounter.unlimitedHeap`)를 넣는 것을 A05 와 같이 검토. 시험은 `A02NbtQuotaReproTest` 를 정식 시험으로 옮기면 된다. 크기 **작음**, 위험 낮음(정상 NBT 는 길이가 음수일 수 없다). 권장 **Sonnet medium**. **가장 먼저 고칠 것.** |

### A02-02 — RCON "인증 전 30초" 가 마지막 읽기 기준이라, 인증 없이 자리를 무기한 붙잡아 관리자 접속을 막을 수 있다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 (RCON 가용성 — 관리 도구 잠김) |
| 종류 | 보안 |
| 근거 | `remote/RconServer.java:203` `socket.setSoTimeout(AUTH_TIMEOUT_MILLIS)` 는 **읽기 한 번마다** 다시 세는 유휴 제한이다. 인증 전에 `TYPE_COMMAND`(`:228`)는 실패 응답만 하고 실패로 세지 않으며, 알 수 없는 종류(`:262`)는 "Unknown request" 로 답하고 역시 세지 않는다. 그래서 20초마다 아무 패킷이나 보내면 인증 없이 영원히 남는다. 자리는 전체 16·IP당 4(`RconLimits`)이고 꽉 차면 새 접속을 바로 닫는다(`:146`). → **주소 4개**면 16자리를 모두 차지해 올바른 비밀번호를 가진 관리자도 들어오지 못한다.<br>바닐라 `RconClient` 는 제한 시간·자리 상한이 아예 없다(연결마다 스레드) — 잠김은 없지만 스레드가 무한히 늘어난다. 이 상한·제한 시간은 Furnace 가 더한 보안 보강(Q2, PR #83)인데 의도("인증 전에는 30초 안에 말을 해야 한다")보다 약하다. |
| 재현 | `A02RemoteReproTest.unauthenticatedClientOutlivesAuthTimeout`: 인증 없이 0·20·40초에 종류 9 패킷 → **40초 뒤에도 연결 유지**(clients=1). 실패.<br>`A02RemoteReproTest.fourAddressesFillAllSlots`: 127.0.0.2~5 에서 4개씩 → 자리 16 모두 차지, 127.0.0.9 관리자 로그인 → `EOFException`(접속하자마자 닫힘). 실패. |
| 제안 | `Client` 에 수락 시각을 두고 인증 전에는 **절대 마감**(수락 + 30초)을 건다(읽기마다 `setSoTimeout(남은 시간)` 또는 마감 넘으면 닫기). 인증 전 로그인 아닌 패킷은 바로 끊거나 실패로 센다. 자리가 꽉 찼을 때 새 접속을 거절하는 대신 가장 오래된 **미인증** 연결을 닫는 것도 고려. `RconServer.java` 만, 크기 **작음**, 권장 **Sonnet medium**. |

### A02-03 — Query 챌린지 표(1024)를 한 주소가 원본 포트만 바꿔 채우면 정상 질의가 최대 30초 막힌다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 (`enable-query` 기본 꺼짐, Query 기능 가용성만) |
| 종류 | 보안 |
| 근거 | `remote/QueryHandler.java:43` 챌린지를 `SocketAddress`(IP+포트)로 저장, `:87` 표가 `MAX_CHALLENGES`(1024)이면 새 핸드셰이크를 무시, 청소는 30초마다(`prune`). 핸드셰이크 패킷은 7바이트라 1024개는 7 KB — UDP 라 주소 위조도 필요 없다. 바닐라 `QueryThreadGs4` 는 상한이 없다(30초 동안 메모리만 늘고 거절은 없음). Furnace 상한이 메모리 위험을 가용성 손실로 바꿨다. |
| 재현 | `A02RemoteReproTest.queryChallengeTableLockout`: 203.0.113.7 의 포트 1~1024 로 핸드셰이크 → 29초 뒤 198.51.100.20 의 핸드셰이크 응답 `null`. 실패. |
| 제안 | IP 당 챌린지 수 상한(예: 8, 같은 IP 의 오래된 것을 밀어냄) + 전체가 차면 거절 대신 가장 오래된 것을 지운다. `QueryHandler.java` 만, 크기 **작음**, 권장 **Haiku low~Sonnet low**. 바닐라 응답 바이트는 바뀌지 않는다. |

### A02-04 — `log-ips=false` 를 무시하고 접속 IP 를 로그에 남기는 곳 두 군데

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 (개인정보 — 운영자가 IP 를 가리도록 설정해도 남는다) |
| 종류 | 바닐라 차이 · 보안 |
| 근거 | `net/WritabilityGuard.java:54` "느린 클라이언트 연결 종료 … ({})" 에 `ctx.channel().remoteAddress()`, `net/ProxyProtocolHandler.java:73` "신뢰하지 않는 주소 {} …" 에 `peer`. 다른 접속 로그는 `ConnectionHandler.loggableAddress()`(바닐라 `Connection.getLoggableAddress`)를 거친다. 바닐라는 IP 를 찍는 모든 접속 로그가 `log-ips` 를 따른다. |
| 재현 | 코드 읽기(로그 문자열과 인자 확인). 시험 없음. |
| 제안 | 두 곳에 `log-ips` 를 넘겨(또는 `ServerLoginContext.logIps` 공유) `<ip address withheld>` 로 가린다. 크기 **작음**, 권장 **Haiku low**. |

### A02-05 — `/worlds load <이름>`·`WorldManager.load(name)` 이 이름 검사 전에 그 경로의 `level.dat` 를 읽는다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (권한 3 명령·플러그인 API, 읽기만, 이어서 실패) |
| 종류 | 보안 |
| 근거 | `world/WorldManager.java:429` `parts.universe().resolve(name).resolve("level.dat")` 를 `WorldCreator` 이름 규칙(`[A-Za-z0-9_.-]{1,64}`, `.`·`..` 거부) **전에** 연다. 명령 인자는 brigadier `word()` 라 `..` 가 허용되므로 `/worlds load ..` 는 서버 폴더 **부모**의 `level.dat` 를 gzip NBT 로 읽고, 그다음 `WorldCreator` 검사에서 실패한다. 플러그인 API 는 `/` 도 넘길 수 있다. 쓰기·노출은 없다(오류 문구만). |
| 재현 | 코드 읽기(가정). |
| 제안 | `storedCreator`·`load` 첫 줄에서 `WorldCreator` 와 같은 이름 검사. 크기 **작음**, **Haiku low**. |

### A02-06 — `proxy-protocol.trusted` 가 16진 글자로만 된 호스트 이름을 숫자 주소로 오인해 기동 때 DNS 를 조회한다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (운영자 설정값, 주석과 다른 동작) |
| 종류 | 보안 · 문서 |
| 근거 | `net/ConnectionLimits.java:214` 정규식 `[0-9a-fA-F:.]+` 는 `cafe`, `dead.beef` 같은 이름도 통과시키고, 이어지는 `InetAddress.getByName` 이 DNS 를 조회한다. 바로 위 주석은 "이름 조회를 피한다: 숫자 주소만 받는다". `BungeeForwarding.ipLiteral`(점 네 칸 IPv4 또는 `:` 포함 IPv6 만)이 이미 올바른 규칙을 갖고 있다. |
| 재현 | 코드 읽기(가정). |
| 제안 | `ipLiteral` 과 같은 규칙으로 바꾼다. 크기 **작음**, **Haiku low**. |

### A02-07 — 명령 자동완성 요청(0x0B)에 Spigot 의 tab-spam 제한이 없다 (바닐라와 같음)

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | Paper/Spigot 호환 · 성능 의심 |
| 근거 | `ConnectionHandler.java:1628`·`:717` 자동완성 요청마다(최대 32500자) 전역 스레드 작업 하나를 넣는다. 바닐라도 제한이 없지만 Spigot `tab-spam-increment`/`tab-spam-limit`(OP 제외 킥)은 있다. Furnace 는 패킷 속도 한도(초당 200)만 걸린다. 권한 없는 선택자·깊은 SNBT 는 이미 막혀 있어(`EntityArgument.parse(reader, source)`, `Snbt` 깊이 512) 비용 폭발은 찾지 못했다. |
| 재현 | 측정 안 함(가정). 비용 측정은 A17 로 넘긴다. |
| 제안 | A17 측정 결과가 나쁘면 Spigot 과 같은 키(`limits.tab-spam-*`, 기본 끔=바닐라)를 더한다. 지금은 조치 없음. |

---

## 확인했고 문제를 찾지 못한 것 (다시 보지 않아도 되는 근거)
- **BungeeCord**: IP 칸은 리터럴만(`ipLiteral`, DNS 없음), 칸 수·모양·UUID·속성 이름 검사가 Spigot 과 같다. 켜지 않았을 때 포워딩 모양을 거절한다. 서명이 없다는 점은 기동 경고(`StartupWarnings.proxy`)가 이미 알린다.
- **Velocity**: HMAC-SHA256 을 `MessageDigest.isEqual` 로 비교, 메시지 번호·로그인 단계(KEY) 검사, 지원 버전 초과 거절, 짧은 비밀 경고.
- **정품 인증**: 검증 토큰은 `SecureRandom` 4바이트, 암호화 응답 배열 256바이트 상한, 세션 조회 실패·장애 구분이 바닐라와 같다. `prevent-proxy-connections` 는 PROXY 프로토콜 주소를 쓴다.
- **프레임·압축**: 단계별 프레임 상한(핸드셰이크 1 KiB·상태 64 B·로그인 1 MiB·구성 128 KiB·플레이 2 MiB), 압축 해제 2 MiB 상한과 임계값 미만 압축 거절 — 압축 폭탄은 2 MiB 로 묶인다.
- **PROXY 프로토콜**: 첫 바이트 검사(Sniffer)로 헤더 없는 직접 접속을 바로 끊고, 신뢰 목록이 비면 기동 경고.
- **RCON**: 비밀번호 상수 시간 비교, 본문 1456 B 상한, 연결당·IP당 실패 제한과 지연(A02-02 를 빼면 의도대로).
- **Query**: 상태 응답은 같은 주소가 받은 챌린지 토큰이 있어야 나가고, 핸드셰이크 응답은 요청과 크기가 비슷해 위조 주소 증폭은 안 된다.
- **클라이언트 입력 길이**: 채팅·명령 256, 책(200쪽·8192자·제목 128), 표지판 384, 모루, `custom_payload` 32767, REGISTER 채널 128개·이름 규칙(Paper 와 같음), 컨테이너 클릭 바뀐 칸 128, 컴포넌트 패치 개수, 채팅 세션 키(512)·서명.
- **권한**: 명령 트리는 `canUse` 로 걸러 보낸다(권한 없는 명령이 자동완성에 보이지 않음), 비-OP 선택자는 파싱 단계에서 거절, 블록·엔티티 태그 질의 권한 2, 구조물·직소 블록 크리에이티브 + 권한 2, 명령 블록·수레는 `enable-command-block` 과 권한, 관전자 텔레포트는 관전자만.
- **파일 경로**: 구조물 템플릿 저장은 `generated/<ns>/structures` 밖이면 거절(네임스페이스 `..` 포함), 라이브러리 좌표는 `..` 와 이상한 글자 거절·sha256 고정/잠금/`.sha1` 검증, 백업은 쓰기만 하고 압축을 풀지 않는다(zip slip 없음), `/worlds create` 이름 규칙.

## 다른 파트로 넘기는 것
- 디스크 NBT 리더의 한도 없음(`NbtIo`) → A05 (A02-01 과 같은 수정으로 묶을 수 있다).
- 자동완성·명령 파싱 비용 → A17 (A02-07).
