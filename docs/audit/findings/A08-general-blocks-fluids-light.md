# A08 일반 블록·유체·식물·무작위 틱·빛·설치/채굴 규칙 — 2차 전체 점검 조사 보고서 (2026-11-08)

- **기준 커밋**: `claude/quirky-wright-lra834` `eab009d9`
- **조사관**: A08 조사 스레드 (Sonnet high). 코드는 고치지 않았다.
- **바닐라 대조**: 바닐라 서버 jar 를 `javap -c -p` 로 읽었다(디컴파일 소스·jar 는 커밋하지 않음). 이 보고서에 쓴 난독화 이름: `dga` Blocks, `dfy` Block, `dtb` BlockBehaviour, `dtc` BlockState, `dtb$d` BlockBehaviour.Properties, `aqu` ServerLevel, `dcw` Level, `dcz` LevelReader, `dlt` PowderSnowBlock, `dnz` SugarCaneBlock, `diw` FenceBlock, `dnq` GrassBlock. 훅 메서드(`dtb`): tick `a(dtc,aqu,jd,ayw)V`, randomTick `b(…)V`, onPlace `b(dtc,dcw,jd,dtc,Z)V`, onRemove `a(dtc,dcw,jd,dtc,Z)V`, updateShape `a(dtc,ji,dtc,dcx,jd,jd)dtc`, neighborChanged `a(dtc,dcw,jd,dfy,jd,Z)V`, canSurvive `a(dtc,dcz,jd)Z`, isRandomlyTicking `d_(dtc)Z`, getFluidState `b_(dtc)epe`, fallOn `a(dcw,dtc,jd,bsr,F)V`.
- **재현 시험**: `docs/audit/findings/A08-repro/*.java.txt`. `.txt` 를 떼고 아래 위치에 넣는다.
  - `A08LightBlockApproxReproTest.java.txt`, `A08PowderSnowFallReproTest.java.txt` → `furnace-server/src/test/java/org/domi/furnace/server/block/`
  - `A08LightRandomizedTest.java.txt` → `furnace-server/src/test/java/org/domi/furnace/server/light/`
  - 실행: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew :furnace-server:test --tests '*A08*'`
  - **실패하는 것이 재현**이다(고치면 통과). 대조용이라 지금도 통과해야 하는 시험: `controlOpenSkyAndStone`, `controlDirtStillHurts`, `A08LightRandomizedTest` 둘.
  - 기준 커밋 실행 결과: 11개 중 **7개 실패**(재현), 4개 통과(대조·빛 엔진 검증).
- `docs/audit/findings/A08-repro/tools/` 에 훅 대조표를 만든 스크립트(`cmp.py`·`parse2.py`·`parse_blocks.py`·`chain.py`: `javap` 출력에서 블록별 재정의 훅을 뽑아 Furnace 쪽 `FDump`(등록된 동작 클래스)와 비교)와 빛 차단 상태 비교(`LDiff`, 결과 `ldiff-result.txt`)를 `.txt` 로 두었다. 경로는 스크래치패드 기준이라 그대로 돌리려면 고쳐야 한다.

## 조사한 범위
- **훅 대조표(블록 전체)**: 바닐라 1054개 블록(`dga` 필드, `javap` 로 각 블록 정의 클래스의 재정의 훅 사슬을 상속까지 따라감)과 Furnace 1060개 블록(`BlockBehaviors`/`Reg.type` 등록 클래스의 재정의)을 tick·randomTick·onPlace·onRemove·updateShape·neighborChanged·canSurvive·isRandomlyTicking·getFluidState·fallOn·use·stepOn 등 훅 단위로 비교했다. 바닐라에 있고 Furnace 에 없는 훅은 `powder_snow.fallOn`(A08-03) 하나였다. (그 밖의 차이는 특수 블록 이름 차이·도우미로 만든 블록 등 오탐이었고 줄 단위로 확인해 제외.)
- **빛 엔진**: `LightEngine`·`BlockLightEngine`·`SkyLightEngine`·`LightTables`(바닐라에서 추출한 `blocklight_1.21.1.txt`)가 바닐라 포팅과 같은지. 반 블록·계단·물·잎·유리·색유리·얼음·눈·경작지·꿀·거미줄·양탄자·다락문을 청크 경계 둘레에 무작위로 놓고 지우며 (1) 증분 갱신 = 처음부터 계산, (2) 블록빛 = 독립 구현(고정점 반복) 인지 6+6 시드로 칸마다 비교(`A08LightRandomizedTest`, 모두 통과). 기존 `IncrementalLightTest` 는 돌·공기·발광석만 써서 모양 있는 블록의 가림 판정은 시험된 적이 없었다.
- **유체**: `FlowingFluid`(퍼짐 거리·구멍 찾기·하강·소스 만들기), 물/용암 만남(`LiquidBlock.shouldSpreadLiquid`, 흑요석·조약돌·현무암), 눈·가루눈·풀 따위의 유체 교체, 예약 틱 간격(물 5·용암 30·지옥 10) 줄 단위 대조 — 같음.
- **무작위 틱**: `ServerLevel.tickChunk` 순서(번개 → 강수 → 얼음/눈 → 섹션별 `randomTickSpeed` 번 칸 뽑기 → `isRandomlyTicking` 검사 → 블록 `randomTick` + 유체 `randomTick`)와 난수 호출 순서, 섹션 건너뛰기, 강수 판정. 같음.
- **식물/성장**: 농작물(`CropBlock` 성장 확률·`getGrowthSpeed` 경작지·이웃 보정), 줄기(수박·호박)·열매 맺기, 사탕수수·선인장·대나무 높이 한도와 `canSurvive`, 나무 묘목·`TreeGrower`, 잎 썩음 거리(`LeavesBlock.updateDistance`), 덩굴 퍼짐, 불 번짐·타는 확률(`FireBlock`), 경작지 마름·밟기(`FarmBlock`). 같음.
- **채굴 속도**: `ItemTools.destroySpeed`/`isCorrectToolForDrops` 와 `Player.getDestroySpeed`(효율·신속·채굴 피로·물 속·공중) 대조 — 같음.
- **빛 근사 사용처**: `B.lightBlockedAbove` 를 쓰는 모든 곳(잔디·균사체·나일리움·신호기) 상태 전부 대조 → A08-01·02.

## 조사하지 못한 범위 (이유)
- **다음 식물·블록은 줄 단위 대조를 못 했다**(시간): 켈프·해초 성장, 대나무 줄기 성장 세부, 코러스(식물/꽃) 성장, 버섯 퍼짐·거대 버섯, 구리 풍화(`ChangeOverTimeBlock`), 점적석(물방울·종유석 성장), 바다거북 알 부화/밟기, 달콤한 열매 덤불, 문·다락문·울타리 문 `neighborChanged`. 훅 대조표에서는 훅이 있다는 것까지만 확인했다(A08-03 외 누락 없음).
- **`BlockPlacement` 의 엔티티 충돌 검사**(`level.isUnobstructed` 호출, 줄 212·233)와, 코드 주석이 말하는 "`ChunkLevel.noCollision` 에 엔티티 충돌 없음"·"`ItemUse` TODO(다른 배)"가 아직 사실인지 — 주석이 낡았을 가능성이 있는데 확인하지 못했다. 다음 차수에서 설치 규칙(`canPlace`/`isUnobstructed`)을 보는 사람이 첫 줄로 확인하길 권한다.
- **실행 대조 불가**: 바닐라 서버를 이 컨테이너에서 띄울 수 없어(Mojang 라이브러리 403) 기대값은 모두 바이트코드와 바닐라에서 추출한 빛 표(`LightTables`)로 계산했다.
- 하늘빛(`SkyLightEngine`) 시험은 `A08LightRandomizedTest` 가 증분=처음부터 비교만 한다(독립 기준 구현 없음). 블록빛만 독립 기준 구현과 비교했다.
- 레드스톤 장치 일체(A07), 블록 엔티티 틱·저장(A09), 몹 자연 생성/스폰 거리(A10)는 범위 밖.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | — |
| 높음 | 1 | A08-01 |
| 중간 | 2 | A08-02, A08-03 |
| 낮음 | 3 | A08-04, A08-05, A08-06 |
| 참고 | 3 | A08-07, A08-08, A08-09 |

도미니코 결정이 필요한 항목은 없다(전부 바닐라와 같게 고치는 것이 원칙). user-todo 항목은 만들지 않았다.

**바닐라와 같다고 확인한 것(아래 표에 올리지 않음)**: 빛 엔진 본체(모양 섞인 무작위 시험 통과), 유체 흐름·만남, `tickChunk` 와 난수 순서, 농작물·줄기·사탕수수·선인장·불·잎·덩굴·경작지·나무 묘목의 틱 로직, 채굴 속도 계산, 플레이어가 가루눈에 떨어질 때의 낙하 피해(`PlayerVitals.fallMultiplier` 가 가루눈에 -1 을 돌려줘 올바름).

**무작위 틱 범위 재확인(A07-07 요청)**: 무작위 틱이 도는 범위는 바닐라에서 `tickChunk` 가 도는 청크 = "블록 틱 청크"(플레이어 청크와의 체비셰프 거리 ≤ 시뮬레이션 거리 + 1)다. 1차 점검의 판정(C1-01)과 같은 원인이라 별도 항목으로 올리지 않는다 — 수정은 A10 스레드가 C1-01 로 진행 중이다. 이 영역에서 새로 필요한 것은 "고친 뒤 `Region` 의 블록 틱 판정이 예약 틱·블록 이벤트·무작위 틱·강수 틱에 똑같이 +1 로 적용되는지"의 확인뿐이다. 기준 커밋에서 확인한 바로는 무작위 틱 자체의 로직(섹션·칸 뽑기·`isRandomlyTicking`)은 바닐라와 같다.

---

## 항목

### A08-01 — 잔디·균사체·나일리움이 "위 칸이 빛을 막는가"를 근사로 판정해, 양탄자·중계기·비교기·퇴비통·이끼 양탄자 밑에서 흙/네더랙이 되고 색유리 밑에서는 살아남는다

| 칸 | 내용 |
|---|---|
| 심각도 | **높음** (건축에서 흔한 조합: 잔디 위 양탄자·중계기가 몇 분 안에 잔디를 흙으로 바꾼다. 눈에 띄는 바닐라 차이) |
| 종류 | 바닐라 차이 (근사 구현 TODO) |
| 근거 | `block/B.java:188-193` `lightBlockedAbove(above) = canOcclude(block) && occlusionShape(above).isFaceFull(DOWN)` 이 `SpreadingSnowyDirtBlock.canBeGrass`(`types/SpreadingSnowyDirtBlock.java:22`)와 `NyliumBlock.canBeNylium`(`types/NyliumBlock.java:16`)에서 쓰인다. 코드 주석 자체가 `TODO(빛)` 라고 적고 있다.<br>바닐라 `dnq`(GrassBlock/SpreadingSnowyDirtBlock) `canBeGrass`: 위가 눈 한 겹이면 참 → 가득 찬 유체면 거짓 → `LightEngine.getLightBlockInto(level, state, pos, aboveState, abovePos, UP, aboveState.getLightBlock(level, abovePos)) < 15`. `getLightBlockInto` 는 두 칸 중 하나라도 모양이 비지 않으면 면 가림(`shapeOccludes`)이 참일 때 16 을 돌려주고, 아니면 위 칸의 `getLightBlock`(불투명 렌더 15, 하늘빛이 위아래로 통하면 0, 아니면 1, 블록이 따로 정하면 그 값)을 쓴다. 즉 "면이 꽉 찼는가"가 아니라 "빛이 얼마나 줄어드는가"다.<br>**상태 전수 비교**(모든 블록 상태에서 `B.lightBlockedAbove` 와 위 바닐라 식을 `LightTables.lightBlockInto`(바닐라 추출 표)로 계산해 비교): 다른 상태 **107개, 블록 21종**.<br>• Furnace 가 막는다고 판정(바닐라는 안 막음): 모든 색 양탄자 16종, 이끼 양탄자, 중계기, 비교기, 퇴비통.<br>• Furnace 가 안 막는다고 판정(바닐라는 막음): 색유리(`tinted_glass`, 빛 차단 15 인데 `canOcclude=false`).<br>(상세는 `A08-repro/tools/ldiff-result.txt` 윗부분.) |
| 재현 | `A08LightBlockApproxReproTest`: `grassSurvivesUnderCarpet`(양탄자 밑 잔디가 `randomTick` 한 번에 흙이 됨: `expected: not equal but was: <10>`), `grassSurvivesUnderRepeaterAndComparator`(중계기), `grassDiesUnderTintedGlass`(색유리 밑 잔디가 흙이 되어야 하는데 살아 있음: `expected <10> but was <9>`), `approximationMatchesVanillaRuleForEveryState`(`다른 상태 107개, 블록 21종`) — 기준 커밋에서 모두 **실패**. 대조: `controlOpenSkyAndStone` 통과. |
| 제안 | `canBeGrass`·`canBeNylium` 의 판정을 `LightTables.lightBlockInto(grassState, aboveState, UP, LightTables.lightBlock(aboveState)) >= 15` 로 바꾼다(나일리움도 같은 식, 바닐라 `NyliumBlock.canBeNylium` 이 같은 호출). 빛 표는 이미 있다(`LightTables` 가 빛 엔진에서 같은 식을 쓴다). `B.lightBlockedAbove` 는 사용처가 없어지면 지운다. 범위 `SpreadingSnowyDirtBlock`·`NyliumBlock`·(A08-02 와 같은 PR) `BeaconBlockEntity`, 크기 **작음**, 위험 낮음(순수 판정 함수 교체, 시험이 이미 있음). 권장 **Sonnet medium**. A08-02 와 한 PR. |

### A08-02 — 신호기 빛줄기 검사가 `getLightBlock >= 15` 대신 같은 근사를 써서, 반 블록·계단·양탄자·눈 한 겹 등이 빛줄기를 끊는다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 (신호기 위에 반 블록·계단·눈이 얹히면 효과가 꺼진다. 건축 빈도는 A08-01 보다 낮음) |
| 종류 | 바닐라 차이 (근사 구현 TODO) |
| 근거 | `block/entity/BeaconBlockEntity.java:~86-91` 주석 `TODO(빛): 바닐라는 getLightBlock(state) >= 15 && 기반암 아님. 지금은 위로 빛을 완전히 막는지로 근사.` — `B.lightBlockedAbove(state) && !bedrock` 로 구간을 끊는다. 바닐라 `BeaconBlockEntity.tick`(구간 순회)은 색 유리가 아닌 칸에서 `getLightBlock(...) >= 15 && !bedrock` 이면 구간을 지우고 끊는다.<br>**상태 전수 비교**: 근사가 "막는다"인데 바닐라는 "안 막는다"인 블록이 **약 150종**(모든 반 블록·계단 종류, 모든 색 양탄자, 이끼 양탄자, 눈 층, 경작지, 흙 길, 햇빛 감지기, 마법 부여대, 엔드 차원문 틀, 서가대(lectern), 돌 절단기, 중계기, 비교기, 퇴비통, 피스톤·끈끈이 피스톤·피스톤 머리, 스컬크 감지기·보정 감지기·비명꾼, 구리 계단/반 블록 …). 반대로 색유리(`tinted_glass`)는 바닐라가 끊는데 근사는 안 끊는다. (`A08-repro/tools/ldiff-result.txt` 아랫부분 "beacon differences".) |
| 재현 | `A08LightBlockApproxReproTest.beaconBeamPassesBottomSlabAndCarpet`(`minecraft:oak_slab 가 신호기 빛줄기를 끊는다고 판정됨`), `beaconBeamStaysOpenUnderBottomSlabCarpetAndSnowLayer`(신호기 위에 반 블록을 올리고 40틱 돌린 뒤 `isBeamActive()` 가 거짓) — 기준 커밋에서 **실패**. 대조 `controlOpenSkyAndStone` 통과(하늘이 열리면 열리고 돌이면 끊김). |
| 제안 | 끊는 조건을 `LightTables.lightBlock(state) >= 15 && !B.is(state, "bedrock")` 로 바꾼다. A08-01 과 한 PR. 크기 **작음**, 위험 낮음. 권장 **Sonnet medium**. |

### A08-03 — 가루눈이 `fallOn` 을 재정의하지 않아, 플레이어가 아닌 생물이 가루눈 위로 떨어지면 낙하 피해를 받는다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 (몹/동물이 가루눈에 떨어져 죽는다. 바닐라에선 피해 없음. 가루눈 함정·낙하 시험 장치에 영향) |
| 종류 | 바닐라 차이 (누락) |
| 근거 | 훅 대조표에서 바닐라에만 있는 훅 1건. 바닐라 `dlt`(PowderSnowBlock) 는 `fallOn(level, state, pos, entity, fallDistance)` 를 재정의해, `fallDistance >= 4` 이고 `entity` 가 `LivingEntity` 일 때만 낙하 소리(7 미만 `small_fall`, 이상 `big_fall`)를 내고 `super.fallOn`(낙하 피해)을 부르지 않는다. Furnace `PowderSnowBlock` 에는 재정의가 없어 기본 `BlockBehavior.fallOn`(`block/BlockBehavior.java:235` — `entity.causeFallDamage`)이 불린다. `Entity.checkFallDamage`(`entity/Entity.java:1445`)가 이 훅을 부르므로 모든 생물에 영향. 플레이어는 `PlayerVitals.fallMultiplier`(`entity/PlayerVitals.java:1196`)가 가루눈에 -1(피해 없음)을 돌려줘서 이미 올바르다. |
| 재현 | `A08PowderSnowFallReproTest.mobTakesNoFallDamageOnPowderSnow`: 소를 가루눈 위로 10칸 낙하 처리 → 체력 `expected: <10.0> but was: <3.0>` (**실패**). 대조 `controlDirtStillHurts`(흙 위 낙하는 피해) 통과. |
| 제안 | `PowderSnowBlock.fallOn` 재정의: `fallDistance < 4 || !(entity instanceof LivingEntity)` 이면 아무것도 안 함, 아니면 `fallDistance < 7` 은 small_fall 소리·아니면 big_fall 소리(블록 `fallSounds` 에서 가져오는 바닐라 식 그대로, 구현 전 바이트코드 확인)만 내고 피해를 주지 않는다. 크기 **작음**, 위험 낮음. 권장 **Sonnet low**. |

### A08-04 — 네더랙 뼛가루 판정이 `propagatesSkylightDown` 을 근사로 쓴다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 (근사 구현 TODO) |
| 근거 | `block/types/NetherrackBlock.java:17-23` — `propagatesSkylightDown(state)` 를 "가리지 않거나 가림 모양이 꽉 차지 않고 유체가 없다"로 근사(주석 `TODO(빛): 블록별 값`). 바닐라는 `BlockBehaviour.propagatesSkylightDown` 이 블록마다 정해진 값(기본은 모양이 완전한 블록이 아니고 유체가 없음, 일부 블록이 따로 재정의 — 정확한 목록은 구현 전에 바이트코드로 확인). 뼛가루가 위 칸이 "빛을 통하는" 네더랙에만 먹히므로 영향은 위 칸이 잎·얼음·유리·꽃 같은 특이 블록일 때뿐이다. |
| 재현 | 만들지 않았다(블록별 값이 이미 `blocklight_1.21.1.txt` 에서 추출되는 `useShape`/`lightBlock` 과 같은 방식으로 얻을 수 있어 아래 제안 한 줄로 끝나는 크기). 참고용으로, 상태 전수 비교로 확인하려면 `LightTables` 에 `propagatesSkylightDown` 열을 추가해 `NetherrackBlock` 근사와 비교하면 된다. |
| 제안 | 바닐라 `propagatesSkylightDown` 값을 상태마다 추출해(빛 표와 같은 방식) `LightTables` 에 열을 추가하고 `NetherrackBlock` 이 쓰게 한다. 크기 작음. 권장 Sonnet medium. |

### A08-05 — `B.isLiquid` 가 바닐라 `BlockState.liquid()` 와 달리 `bubble_column` 을 뺀다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 |
| 근거 | `block/B.java:223` `isLiquid(state) = block == WATER || block == LAVA`. 바닐라 1.21.1 에서 `liquid()` 가 참인 블록은 물·용암·**거품 기둥** 셋(블록 정의 `liquid()`). 거품 기둥을 `liquid()` 로 보는 쪽(설치 시 대체 가능 판정·붕괴/경로 탐색의 액체 판정 등)에서만 차이가 난다. 이 보고서에서는 사용처마다의 영향을 줄 단위로 확인하지 못했다(가정). |
| 재현 | 없음(사용처별 영향 확인 필요). |
| 제안 | `isLiquid` 에 `bubble_column` 추가 후 사용처(`grep isLiquid`) 각각을 바닐라 호출과 대조. 크기 작음. 권장 Sonnet low. |

### A08-06 — 마그마 블록의 서리 보행자 면역이 플레이어 아닌 생물에는 적용되지 않는다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 |
| 근거 | `block/types/MagmaBlock.java` `stepOn`: 웅크리지 않은 `LivingEntity` 에 `hot_floor` 피해 1. 주석이 "서리 보행자 인챈트는 아직 없어 늘 해당 없음"이라 적었다. 바닐라 1.21.1 `MagmaBlock.stepOn` 은 서리 보행자 보유 여부(인챈트 효과 판정)를 피해 조건에 넣는다. 정확한 조건식은 구현 전에 바이트코드로 확인할 것. Furnace 가 서리 보행자 신발을 신은 플레이어에게 이를 다른 곳에서 처리하는지는 확인하지 못했다(가정). |
| 재현 | 없음. |
| 제안 | 바닐라 조건을 확인한 뒤 `ItemTools.enchantmentLevel`(발 칸 장비) 등으로 같은 조건을 넣는다. 크기 작음. 권장 Haiku low~Sonnet low. |

### A08-07 — 참고: 모양이 섞인 블록을 쓰는 빛 시험이 지금껏 없었다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 시험 구멍 |
| 근거 | 기존 `IncrementalLightTest` 는 돌·공기·발광석만 쓴다. 이번에 만든 `A08LightRandomizedTest` 가 반 블록·계단·물·잎·유리·색유리·얼음·눈·꿀·거미줄·양탄자·다락문·용암·횃불을 섞어 증분=처음부터, 블록빛=독립 기준 구현을 칸마다 비교하며 **모두 통과**했다. 빛 엔진 쪽에서는 바닐라와 다른 점을 못 찾았다. |
| 재현 | `A08LightRandomizedTest`(통과가 정상). |
| 제안 | 수정 PR 에서 이 시험을 `.txt` 를 떼고 정식 시험으로 들일 것을 권한다. 하늘빛 기준 구현은 없으니, 필요하면 후속으로 독립 하늘빛 기준 구현을 추가. 크기 작음. 권장 Sonnet medium. |

### A08-08 — 참고: 두 근사(`B.lightBlockedAbove`, 네더랙)는 모두 "빛 차단 표가 생기면 정확히"라는 TODO 였고, 표(`LightTables`)는 이미 있다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 정리 |
| 근거 | `rg "TODO\(빛\)"` 로 위 세 곳(A08-01·02·04)이 전부. 표가 생긴 뒤에도 TODO 를 갱신하지 않아 남아 있었다. 같은 종류의 TODO 가 다른 영역에 더 있는지는 `rg "TODO\("` 로 한 번 훑어 보길 권한다. |
| 재현 | — |
| 제안 | A08-01·02·04 수정으로 해소. |

### A08-09 — 참고: 코드 주석 몇 곳이 낡았을 수 있다(확인 못 함)

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 문서/주석 |
| 근거 | "조사하지 못한 범위" 참고: `ChunkLevel.noCollision` 에 엔티티 충돌이 없다는 주석, `ItemUse` 줄 241 의 다른 배 TODO. 사실이면 설치 규칙(`BlockPlacement` → `level.isUnobstructed`)이 엔티티를 못 본다는 뜻이라 높은 항목이 될 수 있어, 확인이 먼저다. |
| 재현 | 없음. |
| 제안 | 다음 차수에서 A08 후속(설치 규칙 줄 단위 대조)을 열 때 첫 확인 항목으로. |

---

## 제안 모음 (수정 PR 후보)

| 묶음 | 항목 | 크기 | 권장 |
|---|---|---|---|
| 빛 차단 근사 제거 | A08-01, 02, 04 (+07 시험 정식화) | 작음 | Sonnet medium |
| 가루눈 `fallOn` | A08-03 | 작음 | Sonnet low |
| 작은 바닐라 일치 | A08-05, 06 | 작음 | Sonnet low |
| 후속 점검 | A08-09 + 못 본 식물·구리·점적석 줄 단위 대조 | 중간 | Sonnet high (점검) |
