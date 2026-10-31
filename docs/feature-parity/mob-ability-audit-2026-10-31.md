# 몹 세부 능력 대조 감사 (2026-10-31)

읽기 전용 감사. 소스·gradle 은 건드리지 않았다. 대상은 `furnace-server/.../server/entity/` 의 구체 몹 약 85종(Mob 하위)과 바닐라 1.21.1 같은 몹이다.
바닐라 근거는 난독화된 디컴파일(`scratchpad/decomp`, 이하 클래스명은 난독화 이름)이라 **줄 번호는 그 파일 기준**이다. 난독화 클래스 → 몹 이름은 `bsx.java`(EntityType 등록)에서 뽑았다
(예: `cjp`=creeper, `cgh`=wolf, `cjx`=ghast, `cko`=spider, `ckm`=slime, `cfq`=iron_golem, `cmb`=warden, `ckj`=shulker, `cgk`=allay, `ckz`=breeze, `bul`=TamableAnimal, `cne`=AbstractHurtingProjectile, `bsr`=Entity, `btn`=LivingEntity).
확인하지 못한 것은 "가정"이라고 적었다.

## 방법과 한계

1. 자동 대조 4종(몹 상속 사슬 전체를 바닐라/Furnace 양쪽에서 합쳐 비교)
   - 바닐라 클래스의 문자열 리터럴(저장/로드 NBT 키, 로그 문구 포함) 중 Furnace 쪽에 없는 것
   - `goalSelector`/`targetSelector` 등록의 (종류, 우선순위) 쌍 비교
   - 역할별 오버라이드 존재 여부(hurt, die, dropCustomDeathLoot, mobInteract, thunderHit, finalizeSpawn, setTarget, doHurtTarget, doPush, canBeAffected, aiStep, tick, customServerAiStep, removeWhenFarAway …)
   - `broadcastEntityEvent((byte)N)` 번호, `GameEvent` 방출(`this.a(dxz.X)`) 횟수
2. 위 결과에서 차이가 난 몹과 Furnace 파일 머리의 `TODO` 주석을 바닐라 소스와 직접 읽어 확정했다.
3. 한계: 우선순위·번호가 같아도 목표 클래스가 다른 경우, 메서드 본문의 세부 분기(난수 순서 포함)는 이 감사가 잡지 못한다. 주민·습격·피글린 AI·엔더 드래곤은 규모가 커서 본문 단위로는 읽지 않았다(드래곤은 `dragon-parity-recheck-2026-10-28.md` 참고).
4. 사운드 문자열은 바닐라 쪽이 난독화 상수라 일괄 대조하지 못했다(가정: 사운드 이름은 앞선 작업에서 맞춰 둠). 클라이언트 전용 `handleEntityEvent` 본문(입자·애니메이션)은 서버 관측에 영향이 없어 제외했다.

## 일치 확인 (이번에 다시 맞춰 본 것)

- NBT 키: 바닐라 문자열 리터럴 대비 빠진 것은 Allay/Warden `listener`, Wolf `variant`, 그리고 종류 이름 표(라마·앵무·토끼 색 이름은 숫자 id 로 대신 저장)뿐이었다.
- 엔티티 이벤트 번호: 빠진 것은 Ravager 39(기절), Wolf 56(털기 취소), SnowGolem 16(데이터 기본값 오탐)뿐.
- 목표 우선순위 쌍: 몹 약 85종 중 어긋난 것은 아래 표의 Dolphin(2번), Shulker(대상 3번), Vindicator(대상 4번), Parrot(3번), WitherSkeleton/Silverfish/Endermite(1번), Cat(3·4번)이다. 이 중 Silverfish/Endermite/WitherSkeleton 의 차이는 `ClimbOnTopOfPowderSnowGoal`(바닐라 1.21 에 추가) 쪽으로 보이나, 가루눈 위를 걷는 몹은 가정으로 낮음 처리했다.
- 속성 기본값: 이전 감사(2026-10-28)에서 걸린 Vindicator/Rabbit 은 이번에 보니 바닐라와 같다(`cks.java:43`, `cfy.java:205`).
- 스폰 규칙(`SpawnPlacements`): Husk(하늘 보임)·Stray·Drowned(강 1/15, 그 외 1/40 + 해수면 −5)는 이미 구현됨.
- 번개 변환: Pig→좀비 피글린, Villager→마녀, Mooshroom 색 변경, Creeper 충전, Turtle 은 구현됨.
- 이 외 이 문서에 없는 몹(예: Panda, Fox 본체, Bee, Turtle, Axolotl, Frog, Goat, Squid, 물고기, Phantom, Blaze, Witch, Enderman, Husk, Drowned, 좀비 계열 변환/증원/문 부수기, Spider 독 면역, Cat 검은 변종, Wither 소환 틀)은 위 자동 대조와 훑어 읽기에서 차이를 못 찾았다.

## 다른 워커가 처리 중 (조사하지 않음)

| 항목 | 상태 |
|---|---|
| U2 파괴수 블록 파괴 | 진행 중 |
| U1/U3 주민 문, 주민 폭죽 | 진행 중 |
| U4 말 계열(Horse, Donkey, Mule, SkeletonHorse, ZombieHorse). Llama/TraderLlama/Camel 의 탑승은 U4 범위인지 확인 못 함 | 진행 중(말) / 가정(나머지) |

## 빠진/다른 능력

관측 영향은 "플레이어/월드에서 바닐라와 달리 눈에 띄는 정도"다.

| 몹 | 빠진/다른 능력 | 바닐라 근거 클래스·줄 | 관측 영향 |
|---|---|---|---|
| Wolf | **변종**: 스폰 때 바이옴으로 변종 선택, 데이터 동기화, NBT `variant` 저장. 지금은 난수 1회(`random.nextBoolean()`)만 소비하고 변종이 없다 | `cgh.java:59-76, 118-132`(finalizeSpawn·`getVariant`), `Wolf.java:188`(Furnace) | 높음 |
| Wolf | **늑대 갑옷**: 몸통 칸 장착(우클릭), 피해 시 내구도 소모와 깨지는 소리·입자, 아르마딜로 비늘 수리, 가위로 벗기기, 갑옷 피해 흡수 | `cgh.java:277-316`(canUseSlot·actuallyHurt), `cgh.java:319-388`(mobInteract). Furnace `Wolf.java` 에 `ANIMAL_ARMOR` 처리 없음(`Mob.java:118` 칸만 있음) | 높음 |
| 모든 Mob | **가죽끈 전무**: 끈 아이템 사용, 울타리 매듭, 끊김, 끈에 매인 몹의 이동 제한. NBT `leash` 만 보존. 이 때문에 Bee 의 `isLeashed()` 는 항상 거짓, 짐 라마/방랑 상인 끈, Llama 캐러밴, Allay/Camel/Armadillo 끈 판정이 모두 빠짐 | `bsr`/`btp`(Mob) 의 leash 처리. Furnace 근거: `Mob.java:791`, `Bee.java:873`, `Llama.java:242`, `WanderingTraderSpawner.java:93` | 높음 |
| Ghast + 불덩이(Fireball/SmallFireball) | **불덩이 되치기**: 플레이어가 불덩이를 때리면 방향이 바뀌고(소유자 변경), 되튄 큰 불덩이에 맞은 가스트는 피해 1000(= 즉사). 지금은 불덩이를 맞을 수 없다(hurt 거짓). 블레이즈 작은 불덩이도 같은 규칙 | 가스트 `cjx.java:38-55`(`g(brk)`·hurt), 투사체 `cne.java:94`(AbstractHurtingProjectile.hurt), `cnj.java:60`. Furnace `Fireball.java:1`, `Ghast.java:2` | 높음 |
| Allay | **음표 블록·주크박스 수신**: 진동 수신기가 없어 춤과 음표 블록으로 끌려가는 동작이 일어나지 않는다. NBT `listener` 도 저장/복원 안 함. (Allay 선별기에 쓰이는 핵심 능력) | `cgk.java:50, 334, 379-391, 497-540`(`dxz.E/F/H`=jukebox_play/stop/note_block_play, listener NBT). Furnace `Allay.java:2, 115` | 높음 |
| 몹 전반(게임 이벤트) | **몹 쪽 게임 이벤트 미방출**: `ENTITY_DAMAGE`(피해 입을 때), `EXPLODE`, `TELEPORT`(엔더맨·셜커), `PRIME_FUSE`(크리퍼), `SHEAR`(양·무시룸·눈 골렘), `EAT`(판다·말), `DRINK`(마녀), `ENTITY_PLACE`(닭 알), `ENTITY_ACTION`(늑대 털기), `PROJECTILE_SHOOT/LAND`(투사체)가 방출되지 않는다. Furnace 는 `ENTITY_DIE`·`STEP`·`SWIM`·`FLAP`·`HIT_GROUND` 와 일부 블록 이벤트만 낸다. 워든·스컬크 감지기가 이 소음들에 반응하지 않는다 | 바닐라 호출: `btn.java:1542`(ENTITY_DAMAGE), `cjp.java:100`(PRIME_FUSE), `cjt.java:226`(TELEPORT)·`:392`(BLOCK_PLACE)·`:524`(BLOCK_DESTROY), `ckj.java:321`, `cga.java:155`·`cfr.java:71`·`cgc.java:94`(SHEAR), `ckt.java:81`(DRINK), `cfj.java:61`, `cgh.java:193`, `cft.java:343`. Furnace: `GameEvent.ENTITY_DAMAGE` 등 사용처 0(`AbstractMinecart.java:169` TODO) | 중 |
| Creeper, Zombie 계열, Skeleton, WitherSkeleton, Piglin | **충전 크리퍼 머리 떨구기**: 충전된 크리퍼 폭발로 죽으면 머리 아이템(크리퍼/좀비/해골/위더 해골/피글린 머리)을 한 번 떨구고 `canDropMobsSkull` 횟수를 줄인다. Furnace 에 해당 코드 없음 | `cjp.java:135-142`(`gl()`/`gm()`), `ckw.java:446-456`(Zombie, 드라운드·허스크·주민좀비·좀비피글린 공통), `ckl.java:107`, `cku.java:36-42`, `cln.java:101`. Furnace `Piglin.java:2`(TODO), `Creeper.java:2`(TODO) | 중 |
| Wolf, Cat, Parrot (TamableAnimal) | **길들인 펫 사망 메시지**: 주인이 접속 중이고 `showDeathMessages` 가 켜져 있으면 전투 기록의 사망 메시지를 주인에게 보낸다. Furnace `TamableAnimal` 에 `die` 오버라이드 없음 | `bul.java:195-201` | 중 |
| Pig, Strider (Camel) | **타고 조종**: 안장 단 돼지/스트라이더에 타기, 당근 막대/뒤틀린 균류 막대 가속(`ItemSteerable`), 스트라이더는 안장 장착 상호작용 자체가 없다(NBT `Saddle` 만). 스트라이더 스폰 때 좀비 피글린 기수. 말 U4 의 탑승 프레임워크와 겹칠 수 있어 "가정: U4 이후 재확인" | `cfv.java:85-105, 188-201`(Pig mobInteract·ridden), `ckq.java:334-357, 184-197, 364-399`(Strider mobInteract·ridden·finalizeSpawn). Furnace `Pig.java:1`, `Strider.java:2`, `Camel.java:2` | 중 |
| Spider, CaveSpider | **거미줄에 걸리지 않음**: `makeStuckInBlock` 에서 거미줄이면 무시. Furnace `WebBlock` 은 대상 종류를 안 가려서 거미도 느려진다(독 면역은 있음) | `cko.java:74-79` | 중 |
| Breeze | **투사체 튕김**: 날아오는 투사체를 튕겨 낸다(`deflects_projectiles`). 투사체 쪽 규칙이 없어 화살이 그냥 맞거나 무적으로 박힌다 | `ckz.java:228-232`(isInvulnerableTo), 튕김 처리는 투사체 클래스(가정: 1.21 `Projectile.hitTargetOrDeflectSelf`). Furnace `Breeze.java:3` TODO | 중 |
| Parrot | **어깨에 앉기**: 주인 어깨에 앉는 `LandOnOwnersShoulderGoal`, 플레이어 어깨 엔티티 저장, 맞으면 떨어짐, 쿠키 독 | 목표 `cfu.java:97`(`car`, `car.java:10-35`), 어깨 판정 `cgb.java:9-27`. Furnace `Parrot.java:2` | 중 |
| Hoglin | **평화로움 난이도에서 사라짐**: `shouldDespawnInPeaceful` 이 참. Furnace Hoglin 은 `Mob` 기본값(거짓)이라 평화로움에서도 남는다 | `clh.java:189-191`. Furnace `Mob.java:395-396`, `Monster.java:17` | 중 |
| IronGolem, Warden, Slime, Parrot | **`doPush` 훅 부재**: 밀릴 때 몹별 반응. 철 골렘(적대 몹을 1/20 확률로 표적), 워든(밀리면 분노 증가, 20틱 쿨다운), 슬라임(철 골렘에게 닿으면 피해), 앵무(플레이어는 밀지 않음). Furnace `LivingEntity.pushEntities` 는 상대 `push` 만 부르고 몹별 훅이 없다 | `cfq.java:50-56`, `cmb.java:469-477`, `ckm.java:186-191`, `cfu.java:304-308`. Furnace `IronGolem.java:2`, `Warden.java:3`, `Slime.java:2`, `LivingEntity.java:945-957` | 낮음~중 |
| Ravager | 방패에 막히면 기절(이벤트 39), 철 골렘에 막힘, 기수 일러저 태우기, 스폰 공간 검사 | `cki.java`(이벤트 39), Furnace `Ravager.java:2`(TODO) | 낮음 |
| Creeper | (1) 염소를 표적으로 삼지 않음(`setTarget` 필터), (2) `doHurtTarget` 이 참만 돌려주고 피해 없음(Furnace 는 근접 목표가 실제 피해를 줄 수 있음), (3) 활성 효과가 있으면 폭발 뒤 효과 구름(`go()`), (4) 폭발 뒤 제거 방식(`c(bsr.c.a)`+`aq()`, 가정: KILLED 제거)이 Furnace 의 `dead=true; discard()` 와 다를 수 있음 | `cjp.java:118-123, 145-147, 204-221, 199-200`. Furnace `Creeper.java`(해당 오버라이드 없음) | 낮음 |
| Slime / MagmaCube | 슬라임: 철 골렘에게도 피해(위 doPush 행). 마그마 큐브: `isOnFire` 거짓·`getLightLevelDependentMagicValue`·`getSoundVolume` 오버라이드 없음 | `ckm.java:186-191`, `ckc.java`(bR/bu/fa) | 낮음 |
| Vindicator | 이름이 "Johnny" 인 개체는 모든 `LivingEntity`(플레이어 제외 몹 포함)를 표적으로 삼는 `Johnny` 목표, NBT `Johnny` 저장/복원, 이름표 감지. Furnace 는 습격 무기 마법도 TODO | `cks.java:6, 26, 50, 66, 97`. Furnace `Vindicator.java:2` | 낮음 |
| Shulker | 대상 선택 3번 `ShulkerDefenseAttackGoal`(팀에 속한 셜커가 `Enemy` 인 생물을 공격), 열릴 때 플레이어 밀기. Furnace 는 둘 다 TODO(팀 규칙 있으면 구현 가능한지 가정) | `ckj.java:45, 551-556`. Furnace `Shulker.java:58` | 낮음 |
| Rabbit | 킬러 토끼는 이름이 없으면 "The Killer Bunny" 이름표(`entity.minecraft.rabbit.killer_bunny`)를 붙인다. 사망 메시지·이름표 표시에 영향 | `cfy.java:282-300`. Furnace `Rabbit.java:2`(TODO), `:186-196` | 낮음 |
| Spider | 스폰 때 효과(어려움 + 지역 난이도) 부여를 **그룹의 첫 거미만** 받는다(`SpiderEffectsGroupData`). Furnace 는 매번 처음 거미로 보아, 그룹 4마리가 각자 굴린다 | `cko.java:102-131`. Furnace `Spider.java:92` | 낮음 |
| Fox | 입에 문 음식의 효과(수상한 스튜 등)를 먹을 때 적용하지 않고 확률 난수만 소비, 자는/웅크린 대상 필터, 마을 제외, 가루눈 판정 | `cfo.java:380-399, 725-775`. Furnace `Fox.java:318, 470, 725, 775` | 낮음 |
| Bogged | 가위로 깎을 때 `shearing/bogged` 전리품(버섯)을 떨구지 않는다 | `cjn.java`(Shearable). Furnace `Bogged.java:1, 50` | 낮음 |
| Dolphin | 2번 `DolphinSwimWithPlayerGoal`(수영 중 플레이어와 헤엄) 없음. 이유로 든 "플레이어 수영 상태 없음"은 `Entity.isSwimming()`(`Entity.java:598`)이 생겨 낡았을 가능성이 있다(가정). 보물 구조물 탐색도 항상 못 찾음 | `cfm.java:92`. Furnace `Dolphin.java:99, 234` | 낮음 |
| Pufferfish | 플레이어 접촉은 `aiStep` 에서 주변 탐색으로 근사했다. 바닐라 `playerTouch` 는 Player 접촉이 확인될 때 `PUFFER_FISH_STING` 게임 상태 패킷을 보낸다(Furnace 는 직접 소리만). 동작은 거의 같음 | `cfx.java:115-125`. Furnace `Pufferfish.java:2, 103-122` | 낮음 |
| Cow, Mooshroom, Wolf | `getSoundVolume` 오버라이드(0.4F 로 보이는 값) 없음. 값과 의미는 가정(디컴파일에서 `float fa()` 존재만 확인) | 역할 스캔 결과(`cfl`/`cfr`/`cgh` 의 `float fa()`) | 낮음 |
| Warden | 몸 밀기는 위 doPush 행. `Warden.hurt`/`isInvulnerableTo` 외에 `cmb.java:437-449`(hurt, 맞으면 분노) 는 있음. 나머지는 차이 못 찾음 | — | 낮음 |
| Sniffer | 걷는 길찾기가 `onPathfindingStart/Done` 을 안 불러 물 비용 전환이 안 먹음, 파기 연출·게임 이벤트, 발소리 | Furnace `Sniffer.java:2-3`(TODO) | 낮음 |
| Camel | 발소리(모래 위 step_sand), 앉은 자세 몸 크기, 안장 칸, 2인 탑승·질주 | Furnace `Camel.java:2, 138, 176` | 중(탑승은 U4 와 겹침, 가정) |
| Llama / TraderLlama | 끈·캐러밴·카펫 장식·탑승, 상인 보호 `TraderLlamaDefendWanderingTraderGoal`(대상 1번) 없음, 상인에게 매이면 사라짐 시간을 따르는 규칙 | 목표 쌍 비교(TraderLlama 대상 1번 1개 차이), Furnace `TraderLlama.java:2`, `Llama.java:2` | 낮음~중 |
| Wandering Trader / Villager | AbstractVillager 의 8칸 인벤토리, 거래 발전 과제, 평판 기반 특가 | Furnace `WanderingTrader.java:3`, `Villager.java:614` — 주민 본체는 U1/U3 영역이라 목록만 | 낮음 |
| Armadillo / Axolotl / Turtle / Tadpole | 끈·탑승·발소리·밀기 관련 TODO(Armadillo `:2, 158, 243`, Axolotl `:2`, Turtle `:2`) | 위 가죽끈·doPush 행에 흡수 | 낮음 |
| Giant, Illusioner, Evoker, Pillager | 습격 보너스 마법·승리 축하·깃발 줍기 등 습격 연동 TODO. `Raid.java` 가 이제 있으므로 해당 TODO 가 낡았을 가능성(가정). 이번에는 본문까지 읽지 않았다 | Furnace `Pillager.java:2`, `Evoker.java:2`, `Vindicator.java:81` | 낮음 |

## 헤더 TODO 가 낡은 곳 (이미 구현됨, 주석만 고치면 됨)

- `Drowned.java:2`: "checkDrownedSpawnRules 는 틀이 생기면" → `SpawnPlacements.java:215-219` 에 구현됨.
- `Zombie.java:239`: "좀비 → 드라운드(TODO)" → 같은 파일 `createConversionTarget()` 이 `Drowned` 를 만든다.
- `WitherBoss.java:3`: "소환 틀 WitherSkullBlock.checkSpawn" → `block/types/WitherSkullBlock.java` 가 있다.
- `Cat.java:2`: "검은 고양이 변종" → `Cat.java:159-169` 에 swamp_hut 처리가 있다.
- `Creeper.java:2`: "번개를 맞아 충전" → `Creeper.java:94` 에 `thunderHit`.
- `Guardian.java:2`: "아홀로틀 표적" → `Guardian.java:143` 에 Axolotl 포함.
- `Pufferfish.java:2`: 위 표(근사 구현).
- `Dolphin.java:2`(플레이어와 헤엄): 위 표(가정).

## 확인 못 한 것 (가정)

- 게임 이벤트 약자(`dxz.a`, `dxz.c`, `dxz.t`, `dxz.u`, `dxz.m`, `dxz.l`)와 몹 대응은 레지스트리 순서(`dxz.java:5-64`)로 읽었다. 몹별 정확한 사용처(예: Panda `m`=EAT, Witch `l`=DRINK, Chicken `t`=ENTITY_PLACE)는 해당 줄을 직접 읽어 확인했으나, Turtle `a/i/u`, Rabbit `a/c`, Fox `a/c`, Armadillo `r/t/u`, Warden `a` 는 열어 보지 못했다.
- Enderman 이 블록을 집고 놓을 때 `BLOCK_DESTROY/BLOCK_PLACE` 게임 이벤트는 Furnace 쪽 `Level.setBlock` 경로가 대신 낼 수도 있어(미확인) 위 표에서 TELEPORT 만 확정으로 적었다.
- Silverfish/Endermite/WitherSkeleton 목표 개수 차이 1건씩은 바닐라 1.21 `ClimbOnTopOfPowderSnowGoal`(`cab`) 쪽으로 보인다(`cju.java:13`, `ckk.java:16`). Furnace 는 Fox 에만 이 목표를 넣었다(`Fox.java:76`). 가루눈 위에 선 몹이 빠지는 차이로 이어질 수 있으나 확인하지 못했다. 영향은 낮음.
- 주민 두뇌, 피글린 AI, 습격 본문, 엔더 드래곤 위상, 위더 보스 본문은 이번에 줄 단위로 읽지 않았다.
