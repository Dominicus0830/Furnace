# A12 아이템·레시피·전리품·진행 — 2차 전체 점검 조사 보고서 (2026-11-11)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `fcaa0b7c`(PR #199 병합 시점). 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/server-1.21.1`)를 `javap -c -p` 로 읽고, 바닐라 데이터 JSON(`data/minecraft/{recipe,loot_table,advancement}`)을 직접 읽었다.
  난독화 이름 대응(이번에 확인): `an` CriteriaTriggers(`Q` = player_generates_container_loot), `avz` Stats(`E` jump, `J` damage_taken, `L` damage_absorbed, `H`·`I` damage_dealt_absorbed·resisted, `M` damage_resisted, `p`~`D` 이동 거리 15종), `cut` Items, `dga` Blocks, `awn` ItemTags, `dpv` AbstractFurnaceBlockEntity(`g()` getFuel), `cti` DyeColor, `dql` BrushableBlockEntity, `cuq` ItemStack, `cmx` Player, `aqv` ServerPlayer, `btn` LivingEntity.
- 재현 시험: `A12-repro/` 에 5개 파일. 높음 항목 3개 파일(시험 7개)은 기준 커밋에서 **7개 모두 실패**한다(차이 재현). 나머지 2개는 "같음 확인" 탐침(통과/경고 목록 출력)이다. 쓰는 법은 아래 "재현 시험 쓰는 법".

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 5 | A12-01 망원경 발전 과제 3종 달성 불가(`looking_at`) · A12-02 "초원에서 음악 틀기" 달성 불가(`jukebox_playable`) · A12-03 솔질 전리품이 `player_generates_container_loot` 를 안 부름("유물 살려내기" 달성 불가) · A12-04 걷기·점프·피해 받음 통계 21종이 전혀 안 오름 · A12-06 `effects_changed` 의 source 없음(아홀로틀 발전 과제 달성 불가, 기존 문서화 — 영향이 A12-02·03 과 같아 같은 등급) |
| 중간 | 1 | A12-05 먹기·마시기가 `used` 통계를 안 올림 |
| 낮음 | 5 | A12-07 지도가 시야 밖 청크를 불러 오지 않음(범위 좁음) · A12-08 ~ A12-11 |
| 참고 | 2 | A12-12 ~ A12-13 |

도미니코의 결정이 필요한 항목: A12-07 하나(시야 거리가 8청크보다 작을 때 지도가 둘레 청크를 불러 올지 — 영향이 좁아 낮음)(`docs/user-todo/entries/2026-11-11-audit-A12.md`). 나머지는 전부 바닐라와의 관측 차이다.

가장 큰 공통점은 **"바닐라 데이터는 전부 읽히는데, 읽은 조건을 평가하는 쪽(술어)이나 사건을 일으키는 쪽(호출 지점)에 구멍이 있다"** 는 것이다. 데이터 해석 자체(레시피 1,290개·발전 과제 1,399개·전리품 표 1,178개 적재, 모든 레시피 제작 일치, 연료·염료 표)는 이번 조사에서 하나도 어긋나지 않았다(아래 "같음 확인").

## 조사한 범위

- **발전 과제**: 바닐라 `CriteriaTriggers` 57개 이름 전부에 Furnace 매처가 있는지, 바닐라 데이터의 비레시피 발전 과제 약 125개를 트리거별로 훑어 "일어날 수 없는 것"을 찾음, 술어(엔티티·위치·아이템) 지원 필드 대 바닐라 데이터가 쓰는 필드, 호출 지점 대조(브러시·뾰족…), 진행 저장 형식.
- **레시피**: 제작 레시피 887개(shaped 634 + shapeless 253)를 **칸마다 모든 재료 후보(태그 구성원 전부, 최대 60조합)로 3×3·모양 그대로 격자에 놓고 실제로 맞추어 결과 아이템·개수 대조**(3,385조합), 특수 레시피 14종 코드 읽기(갑옷 염색·깃발·책·지도·불꽃놀이·수리·방패 장식·상자 색·수상한 스튜·화살·장식 단지), `RepairItemRecipe` 바이트코드 대조, 남는 아이템.
- **전리품**: 바닐라 함수 40종·조건 19종·항목 8종·숫자 공급자 6종 이름 전부 Furnace 에 있음, 바닐라 전리품 표가 쓰는 함수·조건 실제 집계, 술어의 미지원 필드 경고, 문맥 매개변수(luck·this_entity 등).
- **통계**: 바닐라 `Stats` 커스텀 통계 약 80개 대 Furnace `awardStat` 호출 지점, 종류별 통계(mined·used·crafted·broken·picked_up·dropped·killed·killed_by) 호출 지점.
- **아이템**: 데이터 컴포넌트 이름 54개 전부 등록됨, 연료표 59항목 바이트코드 대조, 염료 색 표, `used` 통계 호출 지점.
- **점수판·지도**: 점수 기준 연결(기존 문서), `scoreboard.dat` 형식, 지도 색 표(바닐라 표를 내보낸 것), 깃발·액자·잠금·확대 연결.

## 조사하지 못한 범위 (이유)

- **전리품 함수·조건의 수치 평가를 바닐라와 한 칸씩 대조**: 상자(280건)·블록·엔티티 정답지 시험이 이미 있고(`VanillaChestLootTest` 등) 그 범위만 확인했다. 정답지가 없는 함수(`enchant_randomly`·`enchant_with_levels`·`set_stew_effect`·`exploration_map`·`apply_bonus` 의 확률 식)는 난수 순서까지 대조하지 못했다. 바닐라 서버 실행이 막혀 있다.
- **물약 양조 표(`PotionBrewing`)·화로 경험치·모닥불 요리**: 이번엔 읽지 못했다(양조 메뉴는 A09). 다음 점검에서 같은 방식(바닐라 `PotionBrewing` 바이트코드 대 `item/PotionBrewing.java`)으로 볼 만하다.
- **점수판 명령·표시 슬롯·팀 규칙의 문구와 반환값**: A13(명령) 범위와 겹쳐 `ScoreboardTest` 가 있는 부분만 확인했다.
- **발전 과제 조건의 세부 값(예: `location` 20틱 평가의 바이옴 판정)**: 53개 바이옴 발전 과제와 같은 데이터 의존 항목은 일일이 못 돌렸다.
- **클라이언트로만 보이는 것**(지도 렌더 결과 색, 통계 화면, 발전 과제 창 배치): 사용자 확인 칸(user-todo)에 이미 있다.

---

## 항목

### 높음

| 칸 | 내용 |
|---|---|
| ID | **A12-01** |
| 심각도 | 높음 |
| 종류 | 바닐라 차이(미구현) |
| 근거 | `loot/EntityPredicate.java:60-63`(player `stats`·`looking_at` 를 읽을 때 경고만 하고 `:255` 에서 `return false`). 바닐라 데이터 `advancement/adventure/spyglass_at_parrot.json`·`spyglass_at_ghast.json`·`spyglass_at_dragon.json` 이 `using_item` 트리거의 `player` 조건으로 `type_specific:{type:"minecraft:player", looking_at:{type:...}}` 를 쓴다. 이 필드는 M19 기록(`unimplemented-recheck-2026-11-07.md` 78행)에 "남은 것(늘 거짓+경고)"으로만 적혀 있다. |
| 재현 | `A12PredicateReproTest.playerLookingAtParrotMatches` — 기준 커밋에서 실패(기대 참, 결과 거짓). 바닐라 데이터 적재 시 경고 `아직 지원하지 않는 player 조건 필드(늘 거짓으로 본다): looking_at` 가 **3번** 나온다(`A12LoadWarningsProbeTest`). |
| 영향 | **"망원경으로 앵무새/가스트/엔더 드래곤 보기"(Is It a Bird?·Is It a Balloon?·Is It a Plane?) 발전 과제 3종을 영영 못 깬다.** 바닐라는 `using_item` 을 망원경을 쓰는 매 틱 부르고 조건이 시선 레이캐스트(바닐라 `PlayerPredicate.LookingAtPredicate`: 눈 위치에서 시선 방향으로 약 100칸 안에서 `ProjectileUtil.getEntityHitResult`, 관전자 제외. 독립 검토에서 바이트코드(`dp`)로 확인: 눈 위치에서 시선 벡터 × 100, 그 선분 상자를 1.0 부풀림, `ProjectileUtil.getEntityHitResult(…, 관전자 아님, 0.0f)`, 결과가 엔티티면 하위 술어 판정 **그리고 `ServerPlayer.hasLineOfSight(엔티티)` 도 참**이어야 한다 — 벽 뒤 앵무새는 달성되지 않는다). |
| 제안 | `LootEntity` 에 플레이어의 시선 엔티티를 돌려주는 메서드를 두고(`KillCredit.snapshot` 이 다른 region 엔티티를 안전하게 뜨는 기존 방식을 따라), `EntityPredicate.playerSpecific` 가 `looking_at` 을 하위 `EntityPredicate` 로 평가하되 위 100칸·1.0 부풀림·관전자 제외·`hasLineOfSight` 를 그대로 옮긴다. 같은 자리의 `stats`(player 술어의 통계 조건, 바닐라 데이터에는 쓰는 곳 없음)도 `PlayerStats` 로 연결 가능. 작음~중간. Sonnet high. |

| 칸 | 내용 |
|---|---|
| ID | **A12-02** |
| 심각도 | 높음 |
| 종류 | 바닐라 차이(미구현) |
| 근거 | `loot/ItemPredicate.java:41-44`(`predicates` 하위 조건 중 `enchantments`·`stored_enchantments`·`damage`·`potion_contents` 만 있고 나머지는 "아직 지원하지 않는 아이템 조건 — 항상 거짓"). 바닐라 데이터 `advancement/adventure/play_jukebox_in_meadows.json` 이 `item_used_on_block` 의 `match_tool` 조건에서 `predicates:{"minecraft:jukebox_playable":{}}` 를 쓴다. |
| 재현 | `A12PredicateReproTest.jukeboxPlayableItemPredicateMatchesMusicDisc` — 기준 커밋에서 실패. 바닐라 데이터 적재 시 경고 `아직 지원하지 않는 아이템 조건 minecraft:jukebox_playable — 항상 거짓` 이 1번 나온다. |
| 영향 | **"초원에서 음악 틀기"(Play Some Music in the Meadows) 발전 과제를 영영 못 깬다.** 데이터 팩이 쓰는 나머지 아이템 하위 조건도 전부 거짓이다: `custom_data`·`container`·`bundle_contents`·`firework_explosion`·`fireworks`·`writable_book_content`·`written_book_content`·`attribute_modifiers`·`trim`(바닐라 `ItemSubPredicates` 1.21.1, 이 목록은 바닐라 데이터에서 쓰이지 않아 이번엔 읽어 대조하지 못했다. 가정). 이 조건은 발전 과제뿐 아니라 `match_tool`·`entity_properties.equipment` 로 전리품 표에도 쓰인다(데이터 팩). |
| 제안 | **A13 보고서(PR #213) 수정 묶음 1(하위 술어 코덱을 명령 인자와 `loot/ItemPredicate` 가 함께 쓰게 함)과 하나로 합친다.** 바닐라 `ItemSubPredicates`(`cu`) 14종(`jukebox_playable` 포함)을 그쪽에서 구현하고, 같은 파일을 서로 다른 방침으로 고치지 않는다. 모르는 이름은 14종이 구현된 뒤 바닐라처럼 그 파일을 거부(오류 로그)한다(절대 규칙 2, 옵션 없음). 구현 전 단계에서만 지금의 거짓 + 경고를 유지. 작음. Sonnet medium. |

| 칸 | 내용 |
|---|---|
| ID | **A12-03** |
| 심각도 | 높음 |
| 종류 | 바닐라 차이(호출 지점 누락) |
| 근거 | `block/entity/BrushableBlockEntity.java:81-88`(`unpackLootTable` 이 전리품만 풀고 발전 과제를 안 부름). 바닐라 `dql.a(cmx)`(BrushableBlockEntity.unpackLootTable(Player)) 바이트코드에 `getstatic an.Q`(`GENERATE_LOOT`) 호출이 있다. 같은 사건을 상자·상자 수레는 `ServerCore.generatedLoot` 로 이미 부른다(`ServerCore.java:524-529`). `docs/feature-parity/advancement-triggers-2026-10-28.md` 는 "브러시 블록(`dql`)은 솔질 작업자 영역이라 연결하지 않았다"고 적어 두었다. |
| 재현 | `A12BrushLootTriggerReproTest.playerBrushFiresContainerLootTrigger` — 기준 커밋에서 실패(보상 경험치 기대 3, 결과 0). 같은 시험 안의 대조(트리거를 직접 부르면 달성)는 통과하므로 시험 배선 문제는 아니다. |
| 영향 | **"유물 살려내기"(Respecting the Remnants, `adventure/salvage_sherd`)를 깰 수 없다.** 이 발전 과제는 6개 고고학 전리품 표 중 하나를 풀 때의 `player_generates_container_loot` + `has_sherd` 를 요구한다(발전 과제 JSON 의 `requirements`). 수상한 모래·자갈을 솔질해도 도예 조각을 얻는 것과 별개로 발전 과제는 영영 안 뜬다. 시련 금고·상자 쪽 같은 트리거는 연결되어 있다. |
| 제안 | `BrushableBlockEntity.unpackLootTable` 에서 `player.handle()` 이 `Player` 이면 전리품을 만들기 전에 `player.triggerAdvancement(a -> a.triggerContainerLoot(table))` (스레드 주인 규칙은 `ServerCore.generatedLoot` 와 같다). 작음. Sonnet medium. |

| 칸 | 내용 |
|---|---|
| ID | **A12-04** |
| 심각도 | 높음 |
| 종류 | 바닐라 차이(미구현) |
| 근거 | 바닐라 `Stats`(`avz`) 커스텀 통계 중 Furnace 에서 **아무 곳에서도 안 오르는 것 21개**: 이동 거리 15종(`walk_one_cm`·`crouch_one_cm`·`sprint_one_cm`·`walk_on_water_one_cm`·`fall_one_cm`·`climb_one_cm`·`fly_one_cm`·`walk_under_water_one_cm`·`minecart_one_cm`·`boat_one_cm`·`pig_one_cm`·`horse_one_cm`·`aviate_one_cm`·`swim_one_cm`·`strider_one_cm`), `jump`, `damage_taken`, `damage_dealt_absorbed`·`damage_dealt_resisted`, `damage_absorbed`, `damage_resisted`. 바닐라 호출 지점: 이동 거리는 `ServerPlayer.checkMovementStatistics`(`aqv`, `avz.p`~`D`), `jump` 는 `Player.jumpFromGround`(`cmx`, `avz.E`), `damage_taken` 은 `Player.actuallyHurt`(`cmx`, `avz.J`), 피해 흡수·저항은 `cmx`(`L`)와 `LivingEntity`(`btn`, `H`·`I`·`M`). Furnace 는 `entity/PlayerVitals.java:1120-1135` `checkMovementStatistics` 가 허기 소모만 하고 통계는 안 올리며(주석: "허기 소모 부분"), `entity/VehicleMoves.java:68` 에 `TODO(통계): checkMovementStatistics`, `PlayerMelee.java:190` 의 `damage_dealt` 만 있다. |
| 재현 | `A12StatsReproTest` 4개(`walkingOneBlockAddsWalkOneCm`·`sprintingAddsSprintOneCm`·`jumpingAddsJump`·`takingDamageAddsDamageTaken`) — 기준 커밋에서 **모두 실패**(결과 0). |
| 영향 | 통계 화면(Esc → 통계)과 `stats/<uuid>.json` 에 걷기·점프 등이 항상 0 이다. 점수판 기준 `minecraft.custom:minecraft.walk_one_cm`·`jump`·`damage_taken` 이 영원히 0 이라 이 기준을 쓰는 서버(걷기 거리 순위·점프 횟수 대결·"피해 누적" 지표)가 통째로 동작하지 않는다. 데이터 팩·점수판 기반 콘텐츠에서 가장 눈에 띄는 차이다. |
| 제안 | **분기별로 바닐라 식을 그대로 옮긴다**(독립 검토가 `aqv`·`cmx` 바이트코드로 확인한 표). `ServerPlayer.checkMovementStatistics` 는 탈것을 타고 있으면 아무것도 안 하고, 아니면 아래 순서로 한 분기만 탄다.
  | 분기(순서대로) | 통계 | 거리 식 | 조건 |
  |---|---|---|---|
  | 헤엄 | `swim_one_cm` | 3차원 | 0 보다 클 때 |
  | 눈이 물속 | `walk_under_water_one_cm` | 3차원 | 0 보다 클 때 |
  | 물 안 | `walk_on_water_one_cm` | 수평(dx, dz) | 0 보다 클 때 |
  | 오르는 중 | `climb_one_cm` | dy 만, `Math.round(dy*100.0)` | dy 가 0 보다 클 때만 |
  | 땅 위 | `sprint_one_cm` / `crouch_one_cm` / `walk_one_cm` | 수평 | 0 보다 클 때, 달리기 다음 웅크리기 순서 |
  | 겉날개 | `aviate_one_cm` | 3차원 | 조건 없음 |
  | 그 밖(공중) | `fly_one_cm` | 수평 | **25 보다 클 때만** |
  `fall_one_cm` 은 이 함수가 아니라 `Player.causeFallDamage`(`cmx.a(float,float,brk)`)에 있고 낙하 거리 2 이상일 때 `Math.round(낙하 거리 * 100.0)`. 탈것 5종(`minecart`·`boat`·`pig`·`horse`·`strider`)은 `aqv` 의 다른 메서드(탑승 통계). `damage_taken`·`damage_absorbed` 는 `Math.round(값 * 10.0f)` 이고 `3.4028235E37` 미만일 때만. 점프는 `onMove` 의 점프 판정 자리(`:1097`). **완료 기준에 계단 오르기(수평만 셈)·사다리(dy 만)·공중 25 이하(안 오름)·낙하 2 미만(안 오름) 시험을 넣는다**(재현 시험은 걷기·달리기·점프·피해만 본다). 중간. Sonnet high. |

### 중간

| 칸 | 내용 |
|---|---|
| ID | **A12-05** |
| 심각도 | 중간 |
| 종류 | 바닐라 차이 |
| 근거 | `entity/Consuming.java:41-56`(`tryStart`)·`:118-176`(`finish`)에 음식·물약·우유·꿀·수상한 스튜의 `awardStat("minecraft:used:<아이템>")` 이 없다(`spyglass`·`ominous_bottle` 만 있음). 바닐라는 `Player.eat`(`cmx.a(dcw,cuq,cpr)`, `avz.c` 호출 확인)와 `PotionItem`/`MilkBucketItem`/`HoneyBottleItem` 의 `finishUsingItem` 이 `ITEM_USED` 를 올린다. |
| 재현 | 코드 추적(`grep "minecraft:used:"` 결과에 음식·물약 경로가 없음). 단위 시험은 만들지 않았다 — `Consuming.finish` 가 Region 을 요구한다. |
| 영향 | `minecraft.used:minecraft.bread`·`golden_apple` 같은 점수판 기준과 통계 화면의 "사용함"이 먹은 음식·마신 물약에 대해 0 이다(`used` 통계는 블록·도구·양동이 등 다른 경로에서는 오른다). |
| 제안 | `Consuming.finish` 의 음식·물약·우유·꿀 가지에서 한 번 `awardStat("minecraft:used:"+n)`. 먹는 중 취소되면 안 오른다(바닐라와 같음 — 완료 시점). 작음. Haiku/Sonnet low. |

| 칸 | 내용 |
|---|---|
| ID | **A12-06** |
| 심각도 | 높음(독립 검토 N2: 영향이 A12-02·03 과 같아 같은 등급) |
| 종류 | 바닐라 차이(기존 문서화) |
| 근거 | `docs/feature-parity/advancement-triggers-2026-10-28.md` "아직 미연결·미완": `effects_changed` 의 `source` 를 넘기지 않아 바닐라 발전 과제 `husbandry/kill_axolotl_target`("친구와 함께해요" — 아홀로틀이 적을 죽여 재생 효과를 받음)이 영영 안 뜬다. 바닐라 데이터가 `effects_changed` + `source` 에 `type: axolotl` 을 요구한다. |
| 재현 | 문서에 이미 있다. 이번에 바닐라 데이터로 해당 발전 과제가 `source` 조건 하나뿐임을 확인했다. |
| 영향 | 발전 과제 1개 달성 불가. |
| 제안 | 아홀로틀이 `ADD` 로 주는 효과 경로(`PlayerVitals.addEffect` 의 호출자)에 효과를 준 엔티티를 넘겨 `triggerEffectsChanged(source)` 에 전달. 작음~중간. Sonnet medium. |

| 칸 | 내용 |
|---|---|
| ID | **A12-07** |
| 심각도 | 낮음(독립 검토 R3 반영: 처음엔 중간으로 적었다) |
| 종류 | 바닐라 차이 + 성능 판단 |
| 근거 | `map/MapRenderer.java:22,56` — "바닐라는 청크를 불러 와(level.getChunk) 먼 열도 칠한다. 여기서는 그 region 이 가진 청크만 칠한다". 칠하는 반경은 바닐라 `MapItem.update` 와 같은 `128 / scale` **픽셀**이고 한 픽셀이 `scale` 블록이라, 범위는 **배율과 상관없이 든 사람 둘레 128블록(8청크, 천장 있는 차원은 64블록)** 이다. 같은 종류의 미구현이 `ExplorerMaps.java:15`(A12-08), `trade/VillagerTrades.java:4`. |
| 재현 | 코드 주석이 근거(시험 없음). 다른 region 소유 청크가 실제로 얼마나 자주 빠지는지는 재 보지 않았다(가정). |
| 영향 | 바닐라가 청크를 불러 오는 범위는 둘레 8청크 원(최대 약 200개)이고, 시야 거리가 8청크 이상이면 그 청크들은 이미 올라와 있어 차이가 거의 없다. 차이가 나는 경우는 시야 거리 8 미만인 서버, 막 도착해 둘레 청크가 아직 안 올라온 때, 둘레 청크가 다른 region 소유인 때로 좁다. 결과물을 바닐라와 같게 하려면 청크를 읽어야 하지만 서버 청크 부하와 맞서므로 **도미니코 결정**(user-todo)으로 남긴다. |
| 제안 | 결정에 따라: (a) 칠하기 위해 필요한 청크를 읽기 전용으로 비동기 요청해 다음 틱에 칠한다(생성은 안 함), (b) 지금대로 둔다. 중간(청크 읽기 경로). Sonnet high. |

### 낮음

| ID | 내용 | 근거·제안 |
|---|---|---|
| **A12-08** | 탐험 지도 전리품 함수·거래의 `skip_existing_chunks`(이미 지도가 가리킨 구조물 건너뛰기)가 없다. 같은 탐험 지도 상자를 여러 장 열면 바닐라는 서로 다른 구조물을 가리키고 Furnace 는 같은 구조물을 가리킨다. | `map/ExplorerMaps.java:15`, `trade/VillagerTrades.java:4`(구조물 시작점 references 증가 없음). 구조물 시작점 저장이 필요해 **큼**. |
| **A12-09** | 상자·상자 수레·단지를 열 때 `fillLoot` 가 플레이어 행운(`luck`)을 문맥에 안 넣는다. 바닐라 `RandomizableContainer.unpackLootTable` 은 `withLuck(player.getLuck())`. 바닐라 데이터에는 `bonus_rolls`(1,302개 전부 0.0)와 `quality`(낚시만)뿐이라 지금은 차이가 안 보이지만, 행운을 쓰는 데이터 팩 표에서는 어긋난다. 또 몹 처치 전리품의 `luck` 도 `ServerCore.java:1638` 에 `TODO(행운)`. | `ServerCore.fillLoot`(`:540` 근방) `params.luck(opener.luck())`. 작음. |
| **A12-10** | 서명된 책을 열 때 선택자·점수 텍스트 컴포넌트를 풀지 않는다(바닐라 `resolveBookComponents`). `/give` 로 만든 책의 `{"selector":"@p"}`·`{"score":...}` 페이지가 해석되지 않은 채 보인다. | `block/ItemUse.java:111` 주석 "미구현". 작음~중간(A13 텍스트 해석과 함께). |
| **A12-11** | `EntityPredicate`·`LocationPredicate` 가 모르는 필드를 만나면 조건 전체를 거짓으로 처리한다(경고 후). 바닐라는 파싱 오류로 그 데이터 파일을 거부한다. 지금은 모든 필드를 지원해(A12-01 의 `looking_at`·`stats` 제외) 바닐라 데이터에서는 드러나지 않지만, 데이터 팩 오타가 조용히 "절대 안 맞는 조건"이 되어 발전 과제·전리품 표가 죽는다. | `loot/EntityPredicate.java:101-105`, `loot/LocationPredicate.java:63-67`. 모든 필드를 구현한 뒤 바닐라처럼 `LootParseException` 으로 거부하고 옵션은 두지 않는다(절대 규칙 2, A13-01 과 같은 방침). 작음. |

### 참고

| ID | 내용 |
|---|---|
| **A12-12** | 위 "같음 확인" 표의 모든 항목(데이터 해석·연료·염료·레시피 3,385조합)은 이번 조사에서 일치했다. 이 부분은 다시 조사하지 않아도 된다. |
| **A12-13** | `Item.onCraftedBy` 훅이 `ConnectionHandler.onItemCrafted` 와 `StonecutterMenu:127` 주석에 `TODO` 로 남아 있다. 현재 `Item.onCraftedBy` 를 바꾸는 바닐라 아이템은 지도(`MapItem`)뿐이고, 그 처리(`MapItems.postProcess`)는 제작대·지도 제작대·제작기에 이미 연결되어 있다. 새 아이템이 생기는 버전업 때 다시 볼 것. |

---

## 같음 확인 (이번에 대조해서 일치한 것 — 다시 조사하지 않는다)

| 대상 | 방법 | 결과 |
|---|---|---|
| 제작 레시피 887개(모양 있음 634 + 없음 253) | 모든 레시피를 격자에 놓고 `findCrafting` → `assemble`, 태그 재료는 칸마다 구성원을 바꿔 가며 3,385조합 (`A12RecipeCraftAllProbeTest`) | 결과 아이템·개수 **불일치 0** |
| `update_recipes` 본문, 발전 과제 1,399개 적재·순서, 전리품 표 1,178개·상자 280건 | 기존 시험(`VanillaRecipesTest`·`VanillaAdvancementsTest`·`VanillaChestLootTest` 등) | 일치(이번에 다시 안 돌림) |
| 발전 과제 트리거 이름 | 바닐라 `an`(CriteriaTriggers) 57개 이름 대 `Criterion` 매처 | 57개 전부 매처 있음 |
| 전리품 함수 40·조건 19·항목 8·숫자 공급자 6 이름 | 바닐라 등록표 바이트코드 문자열 대 `loot/*.java` | 전부 있음 |
| 데이터 컴포넌트 이름 54 | 바닐라 `kq` 문자열 대 `item/DataComponents.java` | 전부 등록됨 |
| 연료표 59항목 | `dpv.g()` 바이트코드를 아이템·블록·태그 이름으로 풀어 대 `FuelRegistry` | **59/59 같음**(항목·틱 수) |
| 염료 텍스처·불꽃놀이 색 | `cti`(DyeColor) 대 `SpecialRecipes.TEXTURE/FIREWORK` | 같음 |
| 수리 레시피 | `RepairItemRecipe` 공식(5% 보너스·저주만 합침) 코드 대조 | 같음 |
| 바닐라 데이터 적재 경고 | 바닐라 전체 적재 시 경고 로그(`A12LoadWarningsProbeTest`) | 위 A12-01(3)·A12-02(1) 뿐 |
| 연결 지점이 있는 것 | 지도 확대·잠금·깃발·액자, 낚시 행운(+플레이어 행운), `mined`·`broken`·`picked_up`·`dropped`·`killed`·`killed_by`·`crafted` 통계 | 연결됨 |

## 재현 시험 쓰는 법

`docs/audit/findings/A12-repro/*.java.txt` 를 `.txt` 떼고 아래 위치에 두고 돌린다(저장소에는 커밋하지 않았다).

| 파일 | 놓는 곳(패키지) | 기대 |
|---|---|---|
| `A12PredicateReproTest` | `furnace-server/src/test/java/org/domi/furnace/server/loot/` | A12-01·02 를 고치면 통과로 바뀐다 |
| `A12BrushLootTriggerReproTest` | `.../server/block/` (`BrushingTest` 를 상속) | A12-03 |
| `A12StatsReproTest` | `.../server/stats/` | A12-04 (이동 거리 15종 중 걷기·달리기만 시험. 나머지는 수정 때 늘린다) |
| `A12RecipeCraftAllProbeTest` | `.../server/recipe/` (`CraftingMatchTest` 상속) | 통과·`PROBE recipes checked=3385 bad=0` 출력 |
| `A12LoadWarningsProbeTest` | `.../server/a12probe/` + `VanillaAdvancementsTestAccess`(advancement 패키지, 한 줄짜리 도우미) | 경고 3+1건 출력 |

```
./gradlew -PskipOracle=true --offline :furnace-server:test --tests '*A12*ReproTest*' -i
```

(`A12LoadWarningsProbeTest` 가 쓰는 `VanillaAdvancementsTestAccess` 는 `advancement` 패키지에 `public static AdvancementManager load() throws Exception { return VanillaAdvancementsTest.vanilla(null); }` 한 줄짜리 클래스를 두면 된다.)

## 수정 묶음 제안

| 묶음 | 항목 | 크기 | 모델·노력치 |
|---|---|---|---|
| 1. 술어(플레이어 필드) | A12-01(`looking_at`, `hasLineOfSight` 포함)·`stats`·A12-11 | 작음~중간 | Sonnet high |
| 2. 호출 지점 | A12-03·05·06 | 작음 | Sonnet medium |
| 3. 통계 | A12-04(분기별 바닐라 식, 위 표) | 중간 | Sonnet high (허기 소모와 같은 함수) |
| 4. 잔여 | A12-09·10 | 작음 | Haiku/Sonnet low |
| 5. 지도 청크 | A12-07 | 중간 | Sonnet high — 도미니코 결정 뒤 |
| 6. 지도 구조물 | A12-08 | 큼 | Opus high — 진행 중인 구조물 저장·보물 지도 작업과 맞춘 뒤 따로 연다 |

**아이템 하위 술어 14종(A12-02)은 A13 보고서(PR #213) 수정 묶음 1 에서 한 번만 고친다**(명령 인자와 공용 코덱, `jukebox_playable` 포함). A12 쪽에서 `loot/ItemPredicate` 를 따로 고치지 않는다. A12-11(모르는 필드 처리)의 방침도 같다: 14종·플레이어 필드를 모두 구현한 뒤에는 바닐라처럼 그 파일을 거부하고 옵션은 두지 않는다(절대 규칙 2).

충돌 주의: 묶음 3(`PlayerVitals.checkMovementStatistics`)은 A11 수정 묶음(낙하·피해 계산)과 같은 파일을 만진다. 묶음 1 은 A13 묶음 1 이 `loot/ItemPredicate` 를 먼저 고치면 `EntityPredicate`(같은 `loot` 패키지)와 충돌하지 않게 순서만 맞추면 된다. H4 묶음 C(값 타입)와는 겹치지 않는다.
