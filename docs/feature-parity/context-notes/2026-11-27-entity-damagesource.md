# 2026-11-27 엔티티 영역 — 호환 계층 DamageSource shim

## 한 것
- `net.minecraft.world.damagesource` 손 클래스: `DamageType`(레코드, 생성자 5개 바닐라 그대로), `DamageScaling`·`DamageEffects`(소리 포함)·`DeathMessageType`(데이터 팩 이름),
  `DamageTypes`(키 47개), `DamageSource`(Paper 1.21.1 필드·규칙), `DamageSources`(Paper 메서드·형식 선택 그대로). `net.minecraft.tags.DamageTypeTags`(33개).
- 레지스트리: `NmsDamageTypes.registry()` — 서버 `entries("minecraft:damage_type")` 순서 + 새 API `GameRegistries.damageType(key)` 정의. `RegistryAccess.SERVER` 에 등록,
  `Registries.DAMAGE_TYPE` 를 `ResourceKey<Registry<DamageType>>` 로. 태그는 `NmsMappedRegistry` 가 서버 태그를 읽는다.
- `Level.damageSources()`(레벨마다 하나, 처음 부를 때 만든다 — 바닐라는 생성자), `Entity.damageSources()` = `level().damageSources()`.
- 루트 다리: `LootContextParams.DAMAGE_SOURCE` 를 `LootContextParam<DamageSource>` 로 되돌리고 `LootTable` 이 이름 대신 직접 부른다.

## Paper 와 맞춘 규칙(javap 확인)
- `sweep()`·`melting()`·`poison()`·`critical(b)` 는 자기를 고치고 자기를 돌려준다. `DamageSources` 의 엔티티 없는 출처는 공유 객체라 이것도 Paper 와 같이 공유 객체를 바꾼다.
- `customEventDamager`·`directBlock`·`directBlockState` 는 복사본(`cloneInstance`)에 싣는다. 복사는 직접 블록·블록 상태·sweep·poison·melting 만 옮긴다(critical·이벤트 가해자는 안 옮김).
- `customEventDamager` 는 직접 엔티티가 있으면 Paper 문구 그대로 IllegalStateException.
- `fireball(src, null)` 은 unattributed_fireball, `explosion(a, b)` 는 둘 다 있으면 player_explosion.

## 남은 구멍(껍데기가 던진다)
- `DamageSource.getWeaponItem`·`getLocalizedDeathMessage`: 엔티티 shim 에 `getWeaponItem`·`getDisplayName`·`getKillCredit` 손 구현이 없다.
- `DamageSources.explosion(Explosion)`: 호환 `Explosion` 이 빈 자리표시라 폭발 원인을 못 읽는다.
- 플러그인이 만든 DamageSource 로 엔티티를 때리는 `Entity.hurt(DamageSource, float)` 는 이 묶음 밖(다음 일).
- `DamageType.CODEC`·`STREAM_CODEC` 같은 코덱 필드는 없다(껍데기 병합이 채운다).
- 서버 피해 형식은 바닐라 47개뿐이다(데이터 팩 피해 형식 추가는 서버가 아직 읽지 않는다 — 가정이 아니라 서버 `DamageType` 이 리소스 파일만 읽음).
