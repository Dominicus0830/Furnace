# 월드 밖 NMS 엔티티의 Bukkit 엔티티 (2026-11-27)

설계 `furnace/designs/detached-bukkit-entity-design-2026-11-27.md`, 검토 필수 6개(`furnace/reviews/detached-bukkit-entity-design-review-2026-11-27.md`)를 구현했다.

## 구조
- `Entity#getBukkitEntity()`(shim): 월드 밖 엔티티도 Paper 처럼 그 종류의 CraftEntity 를 준다. `Compat.detachedEntity(handle)` 가 만들고 UUID 표에는 올리지 않는다(월드 밖 엔티티는 `Bukkit.getEntity` 로 찾아지지 않는다).
- `DetachedEntityHandle`(compat/nms): 월드 밖 엔티티의 `api.entity.Entity` 구현. 종류는 서버 세션이 정한다(생물이면 Living, AI 몹이면 Mob). `stateNow()` 는 기록 프록시.
- 기록 프록시: 읽기는 서버 빈 레벨 세션(`DetachedEntityData#read`)에서, 쓰기는 `EntityOp.StateCall(path)` 로 세션에 재생하고 조작 목록에 붙인다(`DetachedEntityData#apply` + `lastResult`). 넣을 때 서버가 같은 호출을 새 엔티티에 재생한다. 속성 객체를 거친 쓰기는 호출 경로(`Call` 목록)로 남는다.
- 필수 1(넣기 전 예약): `AddHook` 이 스폰 이벤트 전에 불려 `Compat.bindEntity` 가 같은 UUID 표 칸을 플러그인이 쥔 CraftEntity 로 예약하고 핸들을 서버 핸들로 바꾼다. 이벤트의 `getEntity()` 는 그 객체. UUID 는 세션 엔티티가 고른 것(엔티티 난수 씨앗 `EntityRandom` 이 같아 넣은 엔티티와 같다).
- 필수 2(넣기 취소·실패): `endAdd(false)` 가 핸들을 월드 밖 핸들로 되돌린다. 넣는 중 이벤트 처리기가 한 쓰기는 서버 엔티티에 가면서 월드 밖 기록에도 남는다.
- 필수 3(봉인): 조작 목록을 가져간 뒤 서버 엔티티가 정해지기 전(`adding && target == null`)의 쓰기는 분명한 `UnsupportedFeature`. 정해진 뒤와 넣은 뒤는 서버 엔티티로 넘긴다(주인 스레드면 곧바로, 아니면 `editState` 예약; 읽기는 `ThreadOwnershipException`). 쥐고 있던 속성 객체도 경로 프록시라 같은 규칙이다.
- 필수 5: `ChunkInternals.WorldlessEntity` 표지 + 서버 `ChunkInternalsImpl.ForeignEntity.reject`(어댑터 경계 때문에 다리 클래스 안) — 서버가 엔티티 인자를 받는 다섯 입구가 "월드에 아직 없다" 분명한 오류(UnsupportedOperationException)를 낸다.
- 필수 6: 기록하는 `Method` 는 `org.domi.furnace.api.entity` 인터페이스 메서드(와 `Optional`)만 — 서버가 `ChunkInternalsImpl.StateCalls.check` 로 거절한다. 인자는 `Call` 생성 때 복사·고정한다. 재생은 공개 API 인터페이스를 기록한 대로 다시 부르는 리플렉션이라 "서버 내부를 리플렉션으로 부르는 다리"가 아니다(월드 밖 경로라 성능은 문제없다).

## 쓰기 분류표와 Paper 이벤트 (필수 4)
근거: Paper 1.21.1 패치 소스(paperweight userdev `patchedSourcesJar`)의 `CraftEntity`·`CraftLivingEntity`·`LivingEntity`·`Entity`·`CraftEventFactory`. 분류표는 `DetachedEntityHandle.POLICY`(시험 `everyStateMethodHasExactlyOnePolicy` 가 모든 상태 메서드를 하나씩 분류했는지 지킨다).

| 쓰기 | Paper 가 월드 밖 엔티티에도 내는 이벤트 | Furnace 월드 안 엔티티 | 월드 밖(이 구현) |
|---|---|---|---|
| `addPotionEffect`·`removePotionEffect`·`clearActivePotionEffects` | `EntityPotionEffectEvent`(`LivingEntity.addEffect/removeEffect/removeAllEffects` — 월드 검사 없음) | **내지 않는다**(호환 계층에 이 이벤트 다리가 없다) | 내지 않는다(월드 안과 같은 빈칸) |
| `remove()` | `EntityRemoveEvent`(원인 PLUGIN; `generation` 중이면 없음) | **내지 않는다** | 내지 않는다(같은 빈칸). 지운 표시만 한다 — 넣으면 거절 |
| 한 인자 `setInSittingPose`·`setSitting` | `EntityToggleSitEvent` | 낸다 | **낸다**(shim 이 그 CraftEntity 로 바로 내고, 취소면 그대로, 아니면 이벤트 없이 기록) |
| `setCustomName`·`setInvulnerable`·`setSilent`·`setNoGravity`·`setGlowingTag`·`setNoPhysics`·`setCustomNameVisible`·`setCollidable`·`registerAttribute`·속성 쓰기·`ArmorStand` 의 `setSmall`·`setMarker`·`setArms`·`setBasePlate`·`setInvisible`·`setHealth(>0)` | 없음 | 없음 | 없음 |
| `setHealth(0)` | `die()`(사망 이벤트·전리품) | 월드 안에서만 | 월드 밖은 `UnsupportedFeature`(죽이는 일은 월드의 일) |
| `addPassenger`·`removePassenger`·`ejectPassengers`·`leaveVehicle` | `EntityMountEvent`·`EntityDismountEvent`(월드 밖에서도 낸다고 가정 — 확인 못 함) | 낸다 | `UnsupportedFeature`(서버에 월드 밖 대응 조작이 없다. 달린 것이 없으면 `getPassengers()` 는 빈 목록) |

월드 안에서 이미 비어 있는 두 이벤트(EntityPotionEffectEvent·EntityRemoveEvent)는 월드 밖만 따로 내면 월드 안과 어긋나므로 낸다고 하지 않았다 — `docs/user-todo/entries/2026-11-27-detached-bukkit-entity-done.md`.

## 월드 밖에서 아직 안 되는 것(조용히 틀리지 않고 `UnsupportedFeature`)
엔티티 번호(`getEntityId` — 넣을 때 정해진다), `teleport`, NBT 읽기·쓰기, 영속 데이터, 스케줄러, 인벤토리, `Mob` AI(`editAi`·`aiNow` — 월드 밖 목표는 NMS 목표 선택기가 따로 한다), 월드 밖에서 서로 태운 탈것·승객의 읽기.
레벨을 묻는 읽기(`boundingBox` 의 발밑 블록 등)는 세션 빈 레벨이 답한다 — 대부분 필드라 같다.

## 함정
- `Entity#f()`(shim 내부)는 월드의 핸들만 준다 — `getBukkitEntity()` 가 이제 월드 밖에서도 성공하므로 `f()` 는 따로 `notInWorld` 를 던져 기존 월드 밖 분기 보호를 지킨다.
- 서버 `applyOp`·`createNmsEntity`·`buildNms`·세션 `Detached` 는 `StateCall` 재생이 `host` 를 쓰므로 static 이 아니다.
