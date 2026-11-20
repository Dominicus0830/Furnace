# T-221 구조물 몹 finalizeSpawn (2026-11-20)

- **구조물 몹의 world 인자는 배치 길마다 다르다.** 월드젠은 `WorldGenRegion`(레벨 난수 = 그 장식의 `worldgen_region_random` 을 청크 원점에 판 것, 지역 난이도의 머문 시간 0,
  `getEntities` 는 늘 빈 목록, `addFreshEntity` 는 그 순간 NBT 로 저장 — 탄 엔티티는 건너뛴다). `/place` 는 서버 레벨. Paper 의 FAWE populator 는 그 populator.
  설계 `compat-nms-t211-world-surface-2026-11-17.md` 11절의 표.
- **피글린은 `STRUCTURE` 이면 아기·무기를 뽑지 않는다**(보루). 그 뒤 기억 초기화·장비·장비 마법 부여는 그대로 — 서버 이식에 분기와 마법 부여가 빠져 있었다.
- **바다 유적 익사체는 `setPersistenceRequired` 를 먼저 한다**(바닐라 `OceanRuinPieces`). 해저 유적 엘더 가디언은 `heal(getMaxHealth())` 뒤 `moveTo`.
- **이글루 템플릿은 `finalizeEntities` 가 꺼져 있다** → 주민에 `AssignProfessionWhenSpawned` 를 붙이면 안 된다(마을 조각은 켜져 있어 finalizeSpawn 이 붙인다).
- **늪 오두막만 `addFreshEntityWithPassengers(e, CHUNK_GEN)`**, 나머지 조각은 기본(`DEFAULT`).
- **고양이 변종·양 털색은 world 인자의 `getRandom()`** 에서 뽑는다(레벨 자체 난수가 아니다 — 월드젠·플러그인 길에서 갈린다). 고양이 변종 표는 보름(달 밝기 > 0.9)이면 11개, 아니면 10개.
- **마녀(Raider·PatrollingMonster)는 `STRUCTURE` 에서 대장 뽑기를 건너뛴다** — `Mob.finalizeSpawn` 의 `triangle(0, 0.11485)`(nextDouble 둘)·왼손잡이(nextFloat)만.
- **Paper `DummyGeneratorAccess` 는 `getCurrentDifficultyAt`·`getLevel`·`getRandom`·`getEntities`·`getBiomeManager` 에서 `UnsupportedOperationException("Not supported yet.")`.**
  FAWE 2.16.2 `FaweBlockStateListPopulator` 는 `getLevel`·`getCurrentDifficultyAt`·`getRandom`·`getBiomeManager`·`getSeed` 를 서버 레벨로 넘기고 `getEntities`·`addFreshEntity` 는 고쳐 쓰지 않는다
  → 몹은 만들어 마무리한 뒤 버려지고, 아기 좀비가 닭 타기 뽑기에 걸리면 Paper 도 던진다. 같은 예외를 낸다.
