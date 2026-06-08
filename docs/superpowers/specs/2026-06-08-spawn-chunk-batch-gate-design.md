# 스폰 청크 배치 게이트 — 설계

**목표:** 접속(특히 재접속) 시 플레이어가 발밑 청크 도착 전 자유낙하해 1칸 꺼져 보이는 현상 제거.

**상태:** M4b 영속성 완료 후 T12-A verify서 발견된 잔여 이슈. 서버 저장/전송 좌표는 정확(−60.0 검증). 시각적 낙하만 문제.

## 문제

`ConnectionHandler.onEnterPlay`는 플레이어 위치 + `GameEvent(START_WAITING_FOR_CHUNKS)`를 보낸 뒤 플레이어를 region에 adopt한다. 청크는 다음 region 틱부터 `Region.updateChunks`→`streamLoads`에서 **비동기**로 스트림된다(IO풀 .mca 로드 → chunkPool 직렬화 → 전송).

현 스트림 경로는 청크를 `ChunkBatchStart`/`ChunkBatchFinished`로 감싸지 **않는다**(두 패킷은 M2 조인플로우 잔재로 정의만 돼 있고 미사용). 클라이언트는 START_WAITING_FOR_CHUNKS로 로딩 대기에 들어가지만, 경계 지어진 배치 신호를 못 받아 일부 청크 수신 후 대기를 끝내고 물리를 적용한다. 발밑 청크가 아직 안 왔으면 잠깐 자유낙하한다. 재접속은 디스크 `.mca` 디코드가 generate보다 느려 갭이 커서 낙하가 눈에 띈다.

## 접근 (vanilla 배치 게이트)

청크를 `ChunkBatchStart` … 청크들 … `ChunkBatchFinished(count)`로 감싼다. 클라이언트는 배치 경계를 받아 첫 배치(스폰 뷰)가 도착할 때까지 로딩화면서 **물리를 정지**한다. 자유낙하 틱이 0이므로 낙하 자체가 발생하지 않는다. 디코드 지연과 무관하게 클라가 알아서 대기한다.

대안 비교(기각):
- **스폰 청크만 동기 선처리** — onEnterPlay가 Netty IO 스레드라 디스크 디코드 동기 await 시 IO 스레드 블로킹(타 커넥션 지연). 성능·안정성 손해.
- **위치 재전송 스냅** — 떨어진 뒤 텔레포트로 올림. 시각적 깜빡/저크 + 완료감지 레이스. 기능·안정성 손해.

배치 게이트는 성능(블로킹 0)·기능(완전 로드 후 스폰)·안정성(지연 무관 대기) 어느 것도 희생 안 함.

## 변경

`Region.streamLoads` 한 메서드만 수정. 신규 파일 없음. 기존 `ChunkBatchStart`/`ChunkBatchFinished` 패킷 재사용.

```
streamLoads(Player p, List<ChunkPos> toLoad):
    if toLoad.isEmpty(): return            // 빈 배치 스킵
    p.send(new ChunkBatchStart())
    List<CompletableFuture<?>> sends = []
    for cp in toLoad:
        p.loadedChunks().add(cp)
        world.subscribeChunk(cp, p)
        CompletableFuture<?> f =
            world.snapshotChunk(cp)
                .thenCompose(snap -> chunkPool.submit(serialize body))
                .thenAccept(body -> p.send(new LevelChunkWithLight(body)))
                .exceptionally(ex -> { log warn; return null; })   // 실패해도 배치 안 막음
        sends.add(f)
    CompletableFuture.allOf(sends).thenRun(() ->
        p.send(new ChunkBatchFinished(toLoad.size())))
```

## 설계 근거

- **순서 보장:** `Player.send`는 `channel.eventLoop().execute`로 제출 순서대로 마샬한다. `ChunkBatchStart`가 먼저 제출되고, 각 청크 전송은 비동기 완료 시점에(Start 이후) 제출되며, `ChunkBatchFinished`는 `allOf` 완료 후(모든 청크 전송 제출 후) 제출된다 → 와이어 순서 Start → 청크들 → Finished 유지.
- **행(hang) 방지:** 청크 로드/직렬화 실패가 `allOf`를 예외 완료시키면 `thenRun`이 안 돌아 `ChunkBatchFinished`가 안 가고 클라가 영원히 로딩에 갇힌다. per-청크 `exceptionally`로 에러를 삼켜 `allOf`가 항상 정상 완료되게 한다. 실패 청크는 누락(공허)되지만 배치는 닫힌다.
- **count 근사:** `ChunkBatchFinished`의 count는 클라 스로틀링 추정용이라 `toLoad.size()`(실패 포함)면 충분.
- **첫 배치 = 스폰 게이트:** adopt 후 첫 region 틱서 `lastCenter==null`이라 full-view가 한 번에 toLoad → 한 배치. 클라가 이걸 기다린다. 이동 시 로드 셋도 동일하게 배치로 감싸 vanilla 스로틀링과 일치.

## 테스트

`EmbeddedChannel` 통합 테스트: 플레이어 adopt → region 틱 → async 풀(IO/chunk/scheduler) 완료까지 구동 + `runPendingTasks` → outbound 메시지에서 `ChunkBatchStart`가 첫 청크 앞, `ChunkBatchFinished`가 마지막에 오고 count == 로드 청크 수인지 검증. 기존 `ChunkSubscriberTest`의 World+EmbeddedChannel 패턴 참고.

## 범위 밖

- 시뮬레이션 거리/청크 스로틀링 적응(클라 Chunk Batch Received 응답 반영) — 현재처럼 무시.
- 실패 청크 재시도 — 누락 허용.
