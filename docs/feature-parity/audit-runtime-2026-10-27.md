# 런타임·네트워크·영속성 감사 재검증 (2026-10-27)

기준: 최적화 감사 최종 보고서(옛 커밋 b138cd7 기준)를 HEAD `838a8fb4`(claude/quirky-wright-lra834)에서 항목별로 다시 확인했다.
이미 처리된 것(N-01 ①, D-05, N-02~N-05, N-16, E-07~E-09, I-3~I-5, IoLanes)은 빼고 본다.
줄 번호는 이 커밋 직전 HEAD 기준이다. 경로 접두 `S/` = `furnace-server/src/main/java/org/domi/furnace/server/`.

## 결과표

| 항목 | 판정 | 근거(파일:줄) | 조치 |
|---|---|---|---|
| N-06 dirty 게이트 제거 | 해결됨 | `S/world/World.java:639,656-668` — 본문·스냅샷 캐시가 dirty 와 무관하게 `modCount` 로만 판정 | 없음 |
| N-07 팔레트 네이티브 직렬화 | 아직 있음, 보류 | `S/net/protocol/play/Palette.java:21-24` 가 `int[4096]` 을 HashMap 으로 첫 등장 순서 재팔레트화. 섹션 저장 팔레트는 set 순서라 순서가 달라 네이티브로 바꾸면 와이어 바이트가 바뀐다 | 와이어 바이트 불변 조건을 지킬 수 없어 보류. 본문 캐시(N-05/N-06)로 재직렬화 빈도가 이미 낮다 |
| N-09 디스크 로드 벌크 디코드 | 아직 있음, 보류 | `S/world/anvil/AnvilChunkCodec.java:410-438` 블록마다 `setFromIndex`, 뒤에 `rebuildHeightmapsAfterSurface`(296) | 디스크 경로는 편집 청크·외부 월드만 탄다(F-14). 높이맵 의미 검증 필요, 측정 없음 → 보류 |
| N-10 wake-up·틱 사이 인박스 | 아직 있음(구조만 바뀜), 보류 | 실제 경로는 `RegionThread` 가 아니라 `S/world/Regionizer.java` 데드라인 큐(386-419). 인박스는 `Region.runTick` 시작에서만 비운다(`Regionizer.enqueue` 245-249 는 깨우지 않음). `RegionThread`(`S/sched/RegionThread.java:93-113`)는 월드 없을 때의 대체 경로 | 틱 사이 실행은 작업 실행 시점(틱 순서)을 바꿔 parity 위험. B안(`shared-scheduler-review-2026-10-27.md`) 재설계와 같이 본다 |
| N-11 풀 크기·epoll | epoll: 적용 / 풀: 아직 있음 | `S/net/NetworkServer.java:63-64` 가 항상 NIO. `use-native-transport`(`S/config/ServerProperties.java:60`)는 읽기만 하고 안 썼다. netty-all 4.1.115 pom 이 linux-x86_64/aarch_64/riscv64 native epoll 을 runtime 으로 포함 | 바닐라 `ServerConnectionListener` 처럼 `use-native-transport && Epoll.isAvailable()` 면 epoll, 로그도 바닐라 문자열("Using epoll channel type"/"Using default channel type", 1.21.1 jar 문자열로 확인). 풀(청크=코어, gen=max(2,코어), IO=설정, region 워커, netty 2×코어) 통합은 측정 데이터 없음 → 보류 |
| N-12 autosave 분산 | 해결됨 | `S/world/Region.java:507-548,740-744` 틱당 4 ms 예산 큐로 나눠 저장, 카운터는 region 별(분할 시 복사 200) | 없음 |
| N-15 JVM/GC | 현재 설정 평가만 | `furnace-server/build.gradle.kts:27` `-Xmx6G` 뿐 | 측정 없이 바꾸지 않음. 제안: `-Xms6G`(=Xmx) + `-XX:+AlwaysPreTouch`, G1 유지 + `-Xlog:gc*:file=gc.log` 로 할당률·정지 시간 먼저 측정, 그 뒤 `-XX:+UseZGC -XX:+ZGenerational` 비교(가정: 코어·힙 여유가 있을 때만 이득) |
| N-17 청크 요청 조기 취소 | 대부분 해결됨 | 이동 시 시야 밖 pending 제거 `S/world/Region.java:1407`, 꺼낼 때 구독 확인 1474, 진행 중 상한 `maxChunkSendsInFlight` 1457, 가까운 것부터 재정렬 1443 | 이미 시작한 생성·직렬화의 취소는 안 한다(생성 결과는 캐시돼 다시 쓰이고 5×5 의존이 있어) → 보류 |
| E-11 탭 목록 O(P²) | 적용 | `S/world/Region.java:1511-1536` 이 매 틱 관찰자마다 접속자 전원 `putIfAbsent` | 접속자 집합 번호(`Player.rosterEpoch`)가 바뀐 틱에만 훑는다. 합성 측정(`ConcurrentHashMap` 같은 연산): P=100 0.33 ms/틱, P=500 7.6 ms/틱 → 변화 없는 틱 0 번호는 서버 전역이라 한 차원의 입·퇴장이 다른 차원 관찰자도 한 번 다시 훑게 한다 — 비용만의 문제라 그대로 둔다 |
| E-11 SetHeadRotation 중복 | 적용 | `S/world/Region.java:1628` 이동·회전 때마다 무조건 전송 | 바닐라 `ServerEntity.sendChanges` 처럼 `floor(yaw*256/360)` 이 바뀐 때만. 이동만 한 플레이어의 관찰자당 패킷 1개 감소. 클라이언트가 보는 머리 각도는 같다 |
| E-10 region→스레드 정적 해시 | 해결됨(해당없음) | 실제 경로는 Regionizer 워커가 공유 데드라인 큐에서 가져간다(`Regionizer.java:386-419`). `31x+z` 해시는 대체 경로 `S/sched/FurnaceSchedulerImpl.java:50` 에만 남음 | 없음 |
| F-01 락 밖 deflate | 아직 있음, 보류 | `S/world/anvil/RegionFile.java:88` 파일 잠금 안에서 deflate. **정정**: 처음엔 "IoLanes 가 같은 파일 작업을 줄 세워 실효상 해결"이라 적었으나 틀렸다 — 레인 밖 호출이 있다(`S/world/World.java:962` region 스레드의 `hasChunk`, `S/world/gen/AnvilWorldSource.java:34` 레인 없는 `generate`, `loadChunkAsync` 의 ioExecutor). 같은 파일 작업이 모두 레인을 탄다는 근거는 없다 | 잠금 밖 deflate 는 정확성 문제가 아니라 같은 파일 병렬도 문제라 측정 없이 보류. 정확성은 F-12 수정의 파일별 잠금이 보장한다 |
| F-05 섹터 할당기 | 아직 있음, 보류 | `RegionFile.java:130-139` 끝에 붙이기만(옛 섹터 누수) | 할당기 오류 = 영구 손상, 속성 테스트 선행 필요 → 보류 |
| F-12 핸들 LRU | 적용(2차 수정) | `S/world/anvil/RegionFileStore.java:12,27-36` 무제한 `HashMap` — 오래 탐험하면 fd 가 계속 는다(블록·엔티티·poi 3벌) | 바닐라 `RegionFileStorage` 와 같은 256개 LRU(1.21.1 jar 상수 256 확인). 1차 구현(76b5364c)은 내보낸 핸들이 쓰는 중일 때 같은 키를 새로 열어 한 파일에 핸들 두 개가 생길 수 있었다(새 핸들이 낡은 섹터 표로 할당 → 청크 덮어쓰기). 위 레인 밖 호출 때문에 IoLanes 로는 못 막는다. 2차: 핸들과 분리한 파일별 잠금 안에서만 열기·작업·닫기, 내보낸 핸들은 대기열에 두고 키 잠금을 하나도 쥐지 않은 채 그 키 잠금을 잡고 닫는다(잠금 순서 교착 없음). 회귀 테스트 `lru_evictionDuringWrite_doesNotOpenSecondHandle`(래치로 결정적 재현)는 1차 코드에서 실패(C 의 청크 손상)하고 2차에서 통과 |
| I-7/D-13 로그인 전 무제한 처리 | 적용 | `S/net/ConnectionHandler.java:157-167` TokenBucket 이 PLAY 에만 | 속도 제한을 모든 단계에 건다(바닐라 클라는 로그인·설정 단계에 몇 개만 보냄) |
| I-7/D-13 상태별 프레임 상한 | 적용 | `S/net/io/FrameDecoder.java:11` 모든 단계 2 MiB | 핸드셰이크 1024B(최대 합법 781B: id 1 + VarInt 5 + 주소 255자×3+3 + 포트 2 + VarInt 5), 상태 64B(최대 9B), 그 뒤 2 MiB(바닐라 21비트). 길이만 보고 본문 버퍼링 전에 끊는다 |
| 주의: 레거시 포워딩 | — | `S/net/protocol/handshake/Handshake.java` 주소 255자 | BungeeCord 레거시 포워딩(주소 칸에 IP·UUID·속성 JSON)을 지원하게 되면 핸드셰이크 1024B·255자 상한을 다시 정해야 한다. 현재 지원 계획 없음 |
| I-7/D-13 `dataLength<threshold` 미검사 | 적용 | `S/net/io/CompressionDecoder.java:16-17` threshold 미사용 | 바닐라 1.21.1 과 같은 검사·메시지. 근거(바이트코드 대조): `tools/vanilla-data/versions/1.21.1/server-1.21.1.jar` 을 스크래치에 풀어 `javap -c -v`. 난독 클래스 `vr`(= CompressionDecoder: `ByteToMessageDecoder` 상속, 상수 `2097152`·`8388608`, 생성자 `(int, boolean)`) 의 `decode` 가 `e`(validate) 참일 때 `dataLength < d(threshold)` 면 "Badly compressed packet - size of \u0001 is below server threshold of \u0001", `> 8388608` 이면 "... is larger than protocol maximum of 8388608" 로 `DecoderException`. `vt`(Connection)의 `a(IZ)` 가 `new vr(I,Z)` 로 "decompress" 를 넣고, 로그인 리스너 `arw` 가 `vt.a(server.aA(), iconst_1)` 로 부른다 → 서버는 validate=true. 해제 상한은 바닐라 8 MiB 보다 좁은 2 MiB 유지(C→S 최대 패킷이 1 MiB 커스텀 응답이라는 것은 **기억**, jar 미대조) |
| I-7/D-13 IP 쓰로틀 | 적용 안 함 | — | 바닐라에는 없다(Bukkit `connection-throttle` 만). 넣으면 바닐라와 재접속 동작이 달라진다 |
| D-14 PlayerData.load 를 IO 풀로 | 아직 있음, 보류 | `S/net/ConnectionHandler.java:737` 이벤트루프에서 동기 읽기(접속당 1회) | 비동기로 바꾸면 ack 뒤 ~ `onEnterPlay` 사이에 오는 play 패킷의 처리 순서를 다시 설계해야 한다. 이득은 접속당 파일 1개 읽기 → 보류 |
| E-19~E-25 틱 핫패스 할당 | 보류 | 예: `updateVisibility` 의 `HashSet seen`·`List.of` (`Region.java:1552`) | 프로파일 근거 없음. 측정(async-profiler alloc) 뒤 결정 |

## 이번에 바꾼 것
- `FrameDecoder.maxLength` + `ConnectionHandler.frameLimit`/`applyFrameLimit`(단계 전환마다), 모든 단계 TokenBucket.
- `CompressionDecoder` 임계값 미만 압축 거부.
- `NetworkServer.useNativeTransport` + `FurnaceMain` 에서 `use-native-transport` 전달.
- `RegionFileStore` LRU 256(2차: 파일별 잠금으로 핸들 중복 방지).
- `Region.syncTabList` 접속자 번호 게이트, `broadcastMove` 머리 회전 변화 시만.
- 테스트: `FrameLimitTest`, `FramingTest`·`CompressionTest`·`RegionFileStoreTest` 추가 케이스.
