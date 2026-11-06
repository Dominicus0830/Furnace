# 7차 봇 시험 (2026-11-06, 전달체)

상위 README 대로 평지 서버를 띄운 뒤 이 폴더에서 `SRV=$D node conduit.js`.

`conduit.js`: 유리 상자 안을 물로 채우고 (8,-50,8)에 전달체를 놓은 뒤
1. 틀 15개 → 효과 없음, 16개 → 봇에게 `conduit_power`(duration ≤260, ambient)
2. 틀 42개 + `NoAI` 익사체 → 40틱마다 체력 4씩 줄고 블록 데이터에 `Target` UUID
3. 틀 하나를 물로 바꿔 41개 → 공격 멈춤
4. 소리: activate·ambient·ambient.short·attack.target

## 함정
- 봇을 물 채우기 전에 공중으로 tp 하면 땅까지 떨어져 물 밖에 있게 된다 — 물을 먼저 채운다.
