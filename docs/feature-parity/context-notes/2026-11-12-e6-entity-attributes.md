# E6 엔티티 속성 API (2026-11-12)

- Bukkit 1.21.1 원문(Youer 사본의 CraftAttributeInstance·CraftAttributeMap)을 동작 확인용으로만 읽었다: `addModifier`=addPermanentModifier(중복이면 바닐라 IAE), `addTransientModifier`는 Paper, `getDefaultValue`=속성 기본값(엔티티 시작 값 아님), `setBaseValue`는 값을 자르지 않는다(자르기는 `getValue`).
- 옛 이름 변환 표는 Paper `CraftAttributeMap.legacyNMS` 와 같은 목록을 `Attribute.find` 에 넣었다(+ `horse.jump_strength`).
- 함정: 서버 `AttributeMap.register` 는 새 속성도 `version` 을 올려야 스냅샷·저장이 새 속성을 본다(전에는 올리지 않았다). 클라이언트로는 보내지 않는다(바닐라 registerAttribute 도 같다).
- 함정: 시험에서 몹은 틱하는 플레이어가 없으면 자동 틱하지 않는다 — 최대 체력 잘림(`refreshDirtyAttributes`)은 `raw.tick()` 을 직접 불러 확인한다.
- 플레이어 `Region.ownsPlayer` 는 `players` 맵에 있어야 참이다 — 시험은 `r.players().put` + `p.ownerRegion = r` 로 만든다.
