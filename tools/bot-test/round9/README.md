# 9차 봇 시험 (2026-11-06, 행운·불운 전리품)

`luck.js`: 월드 데이터 팩 `lt`(test:t 표 = 돌 weight 100 / 흙 weight 100 quality 100)를 깔고
행운 효과 0 / 행운 / 불운 / 행운 포션 상태에서 상자(LootTable)를 열어 흙 비율을 센다(기대: 불운 0%, 보통 ≈50%, 행운 ≈67%).
이어서 `DeathLootTable` 몹을 플레이어 피해로 죽여 같은 식으로 센다.
사용: `SRV=$D node round9/luck.js` (상위 README 의 서버 준비 후, 서버 폴더에 `world/datapacks/lt` 는 스크립트가 만든다 — 서버를 띄우기 *전에* `node round9/mkpack.js $D`).
