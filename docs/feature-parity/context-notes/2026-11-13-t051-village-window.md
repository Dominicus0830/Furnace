# T-051 주민 산책 마을 쪽 섹션 찾기 창 계산 (2026-11-13)

- 바뀐 곳: `PoiManager.closestVillageSection`(새), `VillageBoundRandomStroll.findSectionClosestToVillage`(위임), `IronGolem.getRandomVillageSection`(`isVillageCenterSection` 직접 조회).
- 옛 구현은 둘레 섹션 125개마다 `sectionsToVillage`(13×13 열 훑기 + 열 캐시 169칸 비우기)를 불렀다. 새 구현은 창(반지름+6 = 8, 17×17 열)을 한 번 읽어 점유된 마을 POI 가 있는 섹션 좌표만 모으고, 섹션마다 체비셰프 최솟값(6 넘으면 7)을 낸다.
- 결과 동일 근거: 순수 함수(난수 소비 없음). 같은 기준(올라온 열만, `hasSections`, `isVillageCenter` = 점유된 마을 POI). 창 안에 중심이 없으면 `sp` 자신(참조 동일 — `MoveBackToVillageGoal` 이 `best == sp` 참조 비교)을 돌려주는 모양도 그대로.
- 시험: `ClosestVillageSectionTest` 가 옛 함수를 오라클로 남겨 무작위 POI 배치 120판 × 위치 60 × 반지름 0~3 을 대조(마을로 가는 답 비율도 확인). 창 반지름을 1 줄이는 변이로 시험이 실패하는 것을 확인.
- 측정(RegionTickBench, 주민·좀비 400, 노이즈 지형, 800틱 중 뒤 600틱, 교대 3회, 틱 평균 ms):
  - 마을 없음(O06 최악 장면): 옛 26.6/29.2/25.4 → 새 10.1/10.2/9.4 (약 −63%)
  - 마을 있음(새 `-Dfurnace.bench.scene=village`: 36채 + 종, 티켓 73개 선점 → 마을 중심 섹션 25개): 옛 20.8/20.2/19.6 → 새 11.6/11.0/11.1 (약 −44%)
  - 마을 안 주민은 `isVillage` 가 먼저 참이라 이 길을 안 타므로 이득이 작다 — 마을 밖(둘레 2섹션 안) 주민 몫만 준다.
