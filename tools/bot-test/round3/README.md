# 3차 봇 시험 스크립트 (2026-11-04)

결과는 `docs/feature-parity/bot-connection-test-2026-11-04.md`. 상위 README 대로 평지 서버를 띄운 뒤 이 폴더에서 `SRV=$D node <스크립트>.js` (`../round2/u.js` 를 쓴다).

| 파일 | 내용 |
|---|---|
| mv.js | 이동 서버 검증 20가지 상황 + 대조군 2개. 상황마다 서버 되돌림 수와 로그 경고 수를 `RESULT` 로 찍는다 |
| u24.js | 겉날개 폭죽 비행(되돌림), 말 재탑승(초기판), 파괴수 나뭇잎 앞면 |
| horse.js | U4: `set_passengers` 로 내린 시각과 다시 탄 시각 |
| rav.js | U2: 나뭇잎 상자에 가둔 파괴수가 20초에 부순 수. `LEAVES=cherry_leaves/oak_leaves` 로 종류를 고른다 |

- 다른 스크립트가 난이도를 쉬움으로 두면 평지 슬라임에 봇이 죽어 결과가 섞인다. `horse.js` 는 평화로 시작한다.
