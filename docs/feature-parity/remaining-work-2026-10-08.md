# 남은 작업 · 미구현 기능 정렬 목록 (2026-10-08, 브랜치 claude/work-continuation-u597cw)

목표: 바닐라/Paper/Spigot과 **관측 가능한 결과가 같고**, 내부는 최대한 빠른 서버.
근거: 감사 문서(`vanilla-diff-2026-10-07.md`) + 이후 구현분 + 월드젠 측정(Harness 11시드, 아래 수치) + 중첩 Noop 리플렉션 스캔.

## 현재까지 끝난 것 (요약)
- 접속/로그인/탭리스트/영속(playerdata)/DoS 방어/종료 처리, 바닐라 데이터 추출 도구, 순정 월드젠 데이터 교체.
- 월드젠 포팅: 블록 술어·배치 수정자·IntProvider·식생 패치·block_column·거대 버섯·대나무·표면 규칙(배드랜드 점토 띠·침식 배드랜드·빙산 확장·steep/y_above/온도)·눈/얼음(FreezeTopLayer)·얼음 가시·빙산·파란 얼음·드립스톤 3종·어두운 참나무.
- 성능: 청크 생성 657→231 ms(단일 스레드), 월드 소스 초기화 4.0→1.1 s.
- 측정: 11시드 hard 불일치 합계 3,075,162 → 1,221,821 (-60%) (이후 드립스톤·빙산·파란 얼음은 아직 재측정 전).

## P0 — 서버로서 기본 동작 (없으면 일반 서버로 쓸 수 없음)
| # | 항목 | 규모 | 비고 |
|---|---|---|---|
| 1 | 채팅·명령(Brigadier, Commands 패킷, 자동완성)·콘솔 stdin·권한 레벨. stop/op/deop/whitelist/ban/kick/say/list/tp/gamemode/time/weather/give | L | 사용자 지시: 월드젠 다음 순서 |
| 2 | `server.properties` 호환 키셋(max-players·white-list·online-mode·difficulty·gamemode·hardcore·motd·level-seed 등), ops/whitelist/banned-*/usercache/eula 파일, 로그인 게이트(밴·화이트리스트·가득 참), 상태 JSON(online/max/sample/favicon) | M | |
| 3 | `level.dat`(시드·스폰·시간·게임룰), `session.lock`, 바닐라 playerdata 형식, 청크 NBT에 biomes·구조물·InhabitedTime 저장, 모르는 NBT 보존, LZ4/.mcc | L | 지금은 바이옴을 plains로 읽음 |
| 4 | 게임모드(기본 survival)·체력·허기·피해·낙하/공허·사망·리스폰·경험치 | L | 지금 전원 creative 고정 |
| 5 | 시간(Set Time)·날씨·난이도·월드 보더 로직과 패킷 | M | |

## P1 — 게임플레이 코어
| # | 항목 | 규모 |
|---|---|---|
| 6 | 블록 설치 상태(방향·반쪽·큰 상자…), 교체 가능 판정, 파괴(도구/드롭/소리·입자), 이웃 업데이트·예약 틱·유체·랜덤 틱·낙하 블록·불·레드스톤 | XL |
| 7 | 아이템 엔티티·드롭·줍기, 컨테이너 클릭 6종(QUICK_MOVE·SWAP·THROW·QUICK_CRAFT·PICKUP_ALL·CLONE), 제작·레시피, 화로 등 나머지 컨테이너, 블록 엔티티(표지판·침대·스포너…) | XL |
| 8 | 엔티티 전반: 몹·동물·주민·투사체·경험치 구슬·전투, 엔티티 메타데이터·장비·애니메이션, 소리·입자·레벨 이벤트 | XL |
| 9 | 빛 엔진(지금 상수 sky 15 / block 0) | L |
| 10 | 차원: 네더·엔드·포털, 월드 유형(대형 바이옴·증폭·슈퍼플랫 연결) | XL |
| 11 | 발전 과제·통계·점수판·보스바·타이틀·포션/속성 | L |

## P2 — 월드젠 남은 항목 (측정 기반 순서; 괄호는 4시드 이상 합산 불일치 규모)
1. **풍성한 동굴 잔여**(점토·이끼·동굴 덩굴·큰 드립잎 등, 약 14만): 식생 패치/이끼 패치 계열에서 아직 어긋남 — 원인 조사 후 수정.
2. **sculk_patch / sculk_vein**(약 7.3만): SculkSpreader(전하 확산) 포팅 필요. 심층 암흑·고대 도시.
3. **나무·덩굴 순서 의존**(정글·맹그로브 등 약 22만+): 바닐라 자체가 생성 순서에 따라 달라지는 부분이 섞여 있어, 줄일 수 있는 몫을 먼저 분리해 조사.
4. root_system(`rooted_azalea_tree`, 약 8천), `lake`(용암 호수 52개 바이옴, 약 6천), 산호 3종·sea_pickle(약 5천), `monster_room`(53개 바이옴, 약 5천), `fossil`, `desert_well`, `disk_grass`(규칙 일부 미지원), `forest_flowers`/`flower_forest_flowers` 중첩 일부, `block_pile`, `bonus_chest`.
5. **구조물 33개**: 광산·요새·마을·해저 신전·삼림 대저택·고대 도시·시련의 방·폐허 포털·묻힌 보물·전초기지·이글루·정글 사원·난파선 외(현재 사막 신전만). 바닐라 월드의 모습에 영향이 큼.
6. 네더·엔드 지형과 feature(현무암 기둥·균류·덩굴·델타·글로우스톤·엔드 섬·스파이크·게이트·코러스 등) — 차원(P1-10)과 같이.
7. 지형 잔차: 오버월드 지형 뼈대는 거의 일치. 남은 건 실제 서버와의 미세 차이를 시드별로 계속 측정.

## P3 — 성능·인프라·운영
- 청크 생성 병목(프로파일): 지형 밀도 27%(대수층 19%), 바이옴 탐색 13%, 구조물 시작 링 336회 비캐시. 출력은 바꾸지 않고 중복·할당만 제거.
- TPS/MSPT 측정, 워치독·크래시 리포트, WriteBufferWaterMark·flush 합치기, 청크 전송 적응형, RegionFile 개선(#19), 청크 티켓·강제 로드·스폰 청크.
- 핸드셰이크 프로토콜 버전 검증, transfer, 쿠키/Plugin Message, 리소스팩, RCON/Query/메트릭.
- 플러그인 API(`furnace-api`): 수명주기·로더(`plugins/`)·이벤트·명령·설정·Server/World/Player/Block API·`plugin.yml`, 클래스로더 격리 (Paper 호환 목표이면 매우 큼).
- 테스트·문서: `ConnectionInputTest.packetFlood_isKicked_butSmallBurstIsNot` 간헐 실패(임시 파일 경쟁), CI, README, 이 문서 계열 갱신.
- 감사 문서 잔여: #10(evict 직후 재구독 경합), #15(레거시 핑), #17(-ea), N6/N8/N9/N10.

## 제안 진행 순서
1. (이미 합의) 월드젠 마무리: P2-1 → P2-2 → P2-4 → P2-3 → P2-5.
2. 그다음 합의대로 P0-1(명령·채팅·콘솔) → P0-2 → P0-3 → P0-4/5.
3. P3 성능은 각 단계 사이에 병행(출력 불변 검증 후 적용).
4. P1은 규모가 커서 P0 이후 작은 단위로 쪼개어 진행.


---

## 재검토 결과 (같은 날 2차) — 앞 목록에서 빠져 있던 것

앞 목록은 감사 문서와 월드젠 측정에 치우쳐 있었다. 바닐라 리포트(`packets.json`, `registries.json`, `commands.json`)·설계 문서(M0~M6)·코드 스텁 검색으로 다시 대조해 아래를 추가한다.

### 규모를 숫자로 본 현재 위치
- 패킷: play 클라이언트바운드 124개 중 구현 33개(미구현 91), play 서버바운드 58개 중 처리 12개(미처리 46). 설정/로그인/상태는 대부분 구현.
- 바닐라가 가진 것(미구현): 엔티티 종류 130, 블록 엔티티 44, 메뉴 25, 포션 효과 39, 속성 31, 명령 83, 레시피 1,290, 전리품 표 1,178, 발전 과제 1,399, 구조물 34종(구조물 세트 20), 사운드 1,611, 파티클 109, 데이터 컴포넌트 57.

### 새로 확인된 누락 (우선순위순)
**A. 결정이 필요한 것 (구조에 영향)**
1. **플러그인 호환 범위.** 설계서는 "Bukkit 코드 미사용 + Folia식 자체 API(`furnace-api`)"이다. 사용자 목표("기존 버킷과 같은 결과물")가 기존 Bukkit/Spigot/Paper 플러그인 jar를 그대로 돌리는 것까지 포함하는지에 따라 규모가 한 단계 달라진다(Bukkit API 호환 층은 수천 개 클래스). 먼저 정해야 함.
2. **온라인 모드(암호화·Mojang 세션 인증·스킨·서명된 채팅)와 프록시 전달(Velocity/Bungee).** 설계서 비목표였으나 바닐라/Paper 기본값은 `online-mode=true`. 채팅 서명(`chat_session_update`, `player_chat`, `chat_ack`)이 함께 따라온다.
3. **Paper 고유 동작.** `paper-global.yml`/`paper-world-defaults.yml`/`spigot.yml`/`bukkit.yml`/`commands.yml`, `/paper`·`/timings`·`/mspt`·`/tps`·`/version`·`/plugins`, 안티 엑스레이, 몹 스폰 한도·엔티티 활성 범위·청크 로딩 속도 설정, 권한 시스템(`permissions.yml`). 기준이 Paper라면 "바닐라 대비 Paper가 바꾼 기본 동작" 목록을 따로 만들어야 한다.

**B. 데이터 주도 시스템 (게임플레이 전반의 전제)**
4. 전리품 표(1,178)·레시피(1,290)·발전 과제(1,399)·데이터 팩 로더(`datapacks/`, `/reload`, `/datapack`) — 상자 내용물, 블록·몹 드롭, 제작, 발전 과제가 전부 여기에 의존.
5. 아이템 데이터 컴포넌트 전반(이름·마법 부여·물약·내구도·속성 수정자 등) 저장·검증·전송. 지금은 불투명 바이트로 들고 다님.
6. 게임 규칙(`/gamerule`, 약 40개)과 그에 따른 동작.
7. 구조물 템플릿(NBT) 추출: 마을·전초기지·고대 도시·시련의 방·네더 요새 등은 `jigsaw` 조립기와 `structure/*.nbt` 템플릿이 필요(데이터 생성기가 내보내지 않으므로 jar에서 따로 추출).

**C. 월드젠에서 빠졌던 것**
8. **월드젠 시점 몹 스폰**(평원의 동물 등 청크 생성 때 생기는 몹), 스포너 블록 엔티티, 구조물 상자 전리품.
9. 구조물 참조/시작 저장(`/locate`, 후처리), `generate-structures=false`, 보너스 상자, 시드 처리(빈 값이면 무작위 — 지금은 0).
10. 바닐라 월드를 열었을 때의 안전성: 레지스트리에 없는 블록을 공기로 바꿔 저장해 영구 삭제(새 버전·모드 월드), `DataVersion` 확인·데이터 픽서 없음.

**D. 게임플레이 쪽에서 앞 목록이 뭉뚱그린 것 (패킷 기준)**
11. 서버바운드 미처리 46개 중 핵심: `use_item`(먹기·활·진주·양동이), `interact`(공격), `swing`, `player_command/player_input`(웅크리기·달리기), `client_command`(리스폰), `chat*`/`chat_command*`/`command_suggestion`, `place_recipe`/`recipe_book_*`, `rename_item`·`select_trade`·`set_beacon`·`edit_book`·`sign_update`·`set_command_block`·`set_jigsaw_block`·`set_structure_block`, `move_vehicle`/`paddle_boat`/`pick_item`, `teleport_to_entity`, `container_button_click`, `chunk_batch_received`(적응형 청크 전송), `resource_pack`, `cookie_response`, `custom_payload`(플러그인 채널·BungeeCord).
12. 클라이언트바운드 미구현 91개 중 핵심: `commands`, `system_chat`/`player_chat`/`disguised_chat`, `update_tags`는 구현됨, `update_recipes`/`recipe`/`update_advancements`, `set_entity_data`/`set_equipment`/`update_attributes`/`update_mob_effect`/`animate`/`entity_event`/`hurt_animation`, `sound`/`level_event`/`level_particles`/`block_event`/`block_destruction`, `section_blocks_update`(다중 블록 갱신), `light_update`, `chunks_biomes`, `set_time`/날씨(`game_event`), `respawn`, `boss_event`/`set_title_*`/`set_action_bar_text`/점수판류, `map_item_data`, `merchant_offers`, `set_passengers`/`move_vehicle`, `explode`, `tab_list`, `server_links`, `resource_pack_*`, `store_cookie`/`transfer`.

**E. 운영·품질**
13. **검증 도구가 월드젠에만 있다.** 네트워크·게임플레이에는 바닐라와 비교할 방법이 없다. 제안: 바닐라 서버 jar의 패킷 코덱(`StreamCodec`)으로 Furnace가 만든 패킷을 직접 디코드해 형식 오류를 잡고, 바닐라 서버와 Furnace에 같은 봇을 접속시켜 접속 시퀀스를 차분 비교(실제 클라이언트가 이 환경에서는 돌지 않음).
14. 스레딩 모델 위험(감사 문서 2.5): region→스레드 정적 해시와 재균형 없음, 한 스레드의 region 순차 틱·블로킹 작업, 틱마다 O(N) 스캔(evict·가시성·탭리스트), region 간 핸드오프 최종 일관성(깜빡임), **전역 틱/배리어 부재**(시간·날씨·게임규칙·`/tick`·예약 명령), 장애 격리·풀 과구독(gen 풀 4청크 제한), 청크당 캐시 메모리(`-Xmx6G`).
15. 손상 파일 방어 잔여: 리전 헤더 길이 할당 상한, 압축 해제 크기 제한, NBT 재귀 깊이 제한, 청크 저장 실패 재시도, 저장 순서 역전 시 오래된 스냅샷이 최신을 덮는 문제.
16. 시작/배포: 시작 스크립트·Dockerfile·fat jar·JVM 플래그(`-Xms`, GC, `-ea`), 파일 로깅과 접속/퇴장 로그, 크래시 리포트, CI(`.github` 없음), README.
17. 번역/로케일: 서버 측 메시지 번역(`disconnect.*`, `multiplayer.*`)과 JSON 텍스트 컴포넌트 체계.

### 정정
- 앞 목록의 "플러그인 API (수명주기·로더·이벤트·명령)"는 A-1의 결정 없이는 규모를 정할 수 없어 P3에 두면 안 된다. 결정 후 P0~P1 사이로 올린다.
- 월드젠 P2-4 항목의 "중첩 Noop"은 리플렉션 스캔으로 확정: `desert_well`, `disk_grass`(규칙 일부), `forest_flowers`·`flower_forest_flowers`, `fossil_lower/upper`, `lake_lava_surface/underground`(52개 바이옴), `monster_room(_deep)`(53), `rooted_azalea_tree`, `sculk_patch_deep_dark`, `sculk_vein`, `sea_pickle`, `warm_ocean_vegetation`(산호 3종).
