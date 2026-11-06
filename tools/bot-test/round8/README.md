# 8차 봇 시험(2026-11-06): region 경계 차이

- `siege.js [밤 수]`: 봇 둘(Alpha 먼저, Bravo 나중)을 3000칸 떨어뜨려(서로 다른 region) 각자 종 격자(16칸 간격 7×7)와 deep_dark 바이옴(자연 몬스터 스폰 없음) 안에 둔다.
  밤마다 `time set 6000`(포위 상태 초기화) → 좀비 제거 → `time set 17990` → 5.5초 뒤 각 봇 56칸 안 좀비 수를 콘솔 `execute if entity` 로 센다.
  바닐라 기대: 포위는 밤마다 한 번 굴림(10%), 둘 다 마을 안이면 먼저 들어온 Alpha 쪽에만.
- `parrot.js`: 길들인 앵무새를 봇 자리로 계속 tp 해 어깨에 앉힌 뒤(100틱 쿨다운), 콘솔 `/gamemode spectator`·`/damage` 로 내려놓기.
- 함정: 같은 밤 안에서 시간을 되돌리면 포위 상태(hasSetupSiege)가 남는다 — 사이에 낮을 한 틱 지나게 한다. mineflayer 의 `declare_recipes` PartialReadError 는 무시.
