# 오프라인 서버 봇 접속 시험

실제 마인크래프트 클라이언트 없이 Furnace 를 `online-mode=false` 로 띄우고 [mineflayer](https://github.com/PrismarineJS/mineflayer) 봇(프로토콜 767 = 1.21.1)으로 접속해
명령·패킷·채팅/액션바 메시지를 확인한다. 결과 기록은 `docs/feature-parity/bot-connection-test-2026-11-03.md`.

## 준비
1. 서버 jar: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew -PskipOracle=true :furnace-server:fatJar :furnace-plugin-test:jar`
2. 바닐라 데이터: 서버는 첫 실행 때 Mojang 에서 바닐라 jar 를 받는데, 클라우드 컨테이너에서는 막힌다(403).
   `main` 브랜치의 `tools/vanilla-data/server-1.21.1/` 에 같은 내용이 있으므로(커밋 금지 데이터라 기준 브랜치에는 없다)
   ```
   git archive main tools/vanilla-data/server-1.21.1/data tools/vanilla-data/server-1.21.1/version.json | tar -x -C <작업폴더>
   mkdir <작업폴더>/vanilla-data-1.21.1 && cp -r <작업폴더>/tools/vanilla-data/server-1.21.1/{data,version.json} <작업폴더>/vanilla-data-1.21.1/
   echo -n 59353fb40c36d304f2035d51e7d6e6baa98dc05c > <작업폴더>/vanilla-data-1.21.1/.complete
   ```
   데이터 없이 띄우면 아이템 레지스트리가 없어 청크 로드가 모두 실패한다.
3. 봇: 이 폴더에서 `npm install` (postinstall 이 `patch-registry.js` 로 prismarine-registry 를 고친다 — Furnace 가 레지스트리 내용을 늘 생략하기 때문).

## 실행
```
export BOT_ROOT=<작업폴더> VANILLA_DATA=<작업폴더>/vanilla-data-1.21.1
D=$(srv/newsrv.sh pr5)              # 평지·오프라인 새 월드
srv/run.sh <furnace-server.jar> $D && srv/waitup.sh $D
SRV=$D node pr5.js 1                # SRV 의 cmds 파일로 콘솔 명령(op 등)을 보낸다
srv/stop.sh $D
```
- `pr2.js` lz4·속성(1 → 재시작 → 2), `pr3*.js` 스트라이더, `pr4*.js` RCON·채널·소리·시야 거리(`probe-plugin` 필요, `enable-rcon=true rcon.password=testpw`),
  `pr5*.js` 플러그인 API 2단계(SamplePlugin, 1 → 재시작 → 2), `pr7.js` 펫 낙하 메시지, `pr8.js <차원> <틱>` 특수 스포너, `base1.js`·`base2.js` 기존 항목.
- `round4/ev.js` 플러그인 API 3단계 이벤트 33항목(시험 플러그인 `round4/probe` 를 `plugins/` 에 넣고 띄운다: `javac -cp furnace-api.jar -d out round4/probe/EventProbe.java && cp round4/probe/furnace-plugin.yml out/ && (cd out && jar cf ../probe.jar .)`).
- `round5/ev.js` 플러그인 API 3단계 둘째 묶음(블록·인벤토리 이벤트) 31항목 — 시험 플러그인 `round5/probe`(BlockEventProbe, `/evcancel`·`/evmode`)를 위와 같은 방법으로 묶어 넣는다.
- `round12/registries.js` 데이터 주도 레지스트리(D2) 8항목 — 서버 폴더를 만든 뒤 `round12/pack` 을 `<서버>/world/datapacks/d2` 로 복사하고 띄운다. 늑대 변종 번호 0(`d2:aaa`)의 엔티티 메타데이터는 mineflayer 가 holder(0=인라인)로 잘못 읽어 PartialReadError 를 찍지만 접속·판정에는 영향 없다(바닐라 1.21.1 은 `holderRegistry` = 번호만, 기존 ashen 도 같다).
- `round10/ui.js` 플러그인 UI API(메뉴·보스바·사이드바) 10항목 — `round10/README.md`.
- `round-w2/glow.js` 플러그인 바이옴·차원 타입 등록(W2) 11항목 — `round-w2/README.md`(시험 플러그인 `round-w2/probe`).
- `round13/brain.js` 두뇌 API(E2) 12항목 — `round13/README.md`(시험 플러그인 `round13/probe`).
- `round15/e3.js` 스폰 정책·엔티티 수·자세·평판·POI API(E3) 15항목 + `MODE=npcs` 1항목 — `round15/README.md`(시험 플러그인 `round15/probe`).
- `round11/virtual.js` 가상 엔티티·NPC·가짜 탭 줄(K1) 11항목 — `round11/README.md`.
- `round8/` 플러그인 API 3단계 셋째 묶음 이벤트(`ev.js` + `probe/Stage3cProbe`, 구역 form enchant anvil brew vehicle fish portal book spawn).
- `round6/` user-todo 정리용 6차(시야 거리·바람 구슬·철퇴·장착 소리·포탈·양조·스트라이더·습격·돌고래·투사체) — `round6/README.md`.
- `round7/conduit.js` 전달체(conduit) 활성화·전달체의 힘·적대 몹 공격 — `round7/README.md`.
- `round14/cause.js` 원인 사슬(B4) 11항목 — 시험 플러그인 `round14/probe`(CauseProbe, 원인을 담는 이벤트의 사슬을 `[CAUSE]` 로그로)를 같은 방법으로 묶어 넣는다.
- `round9/damage.js` 피해 계산 단계 이벤트 8항목 — 시험 플러그인 `round9/probe`(DamageProbe, `/dmgmode`)를 같은 방법으로 묶어 넣는다. 체력 비교는 `naturalRegeneration` 을 끄고.
- `entity/entity.js` 엔티티 AI·틱 묶음(빈 종 마을 판정·좀비 표적·울타리 돌기, `perf` 인자면 소 600마리 `/tick query`) — `docs/feature-parity/perf-entity-2026-11-06.md`.
- `probe-plugin`: `javac -cp furnace-api.jar -d out probe/ProbePlugin.java && cp furnace-plugin.yml out/ && (cd out && jar cf ../probe.jar .)`

## 한계
- 봇은 클라이언트 물리를 흉내 낼 뿐이라 이동 검증·탈것 조종(스트라이더·돼지는 클라이언트가 움직임을 계산)·화면 표시·소리 재생은 확인할 수 없다.
- mineflayer 는 `declare_recipes` 를 읽지 못한다(PartialReadError, 무시해도 접속은 유지). 서버 쪽 문제인지는 확인하지 않았다.
- `srv/*.sh` 는 서버를 한 번에 하나만 띄운다고 가정한다(`^java -Xmx3G` 로 찾음).
