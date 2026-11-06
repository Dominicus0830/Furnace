# 6차 봇 시험 스크립트 (2026-11-06, user-todo 정리)

결과는 `docs/feature-parity/bot-connection-test-2026-11-06.md`. 상위 README 대로 서버를 띄운 뒤 이 폴더에서 `SRV=$D node <스크립트>.js` (`../round2/u.js` 를 쓴다).
평지 서버는 `enable-command-block=true`, `plugins/` 에 SamplePlugin(`furnace-plugin-test.jar`)과 `probe.jar`(상위 `probe-plugin`)를 둔다. `dolphin.js` 만 노이즈 월드(`level-type=minecraft:normal`, `level-seed=12345`).

| 파일 | 내용 |
|---|---|
| vd.js | 플러그인 `setViewDistance`·클라이언트 렌더 거리 변경이 가만히 있어도 바로 반영되는지(청크 수·반지름), `setSimulationDistance` 밖 엔티티 정지 |
| wc.js | 바람 구슬 5틱 규칙(소환 직후·1초 뒤·직접 던진 직후 때리기 → 서버 Motion) |
| mace.js | 철퇴 관통 IV(다이아 갑옷 좀비)·밀도 V·돌풍 III 내려치기 피해와 튕김 높이 |
| ev.js | 몹 장착 소리·조각 감지기(좀비·거치대·말·늑대, 햇빛 마모는 없음), 다른 플레이어 첨벙 소리, SamplePlugin 비op 깊은 곳 보호, 워든이 눈덩이 진동에 분노 |
| portal.js | 네더 포탈로 갔다 오며 도착 직후 걷기·점프 되돌림 수 |
| brew.js | 양조기로 물약을 만들어 꺼내면 발전 과제 Local Brewery |
| strider.js | 스트라이더가 차가운 땅에서 떨기(메타데이터 18)·속도 수정자, 새끼 기수도 떠는지 |
| raid.js | 일터 블록·종으로 만든 마을에서 습격 → 웨이브마다 `execute as … run damage … by <봇>` → 승리 후 축하 폭죽·주민 체력(U1) |
| dolphin.js | 바다에서 돌고래에게 대구 → TreasurePos 가 `/locate structure #minecraft:dolphin_located` 와 같은지 |
| proj.js | 투사체-블록 반응 남은 것: 삼지창(빠름·느림)·화살 → 종유석, 화살 → 종 울림, 스폰 보호(비op/op 주인 불화살 → TNT), 스켈레톤 불화살 + mobGriefing false, `/effect` 몹 대상·효과 입자, 서리 보행자 장화 마그마. **`spawn-protection=16`** 서버 |
| snd.js | 소리 id → 이름(내장 레지스트리 순서). minecraft-protocol 이 holder 의 +1 을 이미 빼 준다 |

## 함정
- 시야(view-distance 6) 밖 칸의 `/setblock`·`/fill` 은 "로드 안 됨"으로 실패한다 — 멀리 짓기 전에 봇을 먼저 `/tp` 하고 청크를 기다린다(raid.js 가 이걸로 가끔 습격이 안 났다).
- 바닥 종은 facing 축과 같은 면을 맞아야 울린다(바닐라 BellBlock.isProperHit).
- 소리는 16칸 안에서만 받는다. 멀리 소환한 몹의 장착 소리가 "없다"면 봇 위치부터 본다.
- `setup()` 전에 받은 청크는 `map_chunk` 핸들러가 못 본다 — 청크 수는 `bot.world.getColumns()` 로 시작값을 채운다.
- 이전 실행의 돌고래·주민이 남아 `@e[limit=1]`·`near()` 가 엉뚱한 개체를 집는다 — 먼저 `/kill`, 선택자엔 `sort=nearest`.
- `/setblock` 으로 침대 반쪽만 놓으면 모양 갱신으로 사라진다(바닐라와 같음). 마을 POI 는 일터 블록·종으로 만든다.
- 축하 폭죽은 머리 위가 트인 주민만 쏜다 — 유리 상자에 지붕을 씌우면 0발.
- 마을의 영웅은 플레이어가 잡아야 붙는다 — `/kill` 대신 `execute as @e[type=#minecraft:raiders] run damage @s 1000 minecraft:player_attack by <봇>`. `/damage` 는 대상 하나만 받는다.
- 철퇴 내려치기는 `bot.physicsEnabled=false` 로 tp 해서 정지 상태에서 떨어뜨려야 낙하 거리가 매번 같다. 1.21.1 은 철퇴 보너스를 더한 **뒤** 치명타 1.5 배를 곱한다(바닐라 cmx 바이트코드).
- 돌풍(바람 폭발) 뒤 봇이 땅에 박혀 되돌림이 반복되는 것은 mineflayer 가 폭발 패킷의 블록 목록을 블록 상호작용 종류와 상관없이 공기로 지우기 때문(봇 문제, 서버는 바닐라처럼 TRIGGER_BLOCK 폭발에도 목록을 보낸다).
- minecraft-data 1.21.1 의 `potion_contents` 에 1.21.2 칸(customName)이 들어 있어 성분 있는 물약이 든 창은 열리지 않는다 — `patch-registry.js` 가 고친다(`npm install` 다시).
