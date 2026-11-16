# T-078 어댑터 감싸개 "지금 값" 읽기 정리 + 스폰 이벤트 구역 이동 뒤 지움 (2026-11-13)

- `CraftEntity.getType()`: 종류는 엔티티가 사는 동안 안 바뀌므로 처음 한 번만 `EntityType.fromKey`(문자열 정규화)로 찾고 객체에 붙인다. Bukkit 열거형에 없는 종류는 붙이지 않고 매번 `UnsupportedFeature`(옛 동작 그대로).
- `getName()`: 이름 없는 몹의 기본 이름("Iron Golem")은 종류 키만으로 정해져 처음 한 번만 만든다. `getCustomName()` 은 핸들 시점 이름을 미리 변환해 두던 것을 소유 스레드가 아닐 때만 변환하도록 바꿨다(표지 `GONE` 은 동일성으로만 비교 — `null` 은 "이름 없음"이라 값과 구별해야 한다).
- `getLocation()`/`getWorld()`: 중간 Furnace `Location`(옛 `liveLocation`)과 핸들 시점 값 미리 만들기를 없앴다. `getWorld()` 는 위치를 만들지 않는다. 사라진 몹·소유 스레드가 아닌 곳은 핸들 시점 값(옛 동작과 같다).
- `CraftLivingEntity.getHealth/getMaxHealth`: 핸들 시점 값을 미리 읽던 것을 필요할 때만.
- 월드 감싸개 조회(`Compat.world`)는 T-071(`handleFor` 빠른 길)이 맡는다 — 여기서는 건드리지 않았다.
- 시험 `EntityHotReadsTest`: 모든 `EntityType` 키에 대해 옛 구현(시험 안에 복사)과 `getType`·`getName` 전수 비교, 없는 종류 예외 반복, 텔레포트·이름·삭제 뒤 지금 값, 소유 스레드가 아닐 때, 두 번째 호출부터 할당 0(스레드별 할당량).
- **#230 남은 틈(서버)**: 스폰 이벤트 처리기가 엔티티를 다른 구역으로 옮긴 뒤 지우면, 추적기가 없는 동안 `setPosRaw` 가 구역 이동을 알리지 않아 `EntityStore.remove` 가 새 구역 키로 목록을 찾았다 → 옛 구역 목록에 지운 엔티티가 남아 `forEachIn`·`inChunk` 에 잡혔다. `Entity.storedSection`(목록에 실제로 넣은 키)을 저장소가 기억해 `remove`·`moved` 가 그 키를 쓴다. 시험 `EntitySpawnRemoveTest#moveThenRemoveInsideSpawnEventLeavesNoStaleSectionEntry`(고치기 전 실패 확인).
- PR #314 독립 검토 반영: (R1) `setPosRaw` 의 구역 이동 알림 조건을 `tracker != null` 에서 `tracker != null || store != null` 로 — 추적 거리 0 인 마커도 옮기면 구역 목록이 따라간다(바닐라 onMove 는 추적과 무관). 게임 이벤트 리스너 `move` 는 추적 시작 뒤(`tracker != null`)에만 부른다(`DynamicGameEventListener.move` 는 `add` 와 같아 스폰 이벤트 도중 미리 등록될 수 있다). (R2) 옮긴 뒤 스폰 취소 시험 추가. (R3) `EntityStore.moved`·`ChunkLevel.entitySectionChanged` 의 안 쓰는 옛 키 인자 제거. 보고서 `furnace/reviews/t078-pr314.md`.
