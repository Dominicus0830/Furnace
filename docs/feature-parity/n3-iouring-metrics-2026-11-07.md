# N3 — io_uring 전송 옵션, Prometheus·JMX 메트릭 (2026-11-07)

둘 다 `furnace.properties` 에서 켜고 끄며 **기본은 꺼짐**(바닐라와 같은 동작).

## io_uring (`io-uring`)
- `io-uring=true` 면 접속 수락·읽기·쓰기를 io_uring 이벤트 루프(Netty incubator `netty-incubator-transport-native-io_uring:0.0.26.Final`, Netty 4.1.115 와 짝)로 한다. 기본 `false` = 기존 선택(`use-native-transport` 가 true 이고 epoll 이 되면 epoll, 아니면 NIO).
- 쓸 수 없으면(리눅스가 아니거나, 커널이 낮거나, 컨테이너/seccomp 가 `io_uring_setup` 을 막음) 경고 한 줄을 남기고 epoll/NIO 로 되돌아간다 — 서버는 그대로 뜬다. 어느 쪽이 골라졌는지는 기동 로그 `Using io_uring channel type` / `Using epoll channel type` / `Using default channel type`.
- 네이티브 라이브러리는 리눅스 x86_64·aarch64 용만 fat jar 에 싣는다(다른 OS 는 자동으로 NIO).
- 코드: `net/NetworkServer.start()`(선택), `net/TransportOptions`(키 파싱).

## 메트릭 (`metrics.*`)
| 키 | 기본 | 뜻 |
|---|---|---|
| `metrics.prometheus.enabled` | false | `GET /metrics` 를 Prometheus 텍스트 형식 0.0.4 로 내보낸다 |
| `metrics.prometheus.bind` | 127.0.0.1 | 바인딩 주소. 루프백이 아닌데 토큰이 비면 기동 때 경고 |
| `metrics.prometheus.port` | 9225 | 포트(0 = 빈 포트) |
| `metrics.prometheus.token` | (빈 값) | 있으면 `Authorization: Bearer <값>` 이 맞아야 200, 아니면 401 |
| `metrics.jmx.enabled` | false | 같은 값을 `org.domi.furnace:type=Server` MXBean 으로 등록(`PlayersOnline`·`GlobalTps`·`LoadedChunks`·`Metrics`) |

- HTTP 는 JDK 내장 `HttpServer`, 전용 데몬 스레드 1개(`Furnace Prometheus`). 게임·region 스레드는 쓰지 않고, `/tps`·`/mspt` 가 읽는 읽기 전용 계측(`TickStartRing`·`TickWindows`)만 읽는다. 바인딩 실패(포트 점유)는 경고만 하고 서버는 계속 뜬다.
- 내보내는 값(`metrics/ServerMetrics`): `furnace_players_online`, `furnace_tick_rate_target`, `furnace_tps_global{window=5s|1m|5m|15m}`, `furnace_mspt_global_seconds{window=5s|10s|1m}`, `furnace_loaded_chunks{dimension}`, `furnace_regions{dimension}`, `furnace_tps_slowest_region{dimension}`, `furnace_mspt_region_seconds{dimension,window}`, `furnace_jvm_heap_used_bytes`, `furnace_jvm_heap_max_bytes`, `furnace_jvm_threads`. 잴 수 없는 값(NaN)과 틱이 없는 창은 줄을 생략한다.
- 종료: 원격 서비스 닫기 단계(`CLOSE_REMOTE_SERVICES`)에서 HTTP 서버를 닫고 MBean 을 해제한다.

## 확인한 것
- 단위: `MetricsTest`(옵션 파싱·경고, 노출 형식·이스케이프, HTTP 200/401/404/405, 바인딩 실패, JMX 등록·해제), `TransportOptionsTest`(키 파싱, `io-uring=true` 로 기동해 접속 수락 + 고른 전송 방식 확인).
- 실제 기동(클라우드 컨테이너, `io-uring=true`·Prometheus·JMX 켬): `Using io_uring channel type` 로 떴고, 상태 핑(서버 목록 응답)이 정상, `curl :19225/metrics` 가 위 값을 돌려줌. 즉 이 컨테이너 커널에서는 io_uring 이 실제로 동작했다.
- 못 한 것: 실제 클라이언트·여러 명 부하에서 io_uring 이 epoll 보다 나은지(성능 비교), 호스트 커널이 io_uring 을 막는 환경에서의 되돌아가기 — `docs/user-todo.md` 1장.
