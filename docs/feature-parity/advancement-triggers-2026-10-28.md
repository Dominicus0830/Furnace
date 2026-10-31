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
| consume_item | **신규** `Consuming.finish` | Player.eat(음식 회복 뒤·효과 전), Potion/Milk/Honey finishUsingItem(고유 효과 전) | 꿀은 바닐라처럼 두 번. 두 번째도 줄이기 전 스택을 넘긴다(바닐라는 줄인 뒤 — 개수 조건만 차이) |
| enter_block | **신규** `Region.checkInsideCampfires` 순회(공기 포함) | Entity.checkInsideBlocks → ServerPlayer.onInsideBlock | 수락된 이동이 있을 때만 순회(기존 모닥불 경로와 같음) |
| bred_animals | **신규** `Animal.finalizeBreeding` | Animal.finalizeSpawnChildFromBreeding | animals_bred 통계도 같이. 개구리·스니퍼는 child=null |
| tame_animal | **신규** `TamableAnimal.tame`, `AbstractHorse.tameWithName` | 같음 | 말은 하트 이벤트 7 전에 |
| villager_trade | **신규** `MerchantMenu` 결과 가져가기(notifyTrade 직후) | AbstractVillager.notifyTrade 끝 | 결과 = offer.result() |

## 2026-10-31 추가 연결 (작업 E)

호출 지점은 바닐라 1.21.1 디컴파일(`an` = CriteriaTriggers 의 각 `an.<필드>.a(` 호출)에서 같은 시점·인자로 옮겼다. 모든 호출은 `Player.triggerAdvancement`(= `runOnOwner`)로 플레이어 주인 스레드에서 돈다. 상대 엔티티 값은 부르는 스레드에서 `KillCredit.lootOf` 로 떠서 넘긴다.
새 사건 종류·매처는 `Criterion`, 호출 메서드는 `PlayerAdvancements.trigger*`, 도우미는 `entity/AdvTriggers`.

| 트리거 | Furnace 호출 지점 | 바닐라 호출 지점 / 비고 |
|---|---|---|
| construct_beacon | `BeaconBlockEntity.tick` 활성화 순간 | 비콘 아래 4·옆 10·위 5 상자의 플레이어에게 단수 |
| enchanted_item | `EnchantmentMenu.clickMenuButton` (통계 다음, setChanged 전) | 마법 부여된 스택 + 비용 |
| brewed_potion | `BrewingStandMenu.PotionSlot.onTake` | 꺼낸 물약의 potion |
| summoned_entity | `ChunkLevel.spawnWither`(50칸, 소환 전)·`spawnGolem`(5칸), `EndDragonFight.setRespawnStage(END)` | 보스 바를 보는 플레이어 |
| cured_zombie_villager | `ZombieVillager.finishConversion` | 평판 이벤트 직전 |
| item_durability_changed | `BlockBreaking.hurtAndBreak`·`Shields.hurtAndBreak`·`EnchantEffects.damageEquipment`·`Player.hurtArmor` | 깎기 전 스택 + 새 피해 값(durability = 최대 - 새 피해, delta = 지금 피해 - 새 피해) |
| player_hurt_entity / entity_hurt_player | `LivingEntity.hurtBy` 끝, `PlayerVitals.hurt` 끝 | dealt = 막기 전 피해, taken = 막은 뒤(막으면 0), blocked |
| levitation | `PlayerAdvancements.tickPlayer(TickState)` (`Region` 플레이어 틱) | 시작 위치·지속 틱, 매 틱 |
| nether_travel | `ConnectionHandler.applyDimensionChange`(기록)·`Region.adoptAfterDimensionChange`(발동) | 네더 → 오버월드 도착 때 |
| fall_from_height / fall_after_explosion / ride_entity_in_lava | `tickPlayer(TickState)` | 아래 차이 참고 |
| effects_changed | `PlayerVitals.addEffect`·`removeEffect`·`removeAllEffects`·효과 만료 | source 는 아직 null |
| fishing_rod_hooked | `FishingHook.retrieve` | 걸린 엔티티가 있으면 그것을, 없으면 낚시찌를 문맥 엔티티로 |
| channeled_lightning | `LightningBolt.tick` | `ThrownTrident` 가 `setCause`(소유 플레이어) |
| lightning_strike | `LightningBolt` 끝 | 번개 256칸 안 모든 플레이어, 15칸 안 안 맞은 것이 구경꾼 |
| shot_crossbow | `RangedWeapons.performShooting` | |
| killed_by_crossbow | `AbstractArrow.onHitEntity` | 관통이면 지금까지 죽인 목록, 아니면 이번 대상 |
| hero_of_the_village / voluntary_exile | `Raid` | |
| started_riding | `Entity.mount` 끝 | 탈것 + 승객 전체의 플레이어 |
| slide_down_block | `Region.checkInsideCampfires` 순회 | 꿀 블록, 20틱마다 |
| bee_nest_destroyed | `BeehiveBlock.playerDestroy` | 내보낸 뒤의 남은 벌 수 |
| target_hit | `TargetBlock.onProjectileHit`(새 `Projectile.onHitBlock` → `BlockBehavior.onProjectileHit` 배선) | 통계 target_hit 도 같이 |
| player_generates_container_loot | `ServerCore.unpackLoot`(블록 용기·엔티티 용기) | 전리품을 만들기 전 |
| thrown_item_picked_up_by_entity / by_player | `Mob.onItemPickup`, `ItemEntity.playerTouch` | 던진 플레이어를 서버 전체에서 UUID 로 찾는다 |
| player_interacted_with_entity | `ConnectionHandler.handleInteractEntity` | 행동을 소비한 경우 |
| using_item | `Consuming.tick` | 쓰는 동안 매 틱 |
| kill_mob_near_sculk_catalyst | `SculkCatalystBlockEntity.CatalystListener` | |
| allay_drop_item_on_block | `GoAndGiveItemsToTarget.tick` | 좋아하는 플레이어에게 |
| avoid_vibration | `VibrationSystem` 진동 판정 | 조심스레 걸을 때 |
| crafter_recipe_crafted | `CrafterBlock.dispenseItem` | 17×17×17 안 플레이어, 월드에 내놓은 아이템마다 |

## 아직 미연결
56종 모두 호출 지점이 있다(위 "연결됨"의 기존 항목 + 이번 추가). 다만 아래 "차이·한계"의 근사를 읽을 것. 기계 자체가 없어 못 이루는 경우:
유리병으로 드래곤의 숨결 구름 뜨기(`ItemUse.glassBottleUse` TODO)에서 오는 player_interacted_with_entity, 효과를 준 쪽(source)이 필요한 effects_changed 의 `source` 조건, 철퇴 내려치기 뒤 fall_after_explosion 의 cause 조건.
(used_totem·used_ender_eye·impossible 은 위 "연결됨"/기존 코드로 이미 호출 지점이 있다.)

## 차이·한계 (확인 못 한 것은 '가정')
- levitation: 효과를 건 순간이 아니라 그 틱 끝에 시작 위치를 잡는다(최대 한 틱 어긋남).
- fall_from_height: 바닐라는 `resetFallDistance` 호출 순간, 여기서는 다음 틱 끝에서 낙하 거리가 0 이 된 것을 보고 부른다. 같은 틱 안에서 시작·끝나는 낙하는 바닐라처럼 안 센다.
- fall_after_explosion: 폭발 원인은 `PlayerVitals.onExplosionHit(source)` 가 있는 경로만(윈드 차지 등). 철퇴 내려치기는 원인을 기록하지 않아 cause 조건이 거짓.
- effects_changed: 효과를 준 엔티티(source)를 아직 넘기지 않는다 → `source` 조건이 있는 발전 과제는 못 이룬다. effects 조건은 정상.
- player_interacted_with_entity: 소비한 상호작용은 늘 SUCCESS(통계 대상)로 보고 손에 든 사본을 넘긴다(가정). 서 있는 갑옷 거치대 `interactAt`·끈 매듭 등 일부 경로는 연결하지 않았다.
- item_durability_changed: 부서질 때 새 피해 값은 `max(피해, 최대 내구)`(EnchantEffects 경로, 가정). 몹의 장비(`Mob`)는 바닐라도 플레이어만 부른다.
- 다른 region 에서 일어난 사건은 플레이어 주인 region 의 다음 플레이어 틱에 반영된다(기존 차이와 같다).
- player_generates_container_loot: 브러시 블록(`dql`)은 솔질 작업자 영역이라 연결하지 않았다.
- 엔티티 값(effects·nbt·vehicle 등)을 다른 region 의 엔티티에서 떠 올 때 `KillCredit.snapshot` 이 미리 복사한다. NBT 는 nbt 조건이 데이터 팩에 읽힌 적이 있을 때만(`EntityPredicate.nbtUsed`) 복사한다. `slots` 조건은 원본을 그대로 본다(가정: 한 틱 안 불변).
- 플레이어의 `is_on_fire`·`is_baby` 같은 플래그는 기존대로(불 상태 없음).

## 보상
- 경험치·레시피: 기존 연결.
- 전리품: **고침** — `ServerCore` 의 `Host.giveLoot` 가 바닐라 AdvancementRewards.grant 대로 표를 ADVANCEMENT_REWARD 문맥(this_entity = 플레이어, origin = 플레이어 위치)으로 굴려 인벤토리에 넣고(들어가면 픽업 소리 0.2·플레이어 난수), 못 넣은 분은 줍기 지연 없이 그 플레이어만 줍게 던진다. 하나라도 들어가면 열린 창을 동기화한다. 순서는 경험치 → 전리품 → 레시피 → 함수.
- 함수: `Host.runFunction` — 실행 주체(플레이어, 권한 2, 출력 없음)는 플레이어 region 스레드에서 만든다. **전역 스레드에서 부여했으면(`/advancement grant` 등) 바닐라처럼 award 안에서 바로 실행**하고, region 스레드에서 부여했으면 전역 스레드(FunctionManager 전용)로 넘겨 다음 전역 틱에 실행한다(차이 — 함수 관리자가 전역 스레드 전용이라 같은 틱 보장은 못 한다).

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
- 플레이어 쪽 상태(진행도·통계·점수판·보상)는 `Player.runOnOwner` 로 플레이어 주인 region 스레드에서 바꾼다: 지금 스레드가 그 플레이어를
  가진 region 이면 바로, 아니면 큐에 넣고 주인 region 의 다음 플레이어 틱(`drainOwnerTasks`)에 실행. 처치·번식·길들이기가 이 경로를 쓴다.
  상대 엔티티 값은 죽는 쪽 스레드에서 `KillCredit.snapshot` 으로 떠 둔다(장비·typeSpecific 은 원본 참조 — 가정: 한 틱 안 불변).
- 다른 region 에서 일어난 처치는 바닐라보다 최대 한 틱 늦게 반영된다(차이).
- 길들인 늑대가 때리면 주인 플레이어가 kill credit(lastHurtByPlayer)을 받는다(바닐라 LivingEntity.hurt).
- 플레이어 사망 쪽(deathCount·deaths·killed_by)은 기존 사망 경로와 같은 스레드에서 처리한다(기존 그대로).
- Animal.loveCausePlayer 는 `level.getTargetableByUuid` — 사랑 원인 플레이어가 다른 region 이면 null(바닐라는 레벨 전체에서 찾음). 차이.

## EntityPredicate 지원 필드
지원: type, flags(is_on_fire·is_sneaking·is_sprinting·is_swimming·is_on_ground·is_baby), equipment, type_specific(일부 종류), location, distance,
**2026-10-31 추가**: effects(amplifier·duration·ambient·visible), nbt(저장 NBT 부분 일치, 플레이어는 SelectedItem 포함), vehicle, passenger(하나라도), targeted_entity(Mob.getTarget),
stepping_on(서 있는 칸 중심), movement_affected_by(이동에 영향 주는 아래 칸 아래 중앙), movement(속도×20: x·y·z·speed·horizontal_speed·vertical_speed·fall_distance), team, slots(슬롯 범위 이름 → 하나라도), periodic_tick.
엔티티 값은 `entity/LootEntities`(살아 있는 값) 와 `KillCredit.snapshot`(다른 스레드용 사본)이 `LootEntity` 의 새 메서드로 돌려준다. 알 수 없는 필드는 여전히 조건 전체가 거짓(경고).
차이(가정): 플레이어의 속도는 서버가 아는 변위(`serverMovement`)라 바닐라 `getKnownMovement` 와 비슷한 근사. 슬롯 범위 중 서버에 칸이 없는 것(말·주민 전용 등)은 빈 스택으로 본다.

## 같이 고친 것
- DamageSourcePredicate is_direct: 원인·직접 엔티티가 둘 다 없으면 바닐라처럼 직접(참).
- entity_killed_player: 자살이면 일으키지 않는다(바닐라 killed != this 검사가 super 호출을 감싼다).
- Region 자동 저장: 퇴출이 예산을 다 써도 틱마다 최소 1개 저장, `saveDirtyChunks` 의 IO 밀림 분기에서 null 큐 NPE 수정.
- `EntityPredicate` 의 `distance`(바닐라 DistancePredicate, float 차이·제곱 비교) 지원 — 전에는 unsupported 라 저격 대결 등이 영영 불가였다.
- `ObjectiveCriteria.byName` 통계 기준 첫 생성 시 `ConcurrentHashMap` Recursive update 예외(생성자가 CACHE 에 넣음) 수정.

## 시험
`AdvancementTriggersTest`(바닐라 데이터가 있을 때): 처치(entity·killing_blow·거리), 사망, 번식·길들이기·거래·먹기·양동이·enter_block 달성,
이미 이룬 조건 재발생 시 패킷 없음, 저장·재로드 후 듣는 쪽 미등록, 처치 점수 기준 5종.
- **작업 E**: `AdvancementTriggerWiringTest` — 바닐라 데이터 없이 임시 데이터 팩의 발전 과제 JSON 으로 트리거 36종의 사건 → 조건 비교 → 달성(맞는 경우·틀린 경우 쌍), 엔티티 조건 새 필드 통과·불일치 13가지, `TickState` 로 levitation·fall_from_height(죽었을 때 제외)·fall_after_explosion·ride_entity_in_lava, 보상 순서(경험치 → 전리품 → 함수).
