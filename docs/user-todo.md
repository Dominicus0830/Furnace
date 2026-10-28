# 사용자 확인·결정 필요 항목 (대화 걸면 알려 줄 것)

2026-10-27 사용자 실접속 테스트 반영 후 정리본. 잘 되는 것으로 확인된 줄은 지웠다. 버그 상세와 원인 조사는 `docs/feature-parity/user-test-bugs-2026-10-27.md`.

## 다시 접속해서 확인할 것 (수정함, 아직 접속 확인 못 함)
- 다른 플레이어가 손에 든 아이템·방어구가 보이는지, 겉옷·겉머리 스킨 레이어가 보이는지, 죽었다 리스폰한 뒤에도 보이는지 (패킷 `set_equipment`, 엔티티 데이터 17·18 추가)
- 방패로 근접 공격을 막으면 밀리지 않는지(바닐라는 막은 플레이어에게 속도 패킷이 가지 않는다 — 구조를 바닐라와 같게 고침)
- 네더 포탈에서 관전 모드는 포탈을 못 타는지
- 종 블록 몸통이 보이는지, 침대가 보이는지(블록 엔티티 추가), 종을 치면 습격자가 발광하는지, 지도 깃발 표식이 유지되는지
- 마을에 주민·철 골렘·고양이가 처음부터 있는지, 주민이 침대·직업 블록을 얻고 문을 열고 일하는지 (구조물 템플릿 엔티티가 지금까지 생성되지 않던 버그와 주민 길찾기가 옛 레벨을 쓰던 버그를 고침). **수정 전에 만든 월드**는 청크를 처음 다시 로드할 때 주민·골렘이 한꺼번에 나타날 수 있음
- 말 체력이 15~30 범위로 나오는지, 워든을 `/summon` 하면 땅속으로 들어가지 않는지, 토끼 스폰 NPE 가 사라졌는지
- 발전과제: kill·번식·길들이기·음식 섭취 트리거가 연결됨(`docs/feature-parity/advancement-triggers-2026-10-28.md`). 다른 region 에서 일어난 처치는 최대 한 틱 늦게 반영될 수 있음
- 새 명령: `/place` `/trigger` `/teammsg` `/version` `/plugins`

## /execute·함수·/schedule·매크로 (구현함, 아직 접속 확인 못 함)
- 설계 대비 차이와 의도한 한계: `docs/feature-parity/execute-function-design-2026-10-27.md` 맨 앞 요약. 아래는 접속해서 해 볼 시나리오.
- 점수 저장: `/scoreboard objectives add t dummy` 후 `/execute as @e[type=cow] store result score @s t run data get entity @s Health` → `/scoreboard players list @e[type=cow,limit=1]` 에 체력이 있는지(소가 여러 마리면 각자 저장)
- 조건: `/execute if entity @e[type=cow,distance=..10] run say 소 있음`, `/execute unless block ~ ~-1 ~ air run say 발밑 블록`, `/execute if score @s t matches 10.. run say 큼`
- 데이터 저장: `/execute store result storage t:x v int 1 run data get entity @e[type=cow,limit=1] Health` 뒤 `/data get storage t:x v`
- 함수 데이터팩: `world/datapacks/test/pack.mcmeta`(pack_format 48) + `data/t/function/a.mcfunction`(예: `say hi` / `scoreboard players add #n t 1`) 를 만들고 `/reload` → `/function t:a` 가 실행되고 결과 메시지가 나오는지. `data/minecraft/tags/function/tick.json`(`{"values":["t:a"]}`)이면 매 틱, `load.json` 이면 `/reload` 뒤 한 번 실행되는지
- 예약: `/schedule function t:a 5s` 가 5초 뒤 실행, `/schedule function t:a 5s append`, `/schedule clear t:a`, 서버 재시작 뒤에도 남아 있는지
- 매크로: 함수 파일 줄이 `$say $(msg)` 일 때 `/function t:m {msg:"hello"}` 와 `/function t:m with storage t:x` 로 치환되는지, 인자 누락 오류 메시지
- 주의: 다른 region(멀리 떨어진 곳)의 엔티티를 오가는 긴 함수는 몇 틱 걸릴 수 있음(의도한 차이). 오래 걸리면 서버 로그의 100틱 경고를 알려 주세요.

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
- `/tp` 는 차원 간 이동 불가(다른 차원 대상이면 실패)
- 아직 없음: `/tick sprint`, 명령 블록 등. 순서는 docs/feature-parity/roadmap-2026-10-09.md 의 작업 리스트
- 벌·벌집·고양이 스포너: 벌집 만지기/꿀병/가위, 밤에 벌이 들어가는지, 벌에 쏘이면 독, 마을 근처 고양이 스폰

## 블록 엔티티
- 미구현: 제작기 메뉴(칸 잠금 클릭 불가 — 지금은 열리지 않음), 스포너 승객/장비, 벌집, 표지판 텍스트 선택자/점수 해석
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
