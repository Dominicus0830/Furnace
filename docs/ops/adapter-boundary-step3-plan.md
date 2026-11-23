# 어댑터 분리 3단계: 거울 동기화를 훅 뒤로 (T-251, 2026-11-23)

2단계(T-250) 다음. 목표는 핵심 자료구조(`Region`·`ChunkColumn`)가 NMS 청크 거울의 **형(type)** 을 모르게 하는 것이다. 맞춤 **지점**(틱 끝 공개·저장·내리기·읽기 진입)은 그대로 두어 동작과 차례를 바꾸지 않는다.

## 지금 모양
- `ChunkColumn` 이 `api.internal.ChunkInternals.ChunkMirror` 형을 직접 들고 있다(`ShimRef.mirror` 약참조, `mirrorHeld`, `mirror()`, `markMirrorPending(m, …)`).
- 그래서 `ChunkColumn` 과, `col.mirror()` 를 부르는 `Region` 이 허용 목록(③)에 있다.
- 맞춤 지점은 `Region` 의 `publishReadViews`(틱 끝)·`syncAllMirrors`(전체 저장)·저장 직전·`unloadMirror`(내리기)와 `World.syncMirrorIfPending`(읽기 진입)이다. 대기 상태(`mirrorPending`·CAS·`mirrorHeld`)는 region 나누기·합치기 때 청크와 함께 가야 하므로 컬럼에 남는다(R8).

## 바꾼 모양
- 서버 쪽 중립 인터페이스 `world.ChunkMirrorOps` 하나: `boolean sync(Object mirror)`, `void published(Object mirror, ChunkReadView view)`, `void unloaded(Object mirror)`.
- `ChunkColumn` 은 거울을 `Object` 로 들고(약참조·강참조 규칙 그대로), 부를 때 `ChunkMirrorOps` 를 거친다. 구현은 다리 `ChunkInternalsImpl` 안의 `MirrorOps`(이미 허용 목록에 있는 클래스 — 새 줄 없음)이고, 그 클래스가 올라올 때(static 초기화) `ChunkColumn.installMirrorOps` 로 꽂는다. 거울은 그 다리로만 붙으므로 늘 먼저 꽂힌다. 다리를 거치지 않고 거울을 붙이는 시험·벤치는 `InternalsImpl.installChunkMirrorOps()` 를 먼저 부른다. 거울이 붙었는데 훅이 없으면 조용히 넘기지 않고 `IllegalStateException`.
- 감싸개 객체를 만들지 않는다: 거울의 동일성 비교(`markMirrorPending`)와 "shim·거울이 서로 쥐어 함께 사라진다"는 약참조 수명이 그대로다. 감싸개를 만들면 누가 감싸개를 강하게 쥘지가 새로 생겨 수명 버그 위험이 있다.
- 결과: 허용 목록에서 `Region`·`ChunkColumn` 두 줄이 빠졌다(12 → 10).
- `ChunkSection`·`ChunkReadView`·`PaletteStorage` 세 줄은 이 단계에서 다루지 않는다. 이들은 읽기 보기 **자료형**(`PackedStates`·`ChunkView`)을 복사 없이 내주려고 API 인터페이스를 직접 구현한다. 감싸면 읽기마다 할당·간접 호출이 늘어 NMS 읽기 성능(N2a R8)에 걸린다. 따로 측정해 "그대로 둠(이유 기록)"인지 정한다(3b).

## 성능 측정 계획
틱 경로라 바꾸기 전·후를 같은 컨테이너에서 같은 명령으로 잰다. 각 5번 돌려 중앙값과 범위를 적는다.

| # | 무엇 | 어떻게 | 통과 기준 |
|---|---|---|---|
| P1 | 거울 없는 보통 서버의 region 틱 | 기존 `RegionTickBench`(`:furnace-server:bench --tests '*RegionTickBench*'`, 기본 장면) | 전·후 중앙값 차이가 실행 간 범위 안 |
| P2 | 거울이 붙은 청크가 많은 틱 끝 공개 | 새 벤치 `MirrorSyncBench`: 지켜보는 청크 1,000개에 아무것도 안 하는 거울을 붙이고 틱 끝 맞춤·공개 알림을 2,000틱(벤치 기본값), 청크당 ns | 차이 2% 이하 또는 실행 간 범위 안 |
| P3 | 대기 쓰기가 있는 맞춤(읽기 진입) | 같은 벤치에서 틱마다 청크 100개에 대기 표시 → `World.syncMirrorIfPending` | P2 와 같음 |
| P4 | 저장·내리기 | 같은 벤치에서 거울 붙은 청크 1,000개 저장·내리기 시간 | P2 와 같음 |

- P2~P4 는 "바꾸기 전" 값을 얻으려고 벤치를 **먼저** 기준 브랜치(77cedd25) 위에서 잰 다음 구조를 바꿨다. 바꾼 뒤 벤치에 달라진 것은 훅을 꽂는 한 줄뿐이다.

## 측정 결과 (2026-11-23, 클라우드 컨테이너 한 대, 같은 명령)
- P2·P3(`MirrorSyncBench`, 청크 1,000개·2,000틱, 앞 1/4 예열 제외, 5번씩): 값이 두 무리(약 8~12 ns 와 약 23~26 ns)로 갈리는 실행 간 흔들림이 있다(JIT·컨테이너 잡음 — 같은 코드에서도 갈린다).

  | | 틱 끝 맞춤+알림 p50(ns/청크) | 대기 표시→맞춤 p50(ns/쓰기) | 내리기(ns/청크, 한 번 잰 값) |
  |---|---|---|---|
  | 전 | 8.8 · 25.8 · 25.5 · 9.4 · 25.1 (중앙 25.1) | 47.5 · 71.5 · 73.4 · 48.5 · 71.1 (중앙 71.1) | 258 · 382 · 565 · 544 · 214 |
  | 후 | 11.8 · 8.6 · 8.9 · 23.5 · 7.4 (중앙 8.9) | 51.0 · 46.4 · 47.2 · 63.5 · 39.8 (중앙 47.2) | 277 · 316 · 289 · 209 · 228 |

  두 무리 안에서 비교해도(전 8.8·9.4 / 후 7.4~11.8, 전 25.1~25.8 / 후 23.5) 느려진 모습은 없다. 다만 n=5 이고 무리별 표본이 적으며(전 2·3개 / 후 4·1개) P2·P3 는 전·후를 번갈아 재지 않았다 — 통계적으로 "같다"를 보인 것은 아니다. 맞춤 횟수(`syncs` 2,201,000)는 전·후 같다.
- P1(`RegionTickBench` 기본 장면, 800틱, 거울 없음): 실행 간 흔들림이 커서 전·후를 **번갈아** 3쌍 쟀다(기준 커밋은 별도 worktree). 틱 평균(ms) 전 2.63 · 4.43 · 4.01 / 후 3.61 · 3.33 · 3.92 — 차이는 실행 간 범위 안이다. 거울이 없는 서버는 바뀐 코드에 닿지 않는다(`publishReadViews` 는 지켜보는 청크가 없으면 바로 돌아가고, 거울 없는 청크는 null 확인에서 끝난다).
- 결론: 잡음보다 큰 저하는 없다(통과 기준 충족). 더해지는 비용은 정적 volatile 읽기 하나·단형 인터페이스 호출 하나·형 검사 하나로 수 ns 미만이고, 거울 없는 청크는 바뀐 코드에 닿지 않는다.
- 예상 비용: 정적 필드 읽기 하나와 단형(monomorphic) 인터페이스 호출 하나(JIT 인라인 대상), 형 검사 하나. 거울 없는 청크는 지금처럼 null 확인에서 끝난다.
- 기능 확인: 기존 `ChunkColumnMirrorPendingTest`·`ChunkFieldCensusTest`·NMS 거울 시험(호환 계층 N2b-4 묶음)·금 파일 그대로.

## 하지 않는 것
- 맞춤 지점의 위치·차례·D3 경고(저장 표시 없이 들여온 내용) 규칙은 바꾸지 않는다.
- 손 shim 이전(4단계, T-232)과 섞지 않는다.
