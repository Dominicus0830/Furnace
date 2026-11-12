# 시야 청크 모양 — 바닐라 둥근 모양 (2026-11-12)

- **문제**: 바닐라 1.21.1 `ChunkTrackingView` 는 정사각이 아니라 둥근 모양이다. 시야 16 → 1021청크(Furnace 정사각 33×33 = 1089). 축 방향은 시야 + 1 칸까지 간다(둘레 포함). 근거: O03 보고서 C1, 바닐라 원문 `ChunkTrackingView.isWithinDistance`(extracted_minecraft_data client1.21.1).
- **원문 식**(1.21.1): `a = max(0,|dx|-1)`, `b = max(0,|dz|-1)`, `major = max(0, max(a,b) - (includeOuter ? 1 : 0))`, `minor = min(a,b)`, 안이면 `minor² + major² < viewDistance²`. `contains` = includeOuter 참(둘레 포함), `isInViewDistance` = 거짓.
- **고친 곳**: `world/ChunkTrackingView`(판정 한곳), `Region.updateChunks`(보낼 집합·내릴 집합), `Region.inView`(전송 직전 stale 검사), `ConnectionHandler` 접속 스폰 배치(spawn-chunk-radius 모서리가 시야 모양 밖이면 뺀다). 청크 구독이 시야와 같이 가므로 엔티티 추적(`chunkTrackedBy`)·빛 갱신 테두리(`onTrackedBorder`)도 같은 모양을 따른다.
- **붙잡기 집합과 보낼 집합은 다르다(PR #270 검토 R1)**: 바닐라 플레이어 티켓(`DistanceManager.PlayerTicketTracker`)은 서버 시야 안 **체비셰프 정사각** 전부를 올려 두고 틱하며, 보내는 것만 둥근 모양이다(Paper 1.21.1 Moonrise 도 로드·틱 정사각, 보내기만 원). Furnace 는 청크 수명이 구독에 묶여 있어서 구독을 둥글게 줄이자 정사각 모서리(시야 10 에서 32칸)가 로드되지 않았다. 지금은 모서리를 **구독 없이** 올린다: `Region.collectCornerLoads`(정사각 ∖ 둥근 모양 중 안 올라온 것, 가까운 것부터)·`loadCornerChunks`(보낼 청크가 다 나간 뒤, 동시 진행 상한의 4분의 1 안에서)·`evictIdleChunks` 가 `heldByPlayerTicket`(서버 시야 정사각)이면 안 내린다. 구독이 없으니 블록 변경 방송·`chunkReady`·빛 경계·엔티티 추적에는 끼지 않는다.
- **시야는 2 이상**: 바닐라 `getPlayerViewDistance` 는 clamp(요청, 2, 서버). `Region.trackingDistance` 로 `updateChunks`·`inView` 가 같이 쓴다. 시험 월드도 시야 2 로 옮겼다(시야 1 = 21칸은 바닐라에 없다).
- **그대로인 것**: 엔티티 틱·자연 스폰·블록 틱 판정 코드(`ticketPlayerWithin`·`inBlockTickingRange`). 바닐라가 시야 + 1(BLOCK_TICKING)·+ 2(FULL)줄까지 로드하는 것은 이 PR 범위 밖(전부터 보낼 집합까지만 로드했다). 전송 속도(auto 배치·클라이언트 속도 따르기)도 손대지 않았다 — 보낼 집합만 줄었다(시야 16: −6.2%).
- **시험**: `ChunkTrackingViewTest` — 바닐라 원문 식을 그대로 옮긴 기준 구현과 모든 칸·시야 2~32·먼 중심에서 대조, 시야별 개수 표(45/109/201/313/453/609/1021/2125/3653), 실제 서버 접속·이동·시야 변경에서 보낸/내린 청크 집합이 바닐라 `difference` 와 같은지.
- **정사각을 가정하던 시험**은 `ChunkTrackingViewTest.vanillaCount` 로 바꿨다(시야 1 → 21, 4 → 109).
- `ChunkCornerLoadTest`: 시야 6 에서 보낸 것 = 둥근 모양, 정사각은 전부 로드, 모서리는 구독 없음·엔티티 틱 대상, 한 칸 이동 뒤에도 정사각 전부 로드.
- 함정: 시야 1 은 서버 설정 하한(2) 아래라 시험에만 나왔다(둥근 모양 21칸). 시험은 시야 2(45칸)로 옮겼다.
