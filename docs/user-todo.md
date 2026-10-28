# 사용자 확인·결정 필요 항목 (대화 걸면 알려 줄 것)

2026-10-28 정리. 잘 되는 것으로 확인된 줄은 지웠다. 버그 상세와 원인 조사는 `docs/feature-parity/user-test-bugs-2026-10-27.md`.
아래 **A. 접속 시험**은 분류별로 "어떻게 / 기대 결과"를 적었다. 복붙할 명령은 코드로 표시. 시험 전에 `/gamemode creative`, `/gamerule doDaylightCycle false` 정도는 필요에 따라 쓰면 된다. 결과가 기대와 다르면 분류 이름과 항목 번호만 알려 주면 된다.

# A. 접속 시험 (수정·구현했지만 접속 확인 못 함)

## A1. 두 명이 접속해야 보이는 것 (외형·패킷)
클라이언트 2개(또는 친구 1명) 필요.
1. 손·방어구 보이기: 한 명이 `/give @s diamond_sword` `/give @s diamond_chestplate` `/give @s diamond_helmet` 후 장착. 다른 쪽에서 손 아이템과 방어구가 보여야 함. 그 뒤 `/kill @s` 로 죽고 리스폰해도 다시 보여야 함.
2. 스킨 겉옷·겉머리: 클라이언트 설정(스킨 사용자 지정)에서 레이어를 켠 쪽을 상대가 볼 때 겉옷·모자 레이어가 보여야 함.
3. 웅크림·활 당김 포즈, 삼지창 급류(물/비 속): `/give @s trident{}`은 쓰지 말고 `/give @s minecraft:trident[minecraft:enchantments={levels:{"minecraft:riptide":3}}]` 후 물속에서 사용.
4. 방패: `/give @s shield` 를 들고 우클릭으로 막은 채 좀비(`/summon zombie ~2 ~ ~`)에게 맞는다. 막은 쪽이 **밀리지 않아야** 함. 도끼 든 좀비(`/summon zombie ~2 ~ ~ {HandItems:[{id:"minecraft:iron_axe",count:1},{}]}`)는 방패를 비활성화함.

## A2. 차원·이동
1. 텔레포트 높이: `/tp @s ~ ~ ~` 해도 F3 의 Y 가 변하지 않아야 함. 네더로: `/execute in minecraft:the_nether run tp @s 0 100 0` → F3 Y 가 100 이고 `Dimension` 이 네더여야 함. 돌아오기: `/execute in minecraft:overworld run tp @s 0 100 0`.
2. 포탈: 흑요석 포탈을 만들어 들어갔다 같은 문으로 돌아올 때 Y 가 한 칸 아래로 끼는지(F3 의 Y 와 서버 로그 비교) 확인.
3. 관전 모드는 포탈 못 탐: `/gamemode spectator` 상태로 포탈 안에 서 있어도 이동되지 않아야 함.
4. 자는 중 텔레포트 깨우기는 미구현이라 시험 불가(아래 알려진 차이).

## A3. 마을·주민·종·침대·지도
1. 마을 찾기: `/locate structure minecraft:village_plains` → 클릭한 좌표로 `/tp @s <x> ~ <z>`. 새 월드 또는 `region/` 삭제 후 처음 생성한 마을일수록 좋음.
2. 마을에 주민·철 골렘·고양이가 처음부터 있는지, 침대·직업 블록이 보이는지, 주민이 문을 열고 일하는지(낮에 직업 블록 주변을 오가는지).
3. 종: 마을 종을 우클릭 → 몸통 모델이 보이고 소리가 나야 함. 주민은 숨으러 침대로 감(침대가 정상이어야 함). 습격자 발광: `/summon pillager ~5 ~ ~` 근처에서 종을 치면 발광.
4. 습격 시작: `/effect give @s minecraft:bad_omen 1000 0` 후 마을로 걸어 들어가기(바닐라는 "나쁜 징조" 상태여야 시작).
5. 지도 깃발: `/give @s map` (주먹으로 우클릭해 활성화) 후 `/give @s white_banner` 를 땅에 설치하고 지도를 든 채 깃발에 우클릭 → 지도에 표식이 생기고 **계속 유지**돼야 함(예전엔 0.3초 뒤 사라짐).

## A4. 몹
1. 말: `/summon horse ~ ~ ~` 를 5~10회 반복해 `/data get entity @e[type=horse,limit=1,sort=nearest] Health` 값이 15~30 범위인지. 탑승: 우클릭으로 안장 없이 타지지 않아도 정상, 안장: `/give @s saddle` 후 말에 우클릭 → 탑승. 내린 뒤 약 3초 안엔 못 타는 것도 바닐라와 같음.
2. 말 장비 창: 길들인 말에 웅크려 우클릭 → 안장/갑옷 칸. 당나귀 상자 창.
3. 워든: `/summon warden ~ ~ ~` → 땅속으로 들어가지 않고 서 있어야 함. 소리 확인, 웅크려 걸으면 감지 못 하는지, 양털로 진동이 막히는지.
4. 토끼: `/summon rabbit ~ ~ ~` 와 사막/설원 바이옴에서 자연 스폰 시 서버 로그에 NPE 가 없어야 함.
5. 고양이·마을 스폰, 벌·벌집: `/summon bee ~ ~ ~` 후 벌집/꿀병/가위 사용, 벌에 쏘이면 독, 밤에 벌이 벌집으로 들어가는지.
6. 스컬크: `/give @s sculk_catalyst` 를 놓고 그 근처에서 좀비·스켈레톤(`/summon zombie ~ ~ ~`)을 `/kill @e[type=zombie,distance=..5]` 로 죽임 → 스컬크가 퍼지는지(소 같은 경험치 적은 몹이면 1~3칸만 생기는 게 정상).
7. 약탈자 순찰: 시간 이동(`/time set`)이 아니라 실제 틱이 흐르는 상태로 낮에 대기. 순찰대는 낮에 약 10분마다 한 번 판정. 자주 보려면 `/gamerule doPatrolSpawning true`(기본) 확인.

## A5. 엔더 드래곤
1. 엔드로: `/execute in minecraft:the_end run tp @s 0 70 0`.
2. 화살로 날개·머리 맞추기: `/give @s bow` `/give @s arrow 64` → 드래곤이 가까이 올 때 머리 명중(큰 피해)과 날개 명중(작은 피해)이 구분되는지, 화살이 몸통을 **통과하지 않는지**.
3. 폭발: 침대 폭발(`/give @s red_bed`, 엔드에서 사용) 또는 TNT(`/give @s tnt` 후 불)로 드래곤에 피해.
4. 부활: 포탈 가운데 받침에 `/give @s end_crystal 4` 로 4방향 크리스탈을 놓으면 부활 연출이 시작되는지, 처치 후 출구 포털과 알이 생기는지.
5. 보스 바 사용자 이름은 미구현(알려진 차이).

## A6. 블록·메뉴·용기
1. 제작기: `/give @s crafter` 를 놓고 우클릭 → 메뉴가 열림. 칸을 클릭해 잠금(×)이 토글되고, 레드스톤 신호(`/give @s redstone_block` 를 옆에 붙이거나 레버)로 제작되는지.
2. 주크박스+깔때기: `/give @s jukebox` `/give @s hopper` `/give @s music_disc_cat` → 주크박스 위에 깔때기를 놓고 음반을 넣었을 때 **재생이 끝나기 전엔 음반이 빠지지 않는지**.
3. 메뉴 열기: 화로에 재료를 넣어 굽는 도중, 깔때기가 연결된 상자를 열었을 때 첫 화면에 내용이 한 번에 정상으로 보이는지(깜빡임·잘못된 개수 없어야 함).
4. 표지판: `/give @s oak_sign` 에 텍스트 입력 후 저장/재접속해도 유지.
5. 사막 신전: `/locate structure minecraft:desert_pyramid` 후 상자 4개(새 월드젠 기준).

## A7. 발전과제
먼저 `/advancement revoke @s everything` 로 초기화한 뒤 `L` 화면에서 확인.
1. 몹 처치: `/summon zombie ~2 ~ ~` 를 때려 죽임 → "Monster Hunter".
2. 번식: `/summon cow ~2 ~ ~` 두 마리 + `/give @s wheat 4` 로 먹여 번식 → "The Parent of Parents".
3. 길들이기: `/summon wolf ~2 ~ ~` + `/give @s bone 10` → "Best Friends Forever". 말은 맨손으로 반복 우클릭해 길들이기.
4. 음식 섭취(consume_item): `/give @s golden_apple` 먹기 → "Zombie Doctor" 같은 트리거 대신 사과/물약 섭취 조건 발전과제.
5. 다른 region(멀리)에서의 처치는 최대 한 틱 늦게 반영될 수 있음(의도한 차이).

## A8. 새 명령
1. `/version`, `/plugins`
2. `/place feature minecraft:oak ~ ~ ~` / `/place structure minecraft:igloo ~ ~ ~`
3. `/trigger`: `/scoreboard objectives add tr trigger` → `/scoreboard players enable @s tr` → `/trigger tr add 5` → `/scoreboard players get @s tr` 가 5.
4. `/teammsg`: `/team add red` → `/team join red @s` → `/teammsg hello` (같은 팀원에게만).
5. `/kill`: `/kill @s`, `/kill @e[type=zombie]` 메시지·수.
6. `/tick`: `/tick query`, `/tick freeze`, `/tick unfreeze`, `/tick step 20`, `/tick rate 40`(→ `/tick rate 20` 복구), `/tick sprint 200` 후 완료 메시지(여러 차원에 플레이어를 두고도 시험).
7. `/locate`: `/locate structure minecraft:village_plains`, (병합 후) `/locate biome minecraft:desert`, `/locate poi minecraft:librarian`.
8. 다차원 이동 명령: `/execute in minecraft:the_nether run tp @s 0 100 0`.

## A9. /execute·함수·/schedule·매크로
설계 대비 차이와 의도한 한계: `docs/feature-parity/execute-function-design-2026-10-27.md` 맨 앞 요약.
- 점수 저장: `/scoreboard objectives add t dummy` 후 `/execute as @e[type=cow] store result score @s t run data get entity @s Health` → `/scoreboard players list @e[type=cow,limit=1]` 에 체력이 있는지(소가 여러 마리면 각자 저장)
- 조건: `/execute if entity @e[type=cow,distance=..10] run say 소 있음`, `/execute unless block ~ ~-1 ~ air run say 발밑 블록`, `/execute if score @s t matches 10.. run say 큼`
- 데이터 저장: `/execute store result storage t:x v int 1 run data get entity @e[type=cow,limit=1] Health` 뒤 `/data get storage t:x v`
- 함수 데이터팩: `world/datapacks/test/pack.mcmeta`(pack_format 48) + `data/t/function/a.mcfunction`(예: `say hi` / `scoreboard players add #n t 1`) 를 만들고 `/reload` → `/function t:a` 가 실행되고 결과 메시지가 나오는지. `data/minecraft/tags/function/tick.json`(`{"values":["t:a"]}`)이면 매 틱, `load.json` 이면 `/reload` 뒤 한 번 실행되는지
- 예약: `/schedule function t:a 5s` 가 5초 뒤 실행, `/schedule function t:a 5s append`, `/schedule clear t:a`, 서버 재시작 뒤에도 남아 있는지
- 매크로: 함수 파일 줄이 `$say $(msg)` 일 때 `/function t:m {msg:"hello"}` 와 `/function t:m with storage t:x` 로 치환되는지, 인자 누락 오류 메시지
- 주의: 다른 region(멀리 떨어진 곳)의 엔티티를 오가는 긴 함수는 몇 틱 걸릴 수 있음(의도한 차이). 오래 걸리면 서버 로그의 100틱 경고를 알려 주세요.
- 알려진 구멍(감사 2026-10-28): 선택자 옵션 `nbt=` `advancements=` `predicate=` `level=` 는 아직 동작하지 않음(항상 거짓/레벨 0). 이 옵션으로 시험하면 실패하는 게 정상이고 수정 작업 예정.

## A10. 저장·성능 체감
1. 저장 유지: 탐험한 뒤 서버를 정상 종료/재시작해도 같은 지형이 나오는지(생성만 한 청크도 저장됨 — 디스크 사용량 증가).
2. 2명 이상 접속해 빠르게 이동할 때 청크 전송 지연이 1명일 때와 큰 차이가 없는지(체감).
3. 서버 로그에 `100틱` 경고, `Can't keep up` 계열 메시지가 없는지.

# B. 결정 필요·재현 정보 필요 (아래 섹션)

## 결정 필요: sync-chunk-writes (2026-10-28)
- 이제 server.properties `sync-chunk-writes`(기본 true, 바닐라와 같음)가 실제로 적용된다. 켜면 청크 저장마다 디스크 동기화를 해서 전원이 나가도 저장된 청크가 깨지지 않지만, 저장 처리량이 약 17배 떨어진다(측정 약 21,000 → 1,200 청크/초, `docs/feature-parity/f05-sector-allocator-2026-10-28.md` 6장).
- `false` 로 끄면 빨라지지만 **전원 차단·OS 크래시 때 청크가 손상될 위험**이 있다(서버 프로세스만 죽는 경우는 안전). 기본값을 바꿀지, 그대로 둘지 정해 주세요.

## 아직 원인을 못 찾은 버그 (실접속 정보가 필요함)
- 투명 아이템(히트박스만 보임): 어떤 아이템이고 어떻게 생겼는지(몹이 죽어 드롭, 상자, 던짐, 합쳐짐 등) 알려 주세요
- 포탈·순간이동 후 Y 가 한 칸 아래로 도착(포탈 프레임에 낌): 서버가 보내는 y 는 요청값 그대로라 클라이언트 쪽 가능성도 있음. 도착할 때 서버 로그의 y 와 클라이언트 F3 의 y 를 비교
- 주크박스 위 깔때기가 음반을 넣자마자 가져가는데 노래가 안 끊김: 서버 경로로는 재현 안 됨(깔때기가 음반을 가져가면 바닐라는 재생이 멈춘다). 음반이 사라지고 노래만 남는지 확인
- 약탈자 순찰이 안 나옴: 게임 시간 145일을 `/time set`·`/time add` 로 넘기셨나요? 실제 틱이 흐르지 않으면 판정이 거의 돌지 않음. 10~11분마다 낮에 한 번 판정해 그중 1/5, 마을 근처에서는 안 나옴
- 스컬크가 퍼지지 않고 가끔 위로 한 칸: 촉매 근처에서 소처럼 경험치가 적은 몹이면 바닐라도 1~3칸만 생김. 좀비·스켈레톤 등으로 확인
- 말에 특정 방향에서 우클릭하면 탑승 안 됨: 바닐라도 내린 뒤 3초는 다시 못 탐. 그 시간이 지나도 안 되는지, 말이 청크 경계 근처인지
- 워든 비명체 4단계 경고: 직접 설치한 비명체는 `can_summon=false` 라 바닐라도 경고 없이 비명만 냄. 감지기→비명체 연결에 `can_summon=true` 가 필요하고 근처 48칸 안에 워든이 있으면 경고도 비명도 없음
- 주민이 종을 치면 숨는지: 숨을 곳이 침대라서 침대가 정상이어야 함(위 마을 항목과 함께 확인)

## CI·시작 스크립트 (.github/workflows/ci.yml, start.sh, start.bat 새로 만듦)
- CI 는 기본 ubuntu-latest 러너·push/PR 전체 트리거로 만들어 둠. 러너 종류(자체 호스팅 여부), 브랜치 제한, 캐시 정책은 정해 주세요.
- 바닐라 jar 추출(3-1)은 첫 실행 때 Mojang 다운로드가 필요할 수 있음. CI 에서 외부 접근을 허용할지, 테스트가 그것 없이 통과하는지 첫 실행으로 확인 필요(로컬 검증은 --offline 뿐).
- 시크릿은 쓰지 않음. 시작 스크립트는 installDist 결과를 `java -Xmx6G -cp lib/*` 로 실행. 실제 서버 기동은 확인하지 못함.

## 결정 필요 (제가 임시로 정한 것은 되돌릴 수 있음)
- **Mojang jar·클래스**: 사용자 위임에 따라 (a) 로 처리함 — `tools/vanilla-data/` 의 jar·libraries·versions·`*.class` 약 6,180개를 git 추적에서 뺐고 `.gitignore` 로 막음(로컬 사본은 그대로). 이미 push 된 히스토리에는 남아 있음(.git 158MB). 히스토리까지 지우려면 force push 가 필요한 파괴적 작업이라 하지 않았음 — 원하시면 말씀하세요.
- 공유 스케줄러(B안): N1~N7 을 반영한 확정 설계서 `docs/feature-parity/shared-scheduler-design-2026-10-28.md` 작성 완료(선행 ①②도 끝남). 결정 필요 — (a) 설정: `region-threads` 이름 유지·의미를 "서버 전체 워커 수"로, 기본값 코어 수 그대로, 롤백 스위치 `-Dfurnace.sharedRegionPool`(기본 true, 한 릴리스 유지) 제안대로 할지 (b) 활성화 시점: S4 merge 때 바로 기본 켬 vs 한 릴리스 동안 기본 끔으로 측정만 (c) 동작 동일 리팩터링 단계(S1~S3)를 먼저 진행해도 되는지. 상세 §10.
- F-14: 생성만 하고 편집 안 한 청크를 디스크에 저장할지 — 바닐라는 생성된 청크를 모두 저장하므로 parity 기준으로는 저장하는 쪽. 작업 리스트에 넣어 둠(되돌리려면 말씀하세요).
- 브랜치 3개(claude/kind-goldberg-au63ot, cool-mayer-hqp9tw, work-continuation-u597cw) 삭제: 제게 권한이 없음(403) → GitHub 웹에서 직접
- 이전에 push 한 커밋 일부의 trailer 에 모델 이름이 들어 있음. 다시 쓰려면 히스토리 재작성이 필요해 하지 않았음

## 마인카트 (이식 완료)
- 미구현: 상자/깔때기 수레·상자 배 창, 스포너·명령 블록 수레 동작, 디스펜서 설치, 몹이 수레를 미는 처리

## 약탈자 순찰·습격
- 가정(바닐라 jar 대조 필요): 저장 키 `patrol_target`, 마녀 지도자 불가, 불길한 깃발 이름 스타일
- 미구현: 습격자 목표 AI(깃발·마을 이동·축하)
- 습격은 바닐라 1.21.1대로 종이 아니라 "나쁜 징조" 상태로 마을에 들어가야 시작됨. 테스트하려면 `/effect give @s bad_omen`
- 바닐라와 차이: 내려간 청크의 POI 는 조회 안 됨, 같은 거리 POI 선택 순서

## 플레이어 전투
- 미구현: 쓸기/피해 파티클(level_particles 패킷 없음), 힘·나약함 효과 반영, 철퇴, 살충 느림
- 확인 필요: 삼지창 급류(물/비 속), 다른 플레이어에게 보이는 웅크림·활 당김 포즈 (손 아이템 수정과 함께 확인)

## 엔티티 창
- 확인: 길들인 말에 웅크려 우클릭 또는 탑승 중 인벤토리 키 → 안장/갑옷 창, 당나귀 상자
- 미구현: 깔때기 수레 빨아들임, 라마 카펫 칸, 이름표 창 제목

## 지도 깃발 표식
- 미구현: 깃발 무늬 컴포넌트→블록 엔티티 변환, 깃발 부술 때 이름·무늬 드롭(LootParams.BLOCK_ENTITY 없음), 네더 지도

## 워든·진동
- 확인: 워든 소리가 나는지(`minecraft:entity.warden.*` 접두사 형식), 조용히(웅크림) 걸으면 감지 안 되는지, 양털로 진동 차단
- 미구현: 진동·음파 파티클, 걸음 소리, 알레이 진동 수신

## 깔때기·용기
- 가정(jar 대조 필요): 양조기 연료 칸은 블레이즈 가루만
- 미구현: 용기 엔티티 전리품 표 풀기, VindicatorBreakDoorGoal

## 엔더 드래곤
- 2026-10-28 디컴파일 한 줄 대조 완료: 난수 순서·죽음 연출·normalize·always_hurts_ender_dragons(= #is_explosion) 확정, 차이 15건 수정 (docs/feature-parity/dragon-parity-recheck-2026-10-28.md)
- 화살·폭발이 파트에 맞고(머리 배율 없음, 그 밖 /4+1) 받침대 청크가 없으면 부활·포털을 미루도록 수정함(접속 확인 필요: 화살로 날개/머리 맞추기, 부활)
- 남은 차이: 활성 출구 포털 판정(end_portal·관문 블록 엔티티 없음), 공격 후 마법 부여 효과, 사용자 이름 보스 바

## 주민 AI
- 주민 FOLLOW_RANGE 가 16→48(바닐라 값)로 바뀜
- 미구현: 밭 수확·뼛가루, 거래 보여주기, 영웅 선물, 습격·숨기 활동(PRE_RAID/RAID/HIDE), 생성 이유별 종류/직업 설정
- 소문·평판·특별 가격(거래 → 평판 변화, 좀비 주민 치료 시 할인, 마을의 영웅): 사용자가 나중에 확인하기로 함

## 다차원 호스팅 — 네더·엔드
- 알려진 차이: 차원 이동이 비동기라 몇 틱 늦게 도착, 보더 차원 공유, 네더 포탈에서 좀비 피글린 미구현
- 같은 문으로 돌아올 때 기존 오버월드 포탈로 오는지(Y 한 칸 아래 문제와 함께 확인)
- 설계 문서: docs/feature-parity/multi-dimension-2026-10-26.md
- 차원마다 region 스레드 풀을 따로 둠(쉬는 차원은 거의 비용 없음). 하나로 공유하는 구조는 B안(위 결정 항목)

## 스컬크
- 가정: SculkBehaviour 구현 블록은 스컬크·맥뿐(바닐라 jar 대조 필요), 경험치 보정(처치자·마법 부여) 없음
- 미구현: 스컬크 영혼 파티클, 발전 과제, pushEntitiesUp, 플레이어 사망 경험치 흡수

## 주민 세부
- 가정: 심을 씨앗 태그 `villager_plantable_seeds` 를 코드에 바닐라 기본값으로 고정(데이터팩 변경 미반영)
- 미구현: 철 골렘 평판 -100 플레이어 공격, 발전 과제, 스포너 경로 finalizeSpawn

## 수면·리스폰
- 알려진 차이: 밤 건너뛰기 후 깨우기가 다음 region 틱, 자는 중 접속 종료 시 침대 위 위치 저장(바닐라는 일어선 자리), 네더·엔드 월드의 기본 스폰 (0,64,0) 전송
- 미구현: 자는 중 텔레포트·차원 이동 시 깨우기

## 명령
- 이번까지 추가: `/give` `/clear` `/enchant` `/tp` `/ride` `/random` `/tag` `/spreadplayers` `/forceload` `/tick`(sprint 제외) `/tellraw` `/title` `/playsound` `/stopsound` `/bossbar` `/transfer` `/particle` `/damage` `/data` `/item` `/loot` `/scoreboard` `/team` `/attribute` `/clone` `/fillbiome` `/place` `/trigger` `/teammsg` `/execute`(일부), 모두 실접속 미확인. 일반 엔티티를 고르는 `@e` 명령은 이제 바닐라 반환값을 낸다 (`/execute store` 는 S3~S4 에서)
- `/tp` 는 차원 간 이동 가능(2026-10-28 구현, 아래 시험 항목 참고)
- 아직 없음: 명령 블록 등(`/tick sprint` 는 구현됨). 순서는 docs/feature-parity/roadmap-2026-10-09.md 의 작업 리스트
- 벌·벌집·고양이 스포너: 벌집 만지기/꿀병/가위, 밤에 벌이 들어가는지, 벌에 쏘이면 독, 마을 근처 고양이 스폰

## 블록 엔티티
- 제작기 메뉴는 구현됨(아래 시험 항목). 미구현: 스포너 승객/장비, 벌집, 표지판 텍스트 선택자/점수 해석
- 확인 못 함: 표지판 JSON 직렬화가 바닐라와 바이트 동일한지(클라가 파싱하면 문제 없음)

## 월드젠 확인
- 새 월드젠 후 사막 신전 상자 4개 확인해 볼 만함 (`decoStarts` 수정 후)

## 서버 성능 관찰
- 플레이어 2명부터 청크 전송이 1명일 때보다 약간 느림(N-05 청크 스냅샷 보관 제거 이후). 작업 리스트에 측정 항목으로 넣어 둠
- 청크 본문 스냅샷 복사 줄임(할당 약 -70%)·엔티티/POI 저장 잠금 분할을 넣음. 많은 플레이어로 체감 차이 확인

## 생성 청크 저장(F-14)
- 생성만 한 청크도 바닐라처럼 디스크에 남는다 — 탐험한 만큼 월드 폴더가 커진다(크기 제한 없음). 디스크 여유 확인
- 한 번 저장된 청크는 이후 월드젠 수정이 반영되지 않는다. 월드젠 parity 를 실제 서버로 확인할 땐 새 월드로(또는 `region/` 삭제)
- 상세: docs/feature-parity/f14-generated-chunk-save-2026-10-27.md

## 공유 스케줄러 (S1~S3 병합됨, 기본 동작 불변)
- S4(차원 간 공유 풀)는 아직 안 켬 — 결정 필요: 설정 이름·기본값·되돌리는 방법. 부하 5ms 의 p95 가 +0.5~0.9ms 높게 나온 경향이 있어 S4 전 번갈아 재측정 필요 (`docs/feature-parity/shared-scheduler-s1s3-2026-10-28.md`)
