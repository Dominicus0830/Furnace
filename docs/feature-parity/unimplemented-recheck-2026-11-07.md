# 미구현 목록 재대조 (M13, 2026-11-07)

`unimplemented-survey-2026-10-31.md`(조사 4건)와 `mob-ability-audit-2026-10-31.md`(몹 능력 표)를 현재 `claude/quirky-wright-lra834`(PR #79 병합 시점) 코드와 대조했다.

## 방법과 한계
- **코드를 읽고 grep 한 것이지 바닐라 jar 와 줄 단위로 대조한 것이 아니다.** "구현됨"은 "구현 코드와 그것을 부르는 시험이 있다"는 뜻이다(시험 클래스 이름: `WolfVariantArmorTest`, `AllayListenerTest`, `LeashTest`, `FireballDeflectTest`, `ChargedCreeperSkullTest`, `ParrotShoulderTest` 등을 근거로 삼았다). 이 문서를 쓰며 시험을 새로 돌리지는 않았다.
- 소스에 남은 `TODO` 179줄을 전부 읽고 분류했다. 파일 머리 TODO 는 낡은 것이 많다(아래 2장).
- 이 문서의 M13 이후 새 묶음은 **M14~M22** 로 번호를 붙였다. M1~M12 에 이미 있는 줄은 중복으로 만들지 않았다(`work-packages-2026-11-06.md` 6.3 절).
- 코드 변경은 낡은 주석 3줄(Creeper·Zombie·Guardian)뿐이다.

## 1. 이미 구현돼 닫아도 되는 줄

### 종합 조사 (`unimplemented-survey-2026-10-31.md`)
| 원래 줄 | 현재 근거 |
|---|---|
| 1장 3 플레이어 이동 서버 검증·`allow-flight` | `PlayerMoveValidator`(+시험)가 `moved too quickly`·비행 판정 |
| 1장 4 블록 파괴 속도에 성급함 등 | `BlockActor` 에 `HASTE` 처리 있음(물속·피로 세부는 미확인) |
| 1장 5 근접 전투·철퇴·살충 | `PlayerMelee`, `MaceSmash`, `MaceEnchantTest`. (힘·나약함·쓸기 파티클·철퇴 착지 입자는 M6 에서 바이트코드 대조로 닫음 — `context-notes.md` "M6") |
| 1장 6 인챈트(가시·물갈퀴·서리 걸음·영혼 가속·호흡·민첩한 웅크리기) | `EnchantEffects`, `EnchantAttributes`, `PlayerLocationEffects`, `PlayerEnchantAttributeTest` |
| 1장 7 명령 블록·수레, `set_command_block`, `enable-command-block` | `CommandBlock(Entity)`, `ConnectionHandler`, `ServerCore` 소비, `CommandBlockTest` |
| 1장 8 시련 스포너·금고 | `TrialSpawnerBlock/Data`, `VaultBlock/Entity`, 시험 |
| 1장 9 솔질 블록 | `BrushableBlock`, `BrushingTest` |
| 1장 11 RCON | `remote/RconServer`, `RconConsole`, 시험 (Query 는 미확인) |
| 1장 13~14 플러그인 이벤트/타입, 스레드 현황·워치독 | `plugin/` 패키지 확대, `ServerWatchdog`, `/tps`, `profiling`, region 정보 API |
| 1장 2 셜커 상자 내용물 | `LootParams.BLOCK_ENTITY` 있음 (재현 시험은 없음 → 가정) |
| 2장 블록 엔티티 structure_block·jigsaw·end_gateway·conduit | 각각 `block/entity/*BlockEntity` 와 패킷 처리(`set_structure_block` 등) 있음 |
| 2장 화살이 버튼을 누름 | `ButtonBlock.entityInside` → `checkPressed` |
| 2장 붉은 돌 광석 `stepOn` | `RedStoneOreBlock` |
| 2장 아이템 사용: 의심스러운 스튜·불길한 병·염소 뿔·뒤틀린 균사 낚싯대 | `Consuming`, `ItemEffects`, `ItemUse`, `Strider` |
| 3장 패킷 수신: `move_player_status_only`, `teleport_to_entity`, `change/lock_difficulty`, `chunk_batch_received`, `block_entity_tag_query`, `entity_tag_query`, `set_structure_block`, `edit_book` | 모두 `ConnectionHandler` 에서 처리 |
| 3장 패킷 송신: `set_entity_link`, `hurt_animation`, `sound_entity`, `server_links`, `resource_pack_*`, `delete_chat`, `player_look_at`, `set_simulation_distance`, `set_chunk_cache_radius` | 해당 패킷 클래스·송신 지점 있음 |
| 3장 `execute on leasher`, 가죽끈 | `Mob.getLeashHolder`, `LeashTest/RulesTest` |
| 3장 `/item` 슬롯(말·주민 등), `/teammsg` | `SlotRanges`(`armor.body`, `villager.`…), `TeamMsgCommand` 있음(채팅 필터만 TODO) |
| 3장 서버 키 `spawn-npcs`, `max-tick-time`, `max-world-size`, `region-file-compression`, `text-filtering-config`, `bug-report-link`, `broadcast-rcon-to-ops`, 리소스 팩, 자리 비움 킥 | 각 키를 `ConnectionHandler/ServerCore/FurnaceMain/...` 이 소비, `PlayerIdleTest` |
| 3장 게임룰 `commandBlockOutput`, `spawnChunkRadius` | `BlockCommandSink` 가 소비, `spawnChunkRadius` 는 `Config`·`ConnectionHandler` 사용 |
| 4장 스폰 청크 상시 로드 | `Config.spawnChunkRadius` |
| 4장 마을 포위·방랑 상인 타이머 | 이미 닫힘(`VillageSiege`, `WanderingTraderState`) |

### 몹 능력 표 (`mob-ability-audit-2026-10-31.md`)
| 원래 줄 | 현재 근거 |
|---|---|
| Wolf 변종 | `Wolf.variantName/variantIndex`, `WolfVariantArmorTest` |
| Wolf 늑대 갑옷 | `Wolf.mobInteract` 에 `wolf_armor` 장착, 가위, 수리 / `WolfVariantArmorTest` |
| 모든 Mob 가죽끈 | `LeashTest`, `LeashRulesTest`, `LeashRemainderTest`, `FenceBlock` 매듭 |
| Ghast/Fireball 되치기 | `FireballDeflectTest`, `Projectile.deflect` |
| Allay 음표 블록·주크박스 수신 | `AllayListenerTest`, `AllayAi` 일부(음표 호감 기억은 TODO 로 남아 있어 **확인 필요**) |
| 몹 게임 이벤트 | `ENTITY_DAMAGE`(`LivingEntity`), `PRIME_FUSE`(`Creeper`), `TELEPORT`(`Enderman`), `SHEAR`(양·무시룸·눈 골렘·`Bogged`), `EAT`(여우·양·판다·낙타), `ENTITY_ACTION`(낙타·거북), `EXPLODE`(`Explosion`), `MobGameEventsTest`/`EntityGameEventsTest`. `DRINK`·`ENTITY_PLACE`(닭 알)·`ENTITY_ACTION`(늑대 털기)·셜커 이벤트는 grep 으로 못 찾음 → M14 |
| 충전 크리퍼 머리 떨구기 | `Creeper.dropSkullIfCharged` 를 Zombie·Skeleton·WitherSkeleton·Piglin 이 호출, `ChargedCreeperSkullTest` (낡은 주석은 이번에 고침) |
| 길들인 펫 사망 메시지 | `PetDeathMessageTest` |
| Pig/Strider 타기 | `PigRidingTest`, `PigCarrotStickTest`, `StriderTest` |
| Spider 거미줄 | 2026-11-02 구현(이전 기록) |
| Breeze 투사체 튕김 | `Breeze.projectileDeflection`, `Projectile.Deflection` |
| Parrot 어깨 | `ParrotShoulderTest`, `PlayerShoulders` |
| Ravager 기절 | `Ravager` 에 `StunTick` 저장·처리 |
| Vindicator Johnny, Shulker 방어 공격(대상 3번), 킬러 토끼 이름 | 각 파일에 구현(`Vindicator`, `Shulker`, `Rabbit`). 남은 `MobRemainderTest` 가 시험 |
| Dolphin 플레이어와 헤엄·보물 | `Dolphin.DolphinSwimWithPlayer…`, `DolphinTreasurePlayerTest` |
| Trader Llama 상인 보호 목표 | `TraderLlama` 에 `TraderLlamaDefend…` 있음(머리 TODO 는 사라짐 시간·나이 0 쪽만) |
| Giant/Illusioner/Evoker/Pillager 습격 연동 | `Raid`, `RaiderGoalsTest` 가 있어 다수 닫혔을 가능성 → M4 가 확정 |

## 2. 낡은 주석 (이번에 고친 것과 남은 것)
- 고침: `Creeper.java:2`(충전 크리퍼 머리 떨구기는 구현됨), `Zombie.java:239`(드라운드 변환은 구현됨), `Guardian.java:2`(아홀로틀 표적은 구현됨).
- 코드와 맞는지 확인되지 않아 **그대로 둔 것**: `Cat.java:2`(스웜프 오두막 변종은 `Cat.java:163` 에 있어 낡았음 — 다음에 만지는 사람이 지운다), `WitherBoss.java:3`(`WitherSkullBlock` 이 있음), `Warden.java:3`/`IronGolem.java:2`/`Slime.java:2`(doPush 훅이 아직 없다는 글 — 코드에도 `Warden`·`IronGolem` 에 훅이 없고 `PushEntitiesTest` 는 끼임 규칙만 보는 것으로 보여 낡지 않았을 것). 
- `Pufferfish.java:2` 는 낡지 않았다(근사 구현).

## 3. 아직 비어 있는 것 — M1~M12 에 이미 있는 것
영혼 가속/착지 입자·워든 진동 입자(M1), 스컬크(M2), 컨테이너·수레·엔티티 창·용기 전리품(M3), 습격(M4), 주민(M5), 플레이어 전투 파티클·효과(M6), 드래곤(M7), 스포너·벌집·표지판·지도 깃발(M8), 수면(M9), 다차원(M10), 명령(M11), 청크 전송(M12)은 각 묶음이 맡는다. 아래는 **어느 묶음에도 없는 것**이다.

## 4. 새 묶음 제안 (M14~M22)
같은 파일을 만지는 쌍은 맨 오른쪽에 적었다. 모델·노력치는 사용자 규칙(복잡한 서버 로직 Opus high, 일반 Sonnet medium~high)에 맞췄다.

| ID | 내용 | 근거(코드) | 모델 | 충돌·선행 |
|---|---|---|---|---|
| M14 | **완료(2026-11-07, 8절)** 몹 후속 잔여: Spider 그룹 효과(`SpiderEffectsGroupData` 없음 — 4마리가 각자 굴림), Slime/IronGolem/Warden `doPush` 훅과 슬라임의 철 골렘 피해, 늑대 털기·닭 알·마녀 `DRINK`·셜커 게임 이벤트, Fox(자는/웅크린 대상 제외·마을 제외·가루눈), Bogged 깎기 전리품(`shearing/bogged`), Pufferfish 접촉 패킷, Sniffer 길찾기 훅·연출, Camel 안장 칸·질주·2인 탑승, Llama 카펫 장식·탑승, Trader Llama 사라짐 시간, Wandering Trader 8칸 인벤토리, Piglin/Hoglin 아기 탑승, Phantom 하악질, Axolotl 밀기, Creeper 효과 구름 | `Slime.java:2`, `IronGolem.java:2`, `Warden.java:3`, `Spider.java`, `Fox.java:475,730,780`, `Bogged.java:51`, `Sniffer.java:2`, `Camel.java:2,138`, `Llama.java:2`, `TraderLlama.java:2`, `WanderingTrader.java:3`, `Piglin.java:2` | Sonnet medium | M3(Camel·Llama 창)·M4(Piglin 아님)와 `Mob.java` 충돌 가능 → M3 뒤 |
| M15 | **완료(2026-11-07, `m15-step-sounds-2026-11-07.md`)** 걸음 소리 전반(블록 SoundType 별 걸음·헤엄 소리, 거북·스트라이더·낙타·아르마딜로 특수 소리, 플레이어 `hit_ground`) | `Entity.java:1002`, `PlayerMovementEmission.java:4`, `Turtle.java:2`, `Strider.java:169`, `Camel.java:2`, `Armadillo.java:2` | Sonnet medium | M1(파티클·게임 이벤트 코드)과 `Entity.java` 겹침 → M1 뒤 |
| M16 | **완료(2026-11-07, context-notes "묶음 M16")** 팀·PvP 규칙 연결: `isAlliedTo`(타게팅 조건·목표 지속 조건·화살·늑대), `pvp` 끔일 때 서로 못 때림, `/teammsg` 채팅 필터, 피해 종류 태그(`is_fire`·`is_explosion`)를 전리품 조건에 연결 | `TargetGoal.java:35`, `TargetingConditions.java:54-55`, `Wolf.java:389`, `AbstractArrow.java:179`, `PlayerMelee.java:4`, `TeamMsgCommand.java:57`, `LootCommand.java:438`, `ServerCore.java:1253` | Sonnet high | 단독(다른 묶음과 파일 안 겹침) |
| M17 | **완료(2026-11-07, context-notes "묶음 M17")** 블록 잔여: 비교기가 아이템 액자 신호를 읽음, 노트 블록 시드·머리 블록 엔티티 소리, 꾸며진 항아리 실크 터치·전리품, 주크박스/연단 드롭 위치, 부싯돌·가위 내구도·통계(TNT·호박), ~~벌집 크리에이티브 드롭~~(M8 에서 함), 양조기 드롭 위치, 모래 같은 낙하 블록 엔티티 데이터, 분말눈 충돌 모양(엔티티별), 화살이 누르는 버튼 외 투사체 반응(TNT·양초는 구현됨) | `ComparatorBlock.java:73`, `NoteBlock.java:149-153`, `DecoratedPotBlock.java:91,113`, `LecternBlock.java:67`, `TntBlock.java:76`, `PumpkinBlock.java:41`, `BrewingStandBlockEntity.java:67`, `FallingBlockEntity.java:3`, `PowderSnowBlock.java:2` | Sonnet medium | M8(블록 엔티티)와 일부 겹침 → M8 뒤 |
| M18 | 아이템 사용 잔여: 스폰 알·양동이·수레의 `entity_data` 컴포넌트(`createDefaultStackConfig`), 드래곤 숨결 병에 담기, 디스펜서 설치(`DispenseBehaviors` 일부), 표지판 밀랍, 다중 발사·불꽃 로켓 장전, 음식·활·방패 `startUsingItem` 플래그, 화로 경험치 구슬, 모루 기본 이름 비교, 이름표 붙인 수레·배의 아이템 이름, 번들 클릭 | `ItemUse.java:121,219,374,553,576,950,1083`, `ChunkLevel.java:1627`, `Crossbow.java:2`, `FurnaceMenu.java:56`, `AnvilMenu.java:151`, `AbstractContainerMenu.java:347`, `OminousItemSpawner.java:2` | Sonnet high | M3(메뉴)·M8과 `ItemUse` 겹침 → M3 뒤 |
| M19 | 서버 옵션·운영 잔여: `spectatorsGenerateChunks` 게임룰, `enable-jmx-monitoring`, `/perf` 명령, 월드 타입 `debug_all_block_states`·`single_biome_surface` 생성기(현재 `WorldPreset` 에 이름만, 생성기 null), `trial_omen`·`oozing` 등 남은 효과 검증, 전리품 조건 `EntityPredicate`·`LocationPredicate` 모르는 필드와 전리품 함수 15종 | `world/GameRules`(소비 0), `ServerProperties`(`enableJmx` 소비 0), `world/storage/WorldPreset.java:11-12`, `loot/LootCondition.java`, `loot/LootFunction.java` | Sonnet medium | 단독 |
| M20 | 플레이어 자세·공유 플래그: 수영·기어가기 자세, 공유 플래그(불·수영·투명·발광), 겉날개 외 자세 전환, 탑승 중 낙하 피해·회전, 비행 킥(`multiplayer.disconnect.flying`), 시선 보정(`getVisibilityPercent` 투명·머리 아이템) | `Player.java:376,705,938,941`, `Entity.java:647,723`, `VehicleMoves.java:60`, `LivingEntity.java:619,1176` | Sonnet high | M9(수면 자세)와 `Player.java` 겹침 → M9 뒤 |
| M21 | **완료(2026-11-07, context-notes "묶음 M21")** 이동·길찾기·보더: 길찾기 노드 평가기 연결(`MoveControl` strafe 확인), 승객 비용 상속(스트라이더), 월드 보더를 반영한 하차 위치(`DismountHelper`), 포탈 직후 수레·낙하 블록 처리 | `MoveControl.java:59`, `Strider.java:173`, `DismountHelper.java:16,130`, `AbstractMinecart.java:208` | Opus high | M10(차원 이동)과 겹침 → M10 뒤 |
| M22 | 엔티티 NBT·표시 엔티티: `Display` 변환 행렬·axis_angle·`text` 읽기·쓰기·정렬·스타일, `Interaction` 의 플레이어 공격/우클릭 기록, `AreaEffectCloud` 입자 NBT·`canBeAffected`, `ArmorStand` 입자·이름, `Enderman` 투사체 텔레포트, `MinecartTNT` 불화살, `AbstractArrow` 박힘 수·줍기, 낚시 행운(플레이어 `luck` 속성) | `Display.java:2,187`, `Interaction.java:2`, `AreaEffectCloud.java:3`, `ArmorStand.java:3`, `Enderman.java:2`, `MinecartTNT.java:3`, `AbstractArrow.java:2`, `FishingHook.java:441` | Sonnet medium | 단독 |

## 5. 이 문서에서 닫지 못한 것(가정)
- 구조물 순정 대조(정글 신전·늪 오두막·해저 신전·전초기지·고대 도시·시련의 방·보루·트레일 유적), 다중 청크 decoration 구조 제약, `/locate` 일부, 지도 `skipKnownStructures`, 지도 렌더링의 먼 열 청크 로딩은 월드젠 쪽이라 이 대조에서 다루지 않았다. 예정된 2차 전체 점검 파이프라인의 월드젠 조사에 넘긴다.
- 구현 줄의 바닐라 일치 여부 전반(방법과 한계 참고).
- ~~`NaturalSpawner.java:4` 의 구조물 스폰 덮어쓰기~~ — 2026-11-07 M14 에서 확인: 이미 구현돼 있었다(`NaturalSpawner.mobsAt` + `StructureSpawnOverrides`, 커밋 0a40de06, 시험 `StructureSpawnOverrideTest`). 바닐라 `ChunkGenerator.getMobsAt`·`isInNetherFortressBounds` 와 같은 순서. 낡은 TODO 주석만 고쳤다.
- 중복 확인이 필요한 줄: 종합 조사의 "엔티티 NBT 저장 공백"(`Glowing`·`Team`·`AbsorptionAmount`·`active_effects` 는 코드에 보이나 전부 왕복되는지는 `EntityNbtRoundTripTest` 범위를 안 읽음).

## 6. 시작 순서 제안
1. 지금: M16, M19, M22 (서로·기존 묶음과 파일이 안 겹침).
2. M1 병합 뒤 M15, M3 병합 뒤 M14·M18, M8 병합 뒤 M17.
3. M9 뒤 M20, M10 뒤 M21.

## 7. M22 결과 (엔티티 NBT·표시 엔티티, 2026-11-07)
바닐라 1.21.1 클래스 바이트코드(`bsq` Display, `bsn` AreaEffectCloud, `ciw` ArmorStand, `bth` Interaction, `f` MatrixUtil 등)를 `javap -c` 로 읽고 맞췄다. 시험 `EntityNbtDisplayTest`.
- **Display**: `transformation` 을 바닐라 `EXTENDED_CODEC` 대로 읽는다 — 복합 태그(회전은 `[x,y,z,w]` 또는 `{angle, axis}`)와 16개 숫자 행렬(행 우선). 행렬은 바닐라 `MatrixUtil.svdDecompose`(자코비 5회 + 기븐스 QR)를 JOML 순서대로 옮긴 `DisplayTransform` 으로 이동·왼쪽 회전·크기·오른쪽 회전으로 쪼갠다(시험은 쪼갠 값을 다시 합쳐 같은 행렬이 나오는지 본다). 코덱이 실패하면(필드 누락·길이 틀림) 바닐라처럼 transformation 을 건드리지 않는다. `start_interpolation` 읽기, `width`/`height` 한쪽만 있을 때 다른 쪽을 0 으로 만들던 것 고침.
- **TextDisplay**: `text`(JSON) 읽기·쓰기(JSON 이 깨져 예외면 경고만 하고 기존 글자 유지, 파서가 null 을 주면 빈 글자), 저장할 때 꺼진 플래그도 `0b` 로 항상 씀, `alignment`(`center/left/right`), `shadow`·`see_through`·`default_background` 플래그, 플래그는 읽을 때 0 에서 다시 쌓는다.
- **Interaction**: 플레이어 공격(`skipAttackInteraction`: `attack` 기록 + `player_hurt_entity` 트리거, `response` 가 꺼져 있으면 일반 공격을 건너뜀)과 우클릭(`interaction` 기록, 결과 CONSUME)을 연결.
- **AreaEffectCloud**: `Particle` NBT(입자 종류 전부, `ParticleNbt`/`ParticleArgument.decodeNbt`)와 `potion_contents`(potion·custom_color·custom_effects) 읽기·쓰기. 물약은 바닐라처럼 **기본 물약 효과만 지속 1/4**, 사용자 지정 효과는 그대로(이전엔 구름을 만든 쪽이 전부 1/4 로 줄였다 — ThrownPotion 이 이제 내용물을 그대로 넘긴다). 입자가 `entity_effect` 면 물약 색(없으면 불투명 검정)으로 다시 칠함. `isAffectedByPotions`(거치대 제외)·`canBeAffected`(언데드의 재생·독, 위더 등)로 걸 수 없는 효과뿐이면 건너뜀.
- **ArmorStand**: 두 번째 타격·크리에이티브로 부술 때 참나무 널빤지 블록 입자 10개, 거치대 아이템이 커스텀 이름을 이어받음.
- **Enderman**: `is_projectile` 태그 피해(트라이던트·위더 해골·바람 충전 포함)나 직접 원인이 물약이면 64번 텔레포트 시도, 맑은 물 물약이면 피해도 받음(물 물약 피해의 직접 원인을 물약으로 넘기도록 `ThrownPotion.applyWater` 도 고침).
- **MinecartTNT**: 불붙은 화살이 직접 원인이면 화살 속도²로 바로 폭발, 폭발 피해의 원인은 쏜 쪽(`Explosion.withCausing`). 이를 위해 **화살이 광산 수레를 맞히게** 했다(`AbstractArrow.CartTarget`) — 바닐라는 화살이 pickable 엔티티를 아무거나 맞히지만 Furnace 는 Targetable 만 맞혔다.
- **AbstractArrow**: 맞은 생물(플레이어 포함)의 박힌 화살 수 +1(관통 화살 제외), `20 × (30 − 수)` 틱마다 하나씩 빠짐, 플레이어는 `DATA_ARROW_COUNT` 동기화. 줍기 규칙(허용/창조 전용/불가)과 관통 대상 기록은 이미 있었다.
- **낚시 행운**: 낚싯대 바다의 행운 + 플레이어 `luck` 속성.
- **독립 검토 반영(2026-11-07)**: 폭발 `indirectSourceEntity` 는 폭발 엔티티만 본다(피해 원인 causing 은 안 씀) · 투사체 `canHitTarget`/`checkLeftOwner` 가 뿌리 탈것이 같은 대상을 제외(탄 수레 안 맞음) · 불화살이 수레에 불을 붙이고 삼지창도 수레를 직접 원인·주인과 함께 맞힘 · 컴포넌트 목록형 회전 `[x,y,z,w]` 정규화 · JOML 기본(`joml.useMathFma` 꺼짐)처럼 SVD 의 `fma` 를 `a*b+c`(두 번 반올림)로 · axis_angle 은 `AxisAngle4f`→`Quaternionf` 생성자 순서(각도 [0,2π) 정리, 축 비정규화) · 구름은 죽어 가는 몹/플레이어 제외, `potion_contents` 문자열 형식 읽기 · 투척 물약의 즉시 피해는 `indirect_magic`+직접 원인 물약(엔더맨 순간이동) · `Interaction` 의 player_hurt_entity 피해 원인에서 엔티티 제거 · 화살이 다른 region 플레이어에 박힐 때 `runOnOwner` 로 넘김.
- **남은 것**: 아이템 입자 `Item` 의 컴포넌트와 엔티티를 목적지로 하는 `vibration` 입자의 NBT 저장/읽기(읽기도 아직 안 받음 — 복원되지 않으면 기본 입자), 투사체가 수레에 맞았을 때 `setLastHurtMob(null)` 갱신, 선택자·점수가 든 TextDisplay 텍스트(`updateForEntity`, 엔티티가 서버 코어에 닿는 길이 없음), 광산 수레 외 Targetable 이 아닌 엔티티(배·액자·그림·Interaction)를 투사체가 맞히는 것, 침(stinger) 수의 플레이어 동기화(`Bee.java`).

## 8. M14 결과 (몹 후속 잔여, 2026-11-07)
바닐라 1.21.1 서버 jar 를 Vineflower 로 디컴파일해 클래스별로 대조했다(`cko` Spider, `cfq` IronGolem, `cmb` Warden, `ckm` Slime, `cfo` Fox, `cjn` Bogged, `cfx` Pufferfish, `chv` Sniffer, `cgv` Camel, `chl` Llama, `chq` TraderLlama, `cmf`/`cmq` 상인, `clo` PiglinAi, `bwp` Mount, `bvq` DismountOrSkipMounting, `eqa`/`epp` 노드 평가기, `cgq` Axolotl). 시험 `MobLeftoversM14Test`(16개).
- **이미 돼 있던 것(확인만)**: 구조물 스폰 덮어쓰기, 늑대 털기 `ENTITY_ACTION`·닭 알 `ENTITY_PLACE`·마녀 `DRINK`·셜커 `CONTAINER_OPEN/CLOSE`·`TELEPORT` 게임 이벤트, 팬텀 사냥 중 고양이 하악질, 크리퍼 효과 구름, 라마 카펫(몸통 칸). 낡은 TODO 주석(`NaturalSpawner`·`Phantom`)을 고쳤다.
- **거미**: `SpiderEffectsGroupData` — 자연 스폰 한 무리가 처음 거미가 고른 효과를 같이 받는다(어려움 10% × 지역 난이도). 동굴 거미는 무리 데이터를 그대로 넘긴다.
- **밀기 훅** `LivingEntity.doPush`: 철 골렘은 민 적대 몹(크리퍼 제외)을 1/20 확률로 대상으로, 워든은 만지기 쿨다운 20틱·분노 35·소란 위치, 슬라임은 철 골렘에게 밀리면 그 골렘을 때린다(`Slime.push`). 플레이어 후보는 `doPush(Player)` 로 같은 훅을 탄다.
- **여우**: 위협 판정에서 믿는 대상·자는 대상·웅크린 대상 제외, 마을(`isVillage` = 마을 거리 1) 안에서는 그늘 찾기 안 함, 가루눈 속에서는 안 잠.
- **늪 해골**: 가위로 깎으면 `shearing/bogged` 표(버섯 2개, 표의 random_sequence)를 몸 높이에서 떨군다.
- **복어**: 주변 찌르기는 바닐라처럼 `Mob` 만, 플레이어는 플레이어 접촉(`playerTouch`)으로 — 조용하지 않으면 `PUFFER_FISH_STING`(게임 이벤트 9) 패킷, 독 60×단계.
- **아홀로틀**: 죽은 척하는 동안 `canBeSeenAsEnemy` 거짓(바닐라 1.21.1 은 `isPushable` 을 바꾸지 않는다 — 문서의 "밀기" 는 오해).
- **상인 라마**: 상인에게 매였으면 상인 남은 시간 - 1 을 따르고, 0 이면 끈을 풀고(아이템 없이) 사라진다(aiStep). 상인 말고 다른 것에 매였거나 플레이어 한 명만 탔거나 길들였으면 안 줄어든다. 상인을 때린 상대를 노리는 `TraderLlamaDefendWanderingTraderGoal`, 상인에게 매인 동안 타지 못함.
- **방랑 상인**: `AbstractVillager` 8칸 가방(NBT `Inventory`, `/item … villager.0~7` — 주민도 같은 칸 번호로 열었다).
- **피글린**: 아기 피글린이 10~40초마다 보이는 아기 호글린을 10~30초 동안 탈 대상으로 삼는 RIDE 활동(`Mount`·`DismountOrSkipMounting` 새 행동), 탈 대상 기억이 없어지면 내림.
- **낙타**: 웅크려 우클릭 → 안장 칸 창, 둘까지 탑승(앞 z 0.5·뒤 -0.7, 동물 +0.2, 자세 전환 애니메이션 높이), 앉은 자세 몸 높이 -1.43(눈 0.845)와 자세를 바꿀 공간 검사, 질주 데이터가 바뀌면 쿨다운 55(서버에서 dash_ready 소리), 질주 표시는 물·용암·탈것 위에서도 풀림, 탄 채 앞으로 가면 앉아 있다 일어남·앉은 동안 몸을 안 돌림. 걸음 소리는 M15 에서 들어갔다.
- **라마**: 6칸 이상 떨어졌을 때만 낙하 피해(말 착지 소리 없음).
- **스니퍼**: 걷는 길찾기(`WalkNodeEvaluator`)가 `onPathfindingStart/Done` 을 부른다(불붙었거나 물속이면 물 비용 0 → 끝나면 -1). 예전 주석의 "GolemTest 가 깨짐" 은 지금 재현되지 않았다. 파는 동안 몸 높이 -0.4(눈 0.81), 씨앗 전리품은 `gameplay/sniffer_digging` 을 선물 매개변수(origin·this_entity)로, 걸음 소리. 파는 입자·찾는 소리는 바닐라도 클라이언트 전용이다.
- **남은 것**: 낙타 머리 회전 고정(clampHeadRotationToBody, 표시용), 플레이어 승객의 말·라마 낙하 피해(`AbstractHorse` TODO), `playBlockFallSound`, 피글린 머리 드롭·방어구 교체 방지 인챈트(`Piglin.java:2`), 호글린 인챈트 후처리, 셜커가 껍데기를 열 때 플레이어 밀기, 밀기 순서(바닐라는 엔티티 구역 순서로 플레이어와 섞여 있고 Furnace 는 엔티티 다음 플레이어 — 워든 만지기 쿨다운이 둘 다 닿을 때 누구에게 먼저 걸리는지만 다를 수 있음). 검토(PR #112)에서 나온 낮은 차이: 낙타 `canChangePose`·앉은 크기에 SCALE 속성 미반영, 크기 변경 뒤 `fudgePositionAfterSizeChange` 없음(엔진 공통), 낙타 질주 처리가 tick 끝이 아니라 aiStep 안, 복어 독에 출처 엔티티 없음.

