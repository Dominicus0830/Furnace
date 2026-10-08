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
