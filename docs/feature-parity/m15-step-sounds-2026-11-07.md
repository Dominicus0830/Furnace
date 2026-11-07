# M15: 걸음·헤엄 소리 전반 (2026-11-07)

바닐라 1.21.1 서버 클래스 바이트코드(`javap -c -p`)와 맞췄다. 난독화 이름: `bsr`=Entity, `btn`=LivingEntity, `btw`=Mob, `cmx`=Player, `dnm`=SoundType, `avp`=SoundEvents,
`dga`=Blocks, `dtb$d`=BlockBehaviour.Properties, `dtr`=BlockSetType, `dup`=WoodType, `awe`=BlockTags, `chi`=AbstractHorse, `cgv`=Camel, `cgf`=Turtle, `ckq`=Strider.

## 채운 것
| 항목 | 바닐라 | Furnace |
|---|---|---|
| 블록 SoundType 표 | `BlockState.getSoundType()` = 블록의 `Properties.sound`(기본 STONE). 문·다락문·버튼·압력판·울타리 문·표지판 생성자는 `properties.sound(type.soundType())` 로 BlockSetType/WoodType 소리를 덮는다(걸이 표지판은 `hangingSignSoundType`) | `blocks/soundtypes_1.21.1.txt`(블록 1060개 × 종류 110개: 크기·높이·break/step/place/hit/fall) + `block/BlockSounds`. 생성기는 `tools/vanilla-data/extract_block_sound_types.py` |
| 기본 걸음 소리 | `Entity.playStepSound`: `playSound(type.getStepSound(), type.getVolume() × 0.15F, type.getPitch())`. **1.21.1 에는 액체·`inside_step_sound_blocks` 검사가 없다** | `Entity.playStepSound` |
| 자수정 울림 | `walkingStepSound` = 걸음 소리 뒤 `crystal_sound_blocks` 이고 `tickCount ≥ lastCrystalSoundPlayTick + 20` 이면 `playAmethystStepSound`: 세기 `×= 0.997^(지난 틱)`, `= min(1, 세기 + 0.07)`, 크기 `0.1 + 세기×1.2`, 높이 `0.5 + 세기×nextFloat×1.2`(난수 한 번) | `Entity.walkingStepSound/playAmethystStepSound`, 플레이어는 `PlayerMovementEmission` |
| 조합 걸음(플레이어) | `Player.playStepSound`: 물에 닿았으면 `waterSwimSound` + `playMuffledStepSound`(크기 ×0.05, 높이 ×0.8). 아니면 위 칸이 `inside_step_sound_blocks`/`combination_step_sound_blocks` 면 위 칸 기준, 조합 블록이면 위 칸 걸음(×0.15) + 발밑 얼버무림 | `PlayerMovementEmission.playStepSound` |
| 플레이어 걸음·헤엄 소리 | Entity.move 의 이동 방출이 `emitsSounds` 로 낸다. `Player.playSound` 는 자기 자신을 뺀다(클라이언트가 직접 낸다). `!isSwimming()` 조건 | 본인을 뺀 플레이어에게 `players` 분류로. `effects` 에 `!swimming` 조건을 더해 STEP 게임 이벤트도 수영 중에는 안 나간다 |
| 헤엄 소리 | `getSwimSound`: 기본 generic.swim, Monster·Hoglin hostile.swim(+splash), AbstractFish fish.swim, Dolphin, Turtle, Axolotl(+splash), Drowned, Player player.swim, SkeletonHorse(땅을 딛고 있으면 step_water/gallop_water 카운터, 떴으면 swim) | 각 클래스 `getSwimSound`·`swimSplashSound` |
| 몹별 걸음 소리 | 아래 표 | 각 클래스 `playStepSound` |
| 플레이어 `hit_ground` | `Entity.checkFallDamage`: `fallOn` 뒤 `HIT_GROUND`(맥락 = 지지 블록 상태, 없으면 `getOnPosLegacy` 칸) | `Region.processMoves` 착지 분기 |

### 몹별 걸음 소리 (크기·높이)
- 0.15, 1.0: Chicken, Cow(+Mooshroom), Panda, Parrot, Pig, PolarBear, Sheep, Wolf, Armadillo, Frog, Goat, Llama(+TraderLlama), Sniffer, Endermite, Ravager, Silverfish, Spider(+CaveSpider), Zoglin, Hoglin, Piglin, PiglinBrute, Zombie 계열(`getStepSound`: zombie·husk·drowned·zombie_villager, ZombifiedPiglin 은 zombie), 해골 계열·Warden(M1).
- 크기 1.0: IronGolem(`iron_golem.step`), Strider(용암 속이면 `step_lava`), Camel(`camel_sand_step_sound_blocks` 위면 `step_sand`).
- Turtle: 아기 `shamble_baby`/어른 `shamble`(0.15), `nextStep` = `moveDist + 0.15`(Warden 0.55·Strider 0.6 은 이미 있었다).
- AbstractHorse: 액체면 소리 없음, 위 칸이 눈이면 눈 SoundType. 탄 채면 `gallopSoundCounter` 증가(처음 5번 `step_wood`, 이후 3번에 한 번 `gallop`), 아니면 나무 계열(WOOD·NETHER_WOOD·STEM·CHERRY_WOOD·BAMBOO_WOOD)이면 `step_wood`, 아니면 `step`. 짐 싣는 말(Donkey·Mule·Llama)은 gallop 에 10분의 1 확률로 `horse.breathe`(×0.6).
- 소리 없음(빈 `playStepSound`): Bee, Allay, AbstractFish.

## 남은 차이 (가정)
- **SoundType 표는 바이트코드 정적 분석으로 얻었다**(바닐라 서버를 띄울 수 없는 환경이라 `Bootstrap` 로 실제 레지스트리를 읽지 못했다). 블록마다 `Properties.sound(...)`·`ofFullCopy`·도우미 메서드·BlockSetType/WoodType 경유를 따라갔고, 알려진 값(풀·나무·금속·구리·벚나무·걸이 표지판·눈·꿀·거미줄 등 표본)을 손으로 확인했다. 상태에 따라 소리 종류가 바뀌는 블록은 1.21.1 에 없다(장식 항아리만 `BlockState.getSoundType` 을 덮어 금 간 상태의 break 소리가 shatter 로 다르다 — 표의 `[state-overrides]` 구역).
- 플레이어 헤엄 소리 크기는 `Player.serverMovement()`(마지막 이동 패킷의 변위)로 잰다. 바닐라 ServerPlayer 의 `deltaMovement` 는 클라이언트가 움직임을 정하므로 대개 0 이라 크기가 거의 0 일 것이다(가정 — 첨벙 소리 `playerWaterSplash` 가 쓰던 값과 같은 근사).
- 말이 탄 채 달릴 때 `gallopSoundCounter` 를 0 으로 되돌리는 `tickRidden` 갈래는 서버에서 돌지 않는다(바닐라도 `isLocalInstanceAuthoritative` 가 서버에서 거짓이라고 읽었다). 서버 카운터는 계속 올라가 3번에 한 번 gallop 이 난다.
- 플레이어 `hit_ground` 의 맥락 블록은 지지 블록이 없을 때 발 밑 칸(`getOnPos` 1e-5 아래)으로 근사한다.
- `Entity.isOnRails`(수레 위 STEP 판정)와 비생물(보트·수레) 이동 방출은 여전히 없다. 낙하 소리(`LivingEntity.playBlockFallSound`·`small_fall`/`big_fall`)는 이 묶음 밖이다(표에는 fall 소리가 들어 있다).

## 검증
- 단위: `StepSoundTest` 21건(표 표본·기본 걸음·자수정 쿨다운·몹 24종·좀비 계열·무음·거북·스트라이더·낙타·말 눈/액체/질주·당나귀 숨소리·헤엄 소리·플레이어 걸음/웅크림/수영/조합 블록/물/자수정).
- 봇(`tools/bot-test/m15/m15.js`, 오프라인 서버 + mineflayer 둘): 걷는 플레이어의 걸음 소리가 다른 봇에게 `player` 분류 크기 0.15 로 6번, 걷는 본인에게는 0번, 웅크리면 0번, 이끼 카펫 위에서는 0.15 와 0.05(높이 0.8)가 6쌍, 소의 걸음 소리 neutral 0.15, 자수정 위에서 크기 0.184·높이 0.51 울림. 6항목 통과.

## 독립 검토 반영 (2026-11-07)
- 박쥐·오징어·발광 오징어·수호자·장로 수호자·브리즈·좀벌레·엔더마이트는 바닐라 `getMovementEmission` 이 EVENTS 라 이동으로 걸음·헤엄 소리를 내지 않는다 — 같은 값으로 덮었다.
- 거북 `playSwimSound` 크기 ×1.5, 해골말 `playSwimSound`(땅 위 0.3 고정, 아니면 min(0.1, ×25))를 `Entity.playSwimSound(volume)` 훅으로 옮겼다.
- 장식 항아리 소리 종류 두 개(DECORATED_POT·DECORATED_POT_CRACKED)와 상태 덮어쓰기. 생성기도 같은 규칙을 갖는다.
- 말의 나무 소리 판정에서 HARD_CROP(melon_stem·pumpkin_stem)을 뺐다(놓기 소리까지 같은 종류만 인정).
- 남은 가정: 플레이어 헤엄 소리 크기는 서버가 아는 움직임(마지막 이동 패킷 변위)으로 잰다. 바닐라 ServerPlayer 의 deltaMovement 는 위치 패킷으로 갱신되지 않아 거의 0 이므로 바닐라는 거의 무음일 수 있다 — 확인되기 전까지 가정이다.
- 탈것이 플레이어일 때 `Entity.waterSwimSound` 가 조종자의 움직임·계수 0.4 를 쓰지 않는 차이(이번 묶음 이전부터 있던 코드)와 HIT_GROUND 지지 블록 대체값 차이는 남은 한계다.
