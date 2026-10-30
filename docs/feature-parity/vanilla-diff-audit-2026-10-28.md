# 바닐라 대비 부족·차이 감사 (2026-10-28)

읽기 전용 하위 에이전트 5개가 영역별로 대조한 결과의 요약. 코드는 수정하지 않았다. 바닐라 근거는 난독화된 디컴파일(클래스·문자열·데이터 json)이라 줄 단위 대조는 대표 항목에 한한다. 확인 못 한 것은 "가정".
원본 보고서(파일:줄·근거 전부)는 세션 스크래치 `audit/{commands,entities,blocks-items,world,net-player}.md` 에 있었고, 이 문서에는 결론만 옮겼다.

## 일치 확인 (문제 없음)
- 블록 정의 241종 전부 등록, 메뉴 24종·레시피 serializer·전리품 조건 19종·게임룰 51종 이름/기본값 일치
- 바이옴 스폰 목록 64개(가중치·군집·비용), MobCategory, 스폰 배치 표(74종), NaturalSpawner 알고리즘 일치
- 몹 속성 약 100종(Vindicator·Rabbit 제외), 엔티티 데이터 직렬화기 번호표, 목표 기반 클래스 flags 36종 일치
- 랜덤 틱 순서·불 번짐·유체·날씨 일치(읽은 범위), NoopFeature 는 `bonus_chest`·`void_start_platform` 뿐
- 엔티티 레지스트리 130종 중 128종 클래스 연결(나머지는 player·fishing_bobber)

## 관측 영향 큼 (우선 수정)
| 영역 | 항목 | 상태 |
|---|---|---|
| 명령 | 선택자 `nbt=` `advancements=` `predicate=` `level=` 스텁(항상 거짓/레벨 0) | 작업 중 |
| 블록 | 블록 파괴 전리품에 BLOCK_ENTITY 미전달 — 내용물 든 셜커 상자 파괴 시 내용물 소실 | 작업 중 |
| 영속화 | 엔티티 `CustomName`·`CustomNameVisible`·`Glowing`·`HasVisualFire`·`Team`·`DeathLootTable` 미저장, `active_effects`·`AbsorptionAmount` 미복원 | 작업 중 |
| 블록 | 묘목·뼛가루가 나무/식물로 자라지 않음(블록 레벨 feature 배치 훅 없음) | 작업 중 |
| 블록 | 발사기·공급기 껍데기 | 작업 중 |
| 엔티티 | Vindicator 체력/추적 뒤바뀜, 평화로움 despawn 규칙, Slime `Enemy` 아님, Rabbit 속성, 깎인 양 전리품 | 작업 중 |
| 월드 | `generate-structures=false` 가 구조물 생성을 안 막음 | 작업 중 |
| 네트워크 | serverbound swing 미처리(팔 휘두름 미방송), pick_item 미처리 | 작업 중 |
| 월드 | 게임룰 9종(fireDamage·freezeDamage·projectilesCanBreakBlocks·enderPearlsVanishOnDeath·spawnRadius 등) 저장만 됨 | 작업 중(일부) |
| 엔티티 | 생물끼리 밀기·maxEntityCramming 없음(몹 농장 핵심) | 구현됨(한계는 context-notes 참고) |
| 플레이어 | 불사의 토템, 겉날개(2026-10-28 구현 — context-notes 차이 참고), 플레이어 방어구 인챈트 경감(보호 등), 플레이어 속성 연동 | 대기 |
| 네트워크 | light_update(0x2A) 미송신 의혹 — 실접속·바닐라 확인 필요 | 확인 작업 중 |
| 블록 | 피스톤이 엔티티를 안 밂, 엔더의 눈 던지기, 몹 양동이(물고기·아홀로틀·올챙이), 솔·고고학, 트라이얼 스포너·금고, 명령/구조/직소 블록, 블록에 맞는 투사체 반응 12종 | 대기 |
| 플러그인 | 이벤트 15종뿐, Entity·World·ItemStack·Inventory 타입 없음 | 대기 |

## 관측 영향 중
- 몹 목표 누락: 좀비 계열(거북알·마을 이동·철 골렘/아기 거북 타깃·`alertOthers`), 거미/해골/슬라임의 철 골렘·거북 타깃, 늑대(라마 회피·거북·해골), 토끼(늑대 회피), 철 골렘 마을 AI(DefendVillage·MoveBackToVillage), 마녀 타깃, 고양이 3종, 돌고래 3종, 여우, 앵무 어깨, 엔더마이트 타깃, 수호자 아홀로틀
- Pig·Strider·Camel 안장은 얹지만 올라타지 못함, 늑대 변종 없음, 마을 포위(VillageSiege) 없음, 엔티티 동결(가루눈) 없음, 복어 접촉 독 없음, Mob 장비 교체 단순화, 팀 `isAlliedTo`, 끈(리쉬), 스포너 수레, 알레이 진동 수신
- 블록 엔티티 12종 없음(skull·conduit·command/structure/jigsaw·end_gateway·end_portal·enchanting_table·brushable·trial_spawner·vault), 가마솥 씻기, 깃발 무늬 블록 이전, 엔티티 접촉 훅(버튼 화살·작물·거북알 등), 비교기 액자, 수상한 스튜/후렴과일/불길한 병, 산양 뿔·망원경·책 열기/편집, TNT 점화 도구 소모
- 월드: 서리 얼음 영구, 개구리알/스니퍼알 부화 새끼 없음, 오버월드 포탈 좀비 피글린, 스폰 청크 상시 로드, `nether_fossil` 미생성, 커스텀 generator(large_biomes·amplified·debug) 무시, 지도는 region 보유 청크만
- 네트워크: custom_payload(플러그인 채널·brand)·hurt_animation·set_entity_link·set_camera·resource_pack 미송신/무시, 이동 검증("moved too quickly") 없음, idle-timeout 킥 없음
- 플레이어: 성급함·채굴 피로·힘·나약함 미반영, thorns·호흡·frost_walker, 수영/기어가기/불/투명/발광 플래그 미동기, 이동 통계 없음
- 명령: `/spectate` `/debug` `/jfr` `/perf` `/publish` 미등록(구현 중), `/kick` 권한 레벨 2(바닐라 3), 블록 엔티티 Clearable/NBT, 채팅 필터, 아이템 술어는 count·damage 만
- 전리품 조건 파서가 모르는 EntityPredicate/LocationPredicate 필드는 항상 거짓(바닐라 데이터에서 걸리는 곳: soul_speed·wind_burst·channeling), 전리품 함수 15종 미지원(데이터팩/플러그인 표만 영향)

## 주의
- 파일 머리의 TODO 주석 상당수가 낡았다(실제로는 구현됨). 주석만 믿고 판단하지 말 것.
- 일부 항목은 기존 알려짐(roadmap·user-todo·context-notes) — 새 발견은 L3(`active_effects`), L4~L6, 블록 H2·H5~H9, 게임룰 9종, swing/light_update, 선택자 스텁, `/kick`.

## 후속 처리: 몹 목표 누락(M3~M6) — 2026-10-28
- 추가: 좀비 계열 4 `ZombieAttackTurtleEggGoal`(새 기반 `RemoveBlockGoal`), 6 `MoveThroughVillageGoal`(새 기반), 타깃 3 철 골렘·5 땅 위 아기 거북, HurtBy `alertOthers(ZombifiedPiglin)`; Drowned 타깃 3 아홀로틀(Drowned·ZombifiedPiglin 도 바닐라처럼 4번 거북 알 목표를 물려받음).
- 추가: Spider 2 아르마딜로 회피·타깃 3 철 골렘, AbstractSkeleton 타깃 3 철 골렘·아기 거북, Slime 타깃 3 철 골렘, Wolf 3 라마 회피(`WolfAvoidEntityGoal`)·타깃 6 아기 거북·7 해골류, Rabbit 4 늑대 회피·킬러 토끼 타깃 2 늑대, Cat·Ocelot 타깃 1 아기 거북, PolarBear 타깃 4 여우, Enderman 타깃 3 엔더마이트, Guardian 선택자 아홀로틀.
- 추가: IronGolem 타깃 1 `DefendVillageTargetGoal`, `MoveBackToVillageGoal`·`GolemRandomStrollInVillageGoal` 바닐라 본문(POI·주민 기반, LandRandomPos).
- 추가: Witch 타깃 1 `HurtByTargetGoal(Raider)`, 2 `NearestHealableRaiderTargetGoal`, 3 `NearestAttackableWitchTargetGoal`(치료 쿨다운 동안 플레이어 타깃 끔).
- 부수 수정: `MoveToBlockGoal.start` 의 maxStayTicks 가 `nextInt(nextInt(1200)+1)` 이던 것을 바닐라 `nextInt(nextInt(1200)+1200)` 로(이 기반을 쓰는 모든 몹의 난수 소비가 바뀜). 좀비·드라운드의 주민 타깃을 AbstractVillager 범위(떠돌이 상인 포함)로.
- 보류: M8 Cat 3종(침대·블록 앉기·주인 위), M9 Dolphin 3종, M10 Fox 마을 산책·가루눈, M11 Parrot 어깨 — 이번 작업 범위 밖(시간), 표는 그대로.
- 후속(2026-10-30): M8 Cat 3종, M9 Dolphin 아이템 놀이·보트 따라가기, M10 Fox 마을 산책·가루눈 오르기, Vindicator 문 부수기를 추가했다(`context-notes.md` 참고). 아직 없음: Dolphin 플레이어와 헤엄(Player 수영 상태 없음), Parrot 어깨 앉기(플레이어 어깨 저장 없음), Dolphin 보물 탐색(구조물 연결).
- 남은 차이: 다른 몹(Vex·Illager 등)의 AbstractVillager 타깃은 확인 안 함. Turtle 귀가 목표가 바닐라 `DefaultRandomPos`(PI/10) 대신 `LandRandomPos` 를 씀(별도 확인 필요).
