# A01 프로토콜·패킷·접속 상태 머신 — 2차 전체 점검 조사 보고서 (2026-11-11)

- **기준 커밋**: `claude/project-thread-wtz9jr` `eab009d9` (PR #180 병합 시점 `claude/quirky-wright-lra834` 과 같음)
- **조사관**: A01 조사 스레드 (Sonnet high). 코드는 고치지 않았다. 바닐라 대조는 `tools/vanilla-data/server-1.21.1` 의 난독화 클래스를 `javap -p -c` 로 읽었다(커밋하지 않음).
- **재현 시험**: `docs/audit/findings/A01-repro/*.txt`. 이번 점검의 재현은 대부분 **실서버에 붙는 Node 스크립트**다(독립 프로토콜 구현으로 검증하는 것이 이 파트의 핵심이라 JUnit 이 아니다). 쓰는 법은 각 파일 첫머리 주석. 기준 커밋에서 "바닐라 기대값"과 다르게 나오는 것이 재현이다.

## 한 줄 결론

핸드셰이크→상태→로그인→구성→플레이 흐름에서 **Furnace 가 보내는 패킷의 바이트 형식에는 바닐라와 어긋난 곳을 찾지 못했다**(독립 구현으로 파싱해 진짜 오류 0건). 찾은 것은 모두 **바닐라가 처리하는데 Furnace 가 무시하는 패킷·경로**(낮음 3, 참고 3)이고 치명·높음·중간은 없다.

## 조사한 범위

- **독립 구현 대조(핵심)**: node-minecraft-protocol 1.68.0 + minecraft-data 1.21.1 스키마(알려진 스키마 오류는 고쳐 씀)로 오프라인 Furnace 서버에 접속해, 콘솔 명령·봇 행동으로 만든 실제 흐름의 모든 패킷(상태·로그인·구성·플레이)을 파싱했다. protodef 는 `PartialReadError`·남는 바이트를 로그만 남기고 넘어가므로 `serializer.js` 를 고쳐 `global.__pktErrs` 에 모았다(`protocheck.js.txt`).
- **실흐름에서 안 나오는 패킷**: 클라이언트바운드 패킷 약 120개의 `write` 본문을 바닐라 필드 순서와 줄 대 줄로 읽어 비교했고(`pairs.txt` 방식), 드문 31개 패킷(말 화면·유령 레시피·개체 소리·플레이어 정보 제거/갱신·전투 진입·시선·연결 줄·탑승자·책 열기·탈것 이동·청크 반경·시뮬레이션 거리·카메라·이전(transfer)·서버 링크·리소스 팩 푸시·가져가기·채팅 삭제·사용자 지정 페이로드·컨테이너 데이터·레시피 책·지도·장비)는 `A01PacketDumpTest.java.txt` 로 실제 직렬화 바이트를 뽑아 독립 파서(`parsedump.js.txt`)로 읽었다. 남는 바이트·읽기 부족 **0건**.
- **연결 상태 머신** (`ConnectionHandler`): 핸드셰이크 의도(상태·로그인·이전), 프로토콜 번호 불일치 킥 문구(바닐라 `arv` 의 754 분기와 같음 — 766 이든 999 든 `multiplayer.disconnect.incompatible`), 상태 요청·핑 순서, 로그인(암호화 요청/응답·압축 설정·`LoginSuccess` 끝의 strict-error-handling 불리언), 로그인 확인 → 구성 → 구성 끝 확인 → 플레이, 구성 단계 재진입(`start_configuration`), 쿠키·이전 요청, 구성 단계 keep-alive, 리소스 팩 응답 상태(`ResourcePackResponses`), 사용자 지정 페이로드·`PluginChannels`.
- **프레임·압축·암호**: `FrameDecoder`/`FrameEncoder`/`CompressionDecoder`/`CompressionEncoder`/`Types`(VarInt·VarLong·문자열·각도·블록 좌표 포장 — 모두 바닐라와 같음).
- **keep-alive 시각**: 15초 전송, 앞 것 미응답이면 `disconnect.timeout` 킥. `/tick freeze` 중에도 계속 간다(실측: 정지 50초 동안 3회 수신, 끊김 없음 — 전역 박동이 `/tick rate` 와 무관한 50ms 고정이라서).
- **구성 단계 순서**: 브랜드 → 서버 링크 → 기능 플래그 → 알려진 팩 → 레지스트리 → 태그 → 리소스 팩 → 끝(바닐라와 같음).

## 조사하지 못한 범위 (이유)

- **A02 몫**: 악성·과대 입력, 속도 제한, 인증·암호화 응답 검증. (문자열 한도를 Furnace 가 ×4, 바닐라가 ×3 바이트로 잡는 차이는 유효한 입력에서는 드러나지 않아 A02 에 넘긴다.)
- **A14 몫**: 플러그인 패킷 API 계약. **PR #151** 의 채팅·표시 이름은 이미 검토됨.
- **실흐름으로 못 만든 클라이언트바운드 패킷**: 주민 거래 목록(`merchant_offers`), 책 열기, 실제 지도 흐름, 개체 소리 — 봇으로 주민 거래 GUI 를 열지 못했다(원시 `use_entity` 로는 안 열림, 프로토콜이 아닌 상호작용 쪽 문제로 보임). 이 패킷들은 위 "줄 대 줄 읽기 + 덤프 파서"로만 검증했고 **실서버 흐름 검증은 못 했다**.
- **실제 바닐라 클라이언트 접속**: 컨테이너에 없다. 독립 구현이 바닐라 클라이언트와 같은 스키마를 쓴다는 가정이다.
- 독립 구현의 알려진 **거짓 양성**(Furnace 오류 아님): ① mc-data 레시피 직렬기 목록에 여분 항목 `crafting_special_banneraddpattern` 이 있어 id ≥ 11 이 한 칸 밀림 → 제거하면 `declare_recipes` 오류가 사라진다(바닐라 `cze` 목록 23개와 Furnace `RecipeManager.SERIALIZERS` 23개가 같음). ② mc-data `food` 컴포넌트에 선택 필드 `usingConvertsTo`(바닐라 `cpr`)·효과 목록 모양이 빠져 `set_slot` 에 남는 바이트가 보임. ③ `wolf_variant` 홀더·`potion_contents.customName` 은 `tools/bot-test/patch-registry.js` 가 고친다.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 0 | |
| 중간 | 0 | |
| 낮음 | 3 | A01-01, A01-02, A01-03 |
| 참고 | 4 | A01-04, A01-05, A01-06, A01-07 |

도미니코 결정이 필요한 항목은 없다(셋 다 바닐라와 같게 만드는 일이다) — user-todo 항목을 만들지 않았다.

---

## 항목

### A01-01 — 구형(첫 바이트 0xFE) 서버 목록 핑에 답하지 않는다

| 칸 | 내용 |
|---|---|
| 심각도 | **낮음** (옛 서버 목록·일부 모니터링 도구가 Furnace 를 "응답 없음"으로 본다. 현재 클라이언트에는 영향 없음) |
| 종류 | 바닐라 차이 · 기능 누락 |
| 근거 | 바닐라 `art$1` 이 파이프라인에 `legacy_query` 로 `aro`(LegacyQueryHandler)를 먼저 단다 — `FE`(1.4)·`FE 01`(1.5)·`FE 01 FA 00 0B "MC\|PingHost" …`(1.6)에 `FF` + UTF-16BE 문자열(`§1\0프로토콜\0버전\0MOTD\0인원\0최대`; 1.4 는 `MOTD§인원§최대`)로 답하고 닫는다. Furnace `NetworkServer.java:154-159` 파이프라인은 `readTimeout → writabilityGuard → frameDecoder → frameEncoder → handler` 이고 0xFE 처리가 없다(`grep legacy` 결과 없음). `FE` 는 VarInt 연속 바이트라 길이가 끝나지 않고, `FE 01` 은 길이 254 프레임으로 읽혀 채워지지 않아 읽기 타임아웃까지 대기한다. |
| 재현 | `legacy-ping.js.txt`: `FE`, `FE 01`, `FE 01 FA …` 각각 보내고 3초 대기 → **세 경우 모두 0바이트 수신, 연결은 열린 채**. 바닐라는 `FF …` 응답 후 닫음. 재현됨. |
| 제안 | `net/LegacyPingHandler.java`(첫 바이트 0xFE 를 엿보는 `ByteToMessageDecoder`, 아니면 자기를 파이프라인에서 제거) 를 `NetworkServer` 에서 `frameDecoder` 앞에 추가. MOTD·인원·최대는 이미 있는 `ServerLoginContext.statusJson` 입력과 같은 원천에서 가져온다. 단 PROXY 프로토콜 사용 시 `haproxyDecoder` 뒤에 둘 것(순서 주의). 파일 1~2개, 크기 **작음**, 위험 낮음, 권장 **Sonnet medium**. |

### A01-02 — play 단계 `ping_request`(0x21)에 `pong_response`(0x36)로 답하지 않는다

| 칸 | 내용 |
|---|---|
| 심각도 | **낮음** (클라이언트 F3 의 핑 그래프가 비어 있다 — 게임 플레이에는 영향 없음) |
| 종류 | 바닐라 차이 · 기능 누락 |
| 근거 | 바닐라 `aru.a(ajm)`(ServerGamePacketListenerImpl.handlePingRequest) = `connection.send(new ajj(packet.getTime()))` 로 **즉시** 같은 시각을 되돌린다(주 스레드로 넘기지도 않음). Furnace `ConnectionHandler.handlePlay` 의 `switch` 에 0x21 이 없고 `default -> { /* 그 외 무시 */ }`(`:2120`). 클라이언트바운드 0x36 패킷 클래스도 없다. |
| 재현 | `play-ping-debug.js.txt`: 접속 후 `ping_request` 한 번 → 2초 안에 `pong_response` **0개**(바닐라 기대값 1). 재현됨. |
| 제안 | `net/protocol/play/PongResponse.java`(id 0x36, long) 추가, `handlePlay` 에 `case 0x21 -> send(ctx, new PongResponse(frame.readLong()))`. 이벤트 루프에서 바로 답한다(바닐라와 같음). 파일 2개, 크기 **매우 작음**, 권장 **Haiku low**. |

### A01-03 — `debug_sample_subscription`(0x13)을 무시해 OP 의 F3 틱 시간 그래프가 비어 있다

| 칸 | 내용 |
|---|---|
| 심각도 | **낮음** (OP 전용 디버그 화면) |
| 종류 | 바닐라 차이 · 기능 누락 |
| 근거 | 바닐라 `aru.a(aha)` → `apn.a(aqv, bls)` → `blq.a(player, type)`: `PlayerList.isOp(profile)` 일 때만 요청 큐에 넣고(권한 레벨 검사 없음, 비 OP 는 조용히 무시), 매 틱 `blq.a(int)` 가 구독을 갱신한다. 만료 조건은 `틱 > 시작+200 && ms > 시작+10000` 이지만 큐를 비우는 호출이 없어 **한 번 요청한 OP 는 OP 가 풀릴 때까지 계속 구독**된다(독립 검증으로 확인). 구독자가 있으면 틱마다 `debug_sample`(clientbound 0x1b: long 배열 + 종류 enum, TICK_TIME 하나뿐)을 구독자 전원에게 1개씩 보낸다. Furnace 는 0x13·0x1b 모두 없다(위 A01-02 와 같은 `default` 로 버려짐). |
| 재현 | `play-ping-debug.js.txt` 의 2부: `op` 를 준 뒤 `debug_sample_subscription`(type 0) → 6초 동안 `debug_sample` **0개**(바닐라 기대값 ≥ 1). 재현됨. |
| 제안 | 구독 표(플레이어→만료 시각)와 전송은 전역 박동(`ServerCore.scheduledTick`)에서. 무엇을 "틱 시간"으로 보낼지는 정해야 한다 — 전역 박동(`globalTickRing`)이 가장 쉽고, region 별 틱 시간(`TickTimeRing`)을 플레이어 소속 region 으로 보내면 바닐라보다 더 쓸모 있다. 후자를 고르면 A17(성능) 과 겹치므로 먼저 전역 박동으로 시작 권장. 파일 3개 안팎, 크기 **작음~중간**, 권장 **Sonnet medium**. |

### A01-04 — 보낸 적 없는 keep-alive id 를 받아도 끊지 않는다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (정상 클라이언트는 그런 id 를 보내지 않는다 — 버그 있는 모드·프록시에서만 보임) |
| 종류 | 바닐라 차이 |
| 근거 | 바닐라 `arr.a(aac)`: 대기 중이고 id 가 같으면 지연(`o`)을 갱신하고, **아니면** 싱글플레이 호스트가 아닌 한 `disconnect.timeout` 으로 끊는다. Furnace `ConnectionHandler.java:1660-1667` 은 id 가 달라도 그냥 무시(대기 상태 유지)하고, 대기 여부도 보지 않아 같은 id 를 다시 보내도 지연이 또 기록된다. 첫 keep-alive 를 보내기 전엔 `lastKeepAliveId` 가 0 이라 id 0 도 "일치"로 처리된다. 틀린 id 만 보내는 클라이언트는 다음 주기(최대 15초 뒤)에야 끊긴다. 응답 없음 15초 킥(`sendKeepAlive`)은 같다. |
| 재현 | `keepalive-wrong-id.js.txt`: 서버가 보낸 적 없는 id 를 보내고 3초 대기 → **연결 유지**(바닐라는 끊김). 재현됨. |
| 제안 | `case 0x18` 에서 `awaitingKeepAlive` 가 꺼져 있거나 id 가 다르면 `kick(ctx, disconnect.timeout, TIMED_OUT)`. 이미 응답한 id 를 지연 도착한 패킷이 다시 보내는 경우는 정상 클라이언트에서 일어나지 않는다. 한 줄 수준, 권장 **Haiku low**. |

### A01-05 — 디코드 실패·규격 위반 때 바닐라의 "Internal Exception" 끊김 사유를 보내지 않고 그냥 닫는다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (악성/버그 클라이언트가 볼 메시지 — 서버 가용성에는 영향 없음, A02 경계) |
| 종류 | 바닐라 차이 |
| 근거 | 바닐라 `vt.exceptionCaught`: 타임아웃이면 `disconnect.timeout`, 그 밖이면 `disconnect.genericReason`("Internal Exception: " + 예외)로 **로그인 단계는 `ait`(LoginDisconnect), 그 외는 `zp`(Disconnect)** 를 보내고 닫는다. Furnace `ConnectionHandler.exceptionCaught`(`:4658`)는 타임아웃만 킥 문구를 보내고, 나머지는 `closeFlushed`(문구 없이 닫음). 같은 파일의 `protocolError`(`:859`)는 바닐라 문구를 흉내 내므로 **일부 경로만** 문구를 보낸다. |
| 재현 | `state-machine-edges.js.txt`: 이름 17자 로그인·로그인 중 play 패킷(0x1A)·256자 호스트명 핸드셰이크 → 세 경우 모두 **0바이트 수신 후 닫힘**(바닐라는 LoginDisconnect 로 "Internal Exception: …"). 바닐라 쪽 정확한 문자열은 바이트코드로 형태만 확인(예외 클래스 이름은 구현마다 달라 **가정**). |
| 제안 | `exceptionCaught` 의 마지막 줄을 `disconnect(ctx, Text.translatable("disconnect.genericReason", "Internal Exception: " + cause))`(상태별 Disconnect 선택은 `disconnect()` 가 이미 함)로. 이 경로는 A02(악성 입력) 수정과 같은 줄이므로 **A02 후속 수정 스레드와 묶어** 한 번에. 한 줄 수준, 권장 **Haiku low**. |

### A01-06 — ClientInformation 구문 분석 오류를 삼키고, 읽은 앞부분만 적용한다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (정상 클라이언트는 항상 완전한 패킷을 보낸다) |
| 종류 | 바닐라 차이 · 견고성 |
| 근거 | `ConnectionHandler.readClientInformation`(`:1011-1031`) 은 필드를 읽는 대로 즉시 플레이어에 반영(`p.locale`·`p.viewDistance`·`p.chat().visibility` …)하다가 `catch (Exception e) { log.debug(...) }` 로 예외를 삼킨다. 중간에 잘린 패킷이면 일부만 반영된 채 연결이 유지된다(예: 렌더 거리는 바뀌고 피부 겉옷·주 손은 그대로). 바닐라는 `ServerboundClientInformationPacket` 디코드가 실패하면 연결을 끊는다(위 A01-05 경로). |
| 재현 | 코드 읽기. 시험 없음. |
| 제안 | 전부 읽어 `ClientInformation` 값 객체를 만든 뒤에 한 번에 적용하고, 읽기 실패는 `exceptionCaught` 로 올린다. 같은 값 객체를 구성·플레이 양쪽(`:908`, `:1626`)이 공유. 파일 1개, 크기 **작음**, 권장 **Haiku low~Sonnet low**. |

### A01-07 — 지연(ping) 첫 표본을 평활화 없이 그대로 쓴다(탭 목록 핑이 접속 직후 높게 나옴, 가정)

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 (탭 목록 핑 숫자 표시만) |
| 종류 | 바닐라 차이 |
| 근거 | 바닐라는 항상 `(이전*3 + 표본)/4`(`arr.a(aac)`)라 초기값 0 에서 첫 표본이 표본/4 로 들어간다. Furnace `Player.recordLatency`(`Player.java:2159-2162`)는 `old == 0` 이면 표본을 그대로 넣는다(주석 "첫 측정은 그대로"). 예: 실제 40ms → 바닐라 10, Furnace 40, 이후 서서히 수렴. 바닐라 초기값이 구성 단계에서 넘어온 값이라 0 이 아닐 수 있어 **가정**이다(독립 검증 의견). |
| 재현 | 코드 읽기. 시험 없음. |
| 제안 | `latencyMs = (old * 3 + sampleMs) / 4` 한 줄로(구성 단계 keep-alive 응답 시간을 초기값으로 넘기는 것까지 바닐라와 맞추려면 `scheduleConfigKeepAlive` 응답 경로도 확인). 한 줄 수준, 권장 **Haiku low**. 바닐라 플레이어 정보 `latency` 필드를 쓰는 곳(탭 목록)에서만 보이는 차이라 우선순위 낮음. |

---

## 이상 없음으로 확인한 것 (다시 조사하지 않도록)

- 서버가 보내는 모든 패킷 종류의 필드 순서·타입·가변 길이: 독립 구현 파싱 오류 0건(거짓 양성은 위 "조사하지 못한 범위" 참고).
- 프로토콜 번호 불일치 킥(766·999 모두 `incompatible`, 바닐라 `arv` 가 754 미만만 `outdated_client` 로 갈라서 같음), 상태 요청·핑 순서 변형(바닐라와 같은 결과로 닫힘), 로그인 시작 중복(닫힘).
- 구성 단계 keep-alive, 구성→플레이 전환 순서, `start_configuration` 재진입.
- `/tick freeze` 중 keep-alive 계속 전송.

## 독립 검증

- A01-01~04 는 Fable 하위 에이전트에 바이트코드를 직접 읽게 해 독립 검증했다(읽기 전용). 네 항목 모두 차이를 확인했고, 두 곳을 고쳤다: A01-03 의 구독 만료(10초 → 사실상 만료 안 됨), A01-04 에 대기 여부 미검사·id 0 일치를 추가. A01-07 은 그 검증에서 새로 나왔다. 클라이언트 화면에서의 관측은 클라이언트 jar 를 보지 않은 **가정**이다.

## 다른 파트에 알릴 것

- **A02/A05 수정 스레드**: A01-05 는 `exceptionCaught` 한 줄이므로 A02 후속 수정에 포함.
- **A17**: A01-03 의 "틱 시간" 표본 원천을 정할 때 region 별 틱 시간을 쓸지 결정.
- **충돌 주의**: A01-02·03·04·05·06 은 모두 `ConnectionHandler.java` 의 `handlePlay` 바로 근처다. 같은 시기에 다른 수정 스레드가 `ConnectionHandler` 를 건드리면 충돌한다. A01-02·04·05·06·07 은 한 스레드(Haiku/Sonnet low)가 한 PR 로 묶어 처리하기를 권한다.
