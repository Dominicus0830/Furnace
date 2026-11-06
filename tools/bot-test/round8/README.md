# 8차 봇 시험 (2026-11-06, 네더 포탈 좀비 피글린)

`portal-piglin.js`: 오버월드 지옥문 10개를 세우고 `randomTickSpeed 4096` 으로 15초 틱 → 결과 (쉬움 8 / 어려움 17 / 평화 0 / doMobSpawning=false 0),
몹은 흑요석 바닥 위 칸에 생기고 손에 황금 검, `PortalCooldown` 300. `n.js` 방식으로 네더의 지옥문은 0마리(오버월드 포탈을 지운 뒤 확인).
확률 시험이라 포탈 여러 개 + 긴 시간이 필요하다(포탈 한 개 6초는 0마리가 흔하다).
함정: 지옥문 바닥을 유리로 바꾸면 틀이 깨져 포탈이 사라진다 — "스폰 불가 바닥"은 시험할 수 없다.

## region 경계 차이

- `siege.js [밤 수]`: 봇 둘(Alpha 먼저, Bravo 나중)을 3000칸 떨어뜨려(서로 다른 region) 각자 종 격자(16칸 간격 7×7)와 deep_dark 바이옴(자연 몬스터 스폰 없음) 안에 둔다.
  밤마다 `time set 6000`(포위 상태 초기화) → 좀비 제거 → `time set 17990` → 5.5초 뒤 각 봇 56칸 안 좀비 수를 콘솔 `execute if entity` 로 센다.
  바닐라 기대: 포위는 밤마다 한 번 굴림(10%), 둘 다 마을 안이면 먼저 들어온 Alpha 쪽에만.
- `parrot.js`: 길들인 앵무새를 봇 자리로 계속 tp 해 어깨에 앉힌 뒤(100틱 쿨다운), 콘솔 `/gamemode spectator`·`/damage` 로 내려놓기.
- 함정: 같은 밤 안에서 시간을 되돌리면 포위 상태(hasSetupSiege)가 남는다 — 사이에 낮을 한 틱 지나게 한다. mineflayer 의 `declare_recipes` PartialReadError 는 무시.
- `cmdchain.js [횟수]`: `enable-command-block=true`. 임펄스 + 체인 5칸, 첫 칸·끝 칸이 게임 시각을 점수(s0·s4)로, 중간 칸은 3000칸 떨어진 다른 봇(다른 region) 옆 돼지를 `@e` 로 찾는다. 레드스톤 블록을 다시 놓아 횟수만큼 돌리고 s4 - s0 을 본다(바닐라 0).
