# 발전 과제 트리거·점수판 기준 연결 현황 (2026-10-28)

바닐라 1.21.1 `CriteriaTriggers` 56종 기준. "연결" = 게임 이벤트에서 `PlayerAdvancements.trigger*` 를 부르고 조건 매처가 있다.
호출 지점은 바닐라 디컴파일을 직접 읽어 대조했다(ServerPlayer, LivingEntity.die, Entity.awardKillScore, KilledTrigger, BredAnimalsTrigger, TradeTrigger, TameAnimalTrigger, Player.eat).

## 연결됨
| 트리거 | Furnace 호출 지점 | 바닐라 호출 지점 | 비고 |
|---|---|---|---|
| impossible | — | — | 늘 거짓(명령으로만) |
| tick | `PlayerAdvancements.tickPlayer` (Region 플레이어 틱), 접속 첫 틱 | ServerPlayer.tick | 기존 |
| location | `tickPlayer` 20틱마다 | ServerPlayer.doTick | 기존 |
| inventory_changed | ConnectionHandler 슬롯 변경 | ServerPlayer 컨테이너 리스너 | 기존 |
| recipe_unlocked | `Player.awardRecipes` | ServerRecipeBook | 기존 |
| recipe_crafted | 제작 결과 가져가기 | ResultSlot | 기존 |
| placed_block / item_used_on_block / default_block_use / any_block_use | BlockPlacement·ItemUse·ConnectionHandler | 각 아이템/블록 | 기존 |
| filled_bucket | ItemUse → `fireBlockTrigger` | BucketItem | **이번 수정**: 위치 문맥을 넘겨 매처가 ClassCastException 낼 수 있던 버그 → 채운 양동이 스택을 넘긴다 |
| slept_in_bed | PlayerSleeping | ServerPlayer.startSleepInBed | 기존 |
| changed_dimension | Region 차원 이동 | ServerPlayer.changeDimension | 기존 |
| player_killed_entity | **신규** `LivingEntity.die` → `KillCredit.award` | LivingEntity.die → ServerPlayer.awardKillScore | entity(this=상대, origin=플레이어) + killing_blow(태그·원인·직접 엔티티) |
| entity_killed_player | **신규** `PlayerVitals.die` / `KillCredit.award` | Entity.awardKillScore | 죽인 쪽이 몹이든 플레이어든 |
| consume_item | **신규** `Consuming.finish` (음식·물약·우유·꿀) | Player.eat, PotionItem 등 finishUsingItem | 줄이기 전 스택 |
| enter_block | **신규** `Region.checkInsideCampfires` 순회(공기 포함) | Entity.checkInsideBlocks → ServerPlayer.onInsideBlock | 수락된 이동이 있을 때만 순회(기존 모닥불 경로와 같음) |
| bred_animals | **신규** `Animal.finalizeBreeding` | Animal.finalizeSpawnChildFromBreeding | animals_bred 통계도 같이. 개구리·스니퍼는 child=null |
| tame_animal | **신규** `TamableAnimal.tame` | TamableAnimal.tame | 말류(AbstractHorse.tameWithName)는 미확인 |
| villager_trade | **신규** `MerchantMenu` 결과 가져가기(notifyTrade 직후) | AbstractVillager.notifyTrade 끝 | 결과 = offer.result() |

## 미연결 (이유)
| 트리거 | 이유 |
|---|---|
| used_totem | 불사의 토템 자체(checkTotemDeathProtection)가 없다 |
| effects_changed | 효과 추가/제거 경로가 여러 곳이고 원인 엔티티 전달이 없다. 근사 금지로 보류 |
| levitation, nether_travel, fall_from_height, ride_entity_in_lava | ServerPlayer 의 시작 위치·틱 기록 상태가 없다 |
| enchanted_item, brewed_potion, construct_beacon, used_ender_eye, summoned_entity, cured_zombie_villager, fishing_rod_hooked, item_durability_changed, channeled_lightning, shot_crossbow, killed_by_crossbow, hero_of_the_village, voluntary_exile, slide_down_block, bee_nest_destroyed, target_hit, player_generates_container_loot, thrown_item_picked_up_by_entity/player, player_interacted_with_entity, started_riding, lightning_strike, using_item, kill_mob_near_sculk_catalyst, allay_drop_item_on_block, avoid_vibration, crafter_recipe_crafted, fall_after_explosion, player_hurt_entity, entity_hurt_player | 매처(조건 파서)가 아직 없다(`default -> false`). 대부분 해당 게임 기능은 있으나 호출 지점별 사건 객체 설계가 필요. 다음 과제 |

## 보상
- 경험치·레시피: 기존 연결.
- 함수: **신규** `Host.runFunction` — 실행 주체(플레이어, 권한 2, 출력 없음)는 플레이어 region 스레드에서 만들고 실행은 전역 스레드(FunctionManager 전용)로 넘긴다. 바닐라는 award 안에서 바로 실행 → **한 전역 틱 늦을 수 있음(차이)**.
- 전리품: 아직 인벤토리에 넣지 않는다(경고 로그). 미연결.

## 점수판 기준
| 기준 | 상태 |
|---|---|
| health, food, air, armor, xp, level | 기존(PlayerVitals 틱, 바닐라 doTick 과 같은 비교) |
| deathCount | 기존(PlayerVitals.die) |
| totalKillCount, playerKillCount | **신규** KillCredit.award |
| teamkill.<색>, killedByTeam.<색> | **신규** handleTeamKill 두 번(바닐라 순서) |
| minecraft.killed:<종류> | **신규** LivingEntity.die 의 Player.killedEntity 자리 |
| minecraft.killed_by:<종류> | **신규** PlayerVitals.die |
| custom: mob_kills, player_kills, deaths, animals_bred | **신규**. time_since_death·time_since_rest 는 사망 시 0(resetStat) |
| ServerPlayer.increaseScore(deathScore) | 생략 — 바닐라 deathScore 는 쓰는 곳이 없어 0(가정: 디컴파일에서 대입을 못 찾음) |

## 스레드
- PlayerAdvancements·PlayerStats·Scoreboard 는 synchronized. 몹이 다른 region 에서 죽을 때(화살 등) 킬러 플레이어의 진행은 몹 region 스레드에서 갱신된다 — 락으로 안전하지만 보상(경험치 지급·패킷)이 플레이어 소유 스레드 밖에서 일어날 수 있다. **남은 위험**.
- 직접 원인 엔티티: LivingEntity 는 die 시점의 pendingDirect 를 쓴다. 플레이어 사망은 hurt 의 causing/direct 를 넘긴다.
- Animal.loveCausePlayer 는 `level.getTargetableByUuid` — 사랑 원인 플레이어가 다른 region 이면 null(바닐라는 레벨 전체에서 찾음). 차이.

## 같이 고친 것
- `EntityPredicate` 의 `distance`(바닐라 DistancePredicate, float 차이·제곱 비교) 지원 — 전에는 unsupported 라 저격 대결 등이 영영 불가였다.
- `ObjectiveCriteria.byName` 통계 기준 첫 생성 시 `ConcurrentHashMap` Recursive update 예외(생성자가 CACHE 에 넣음) 수정.

## 시험
`AdvancementTriggersTest`(바닐라 데이터가 있을 때): 처치(entity·killing_blow·거리), 사망, 번식·길들이기·거래·먹기·양동이·enter_block 달성,
이미 이룬 조건 재발생 시 패킷 없음, 저장·재로드 후 듣는 쪽 미등록, 처치 점수 기준 5종.
