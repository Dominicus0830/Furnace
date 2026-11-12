# 시야 청크 모양 — 바닐라 둥근 모양 (2026-11-12)

- **문제**: 바닐라 1.21.1 `ChunkTrackingView` 는 정사각이 아니라 둥근 모양이다. 시야 16 → 1021청크(Furnace 정사각 33×33 = 1089). 축 방향은 시야 + 1 칸까지 간다(둘레 포함). 근거: O03 보고서 C1, 바닐라 원문 `ChunkTrackingView.isWithinDistance`(extracted_minecraft_data client1.21.1).
- **원문 식**(1.21.1): `a = max(0,|dx|-1)`, `b = max(0,|dz|-1)`, `major = max(0, max(a,b) - (includeOuter ? 1 : 0))`, `minor = min(a,b)`, 안이면 `minor² + major² < viewDistance²`. `contains` = includeOuter 참(둘레 포함), `isInViewDistance` = 거짓.
- **고친 곳**: `world/ChunkTrackingView`(판정 한곳), `Region.updateChunks`(보낼 집합·내릴 집합), `Region.inView`(전송 직전 stale 검사), `ConnectionHandler` 접속 스폰 배치(spawn-chunk-radius 모서리가 시야 모양 밖이면 뺀다). 청크 구독이 시야와 같이 가므로 엔티티 추적(`chunkTrackedBy`)·빛 갱신 테두리(`onTrackedBorder`)도 같은 모양을 따른다.
- **그대로인 것**: 로드 티켓·스폰·틱 범위. 바닐라 플레이어 티켓은 처음부터 정사각(체비셰프 ≤ 서버 시야, 청크 단계 FULL 은 + 2 칸)이라 이미 `Region.ticketPlayerWithin`·`inBlockTickingRange` 가 맞다. 시야 + 1 줄 청크는 FULL 로 올라오지만 엔티티 틱·자연 스폰 대상이 아니다(`naturalSpawningAllowed` 는 시야 ≤ vd). 전송 속도(auto 배치·클라이언트 속도 따르기)는 손대지 않았다 — 보낼 집합만 줄었다(시야 16: −6.2%).
- **시험**: `ChunkTrackingViewTest` — 바닐라 원문 식을 그대로 옮긴 기준 구현과 모든 칸·시야 2~32·먼 중심에서 대조, 시야별 개수 표(45/109/201/313/453/609/1021/2125/3653), 실제 서버 접속·이동·시야 변경에서 보낸/내린 청크 집합이 바닐라 `difference` 와 같은지.
- **정사각을 가정하던 시험**은 `ChunkTrackingViewTest.vanillaCount` 로 바꿨다(시야 1 → 21, 4 → 109).
- 함정: 시야 1 은 서버 설정 하한(2) 아래라 시험에만 나온다. 시야 1 둥근 모양은 21칸(3×3 아님).
