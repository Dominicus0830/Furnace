# 호환 Entity#getEntityId (2026-11-12)

PR #246 검토에서 나온 공백: 호환 Bukkit `Entity` 에 `getEntityId()` 가 없어 엔티티 id 를 쓰는 ProtocolLib 플러그인이 `NoSuchMethodError` 로 죽었다.

- Furnace API `Entity#entityId()` 추가(핸들을 만든 순간의 서버 엔티티 id, 플레이어 포함). 네트워크 패킷이 쓰는 번호와 같다.
- Bukkit `Entity#getEntityId()` = 그 값(Paper 와 같다). NMS shim `Entity#getId()` 도 같은 값을 돌려준다(이전엔 `UnsupportedNmsCall`).
- Paper 의 `Entity#getLocation(Location)`(주어진 Location 을 채워 돌려줌, null 이면 null) 추가.
- 점검했으나 이 PR 에서 넣지 않은 흔한 Entity 메서드(속도·바닥 여부·불 틱·살아 있던 틱·경계 상자·크기·장면 태그·`getFacing` 등)는 Furnace 공개 API 에 해당 읽기·쓰기가 없어 별도 작업이다(위 이름들이 여전히 없다).

## 후속 (PR #248 검토 R1·R3·R4)

- **R1 머리 yaw**: Paper `CraftEntity#getLocation()`/`getLocation(Location)` 의 yaw 는 `getBukkitYaw()` — 살아 있는 엔티티는 머리 방향(`getYHeadRot`), 그 밖은 몸 방향. Furnace API `Entity#headYaw()` 를 더했다(핸들 시점 값, 기본은 `location().yaw()`; 서버 `EntityHandle` 은 살아 있는 엔티티에서 `yHeadRot`). 호환 `CraftEntity#getLocation` 이 그 값을 yaw 로 쓴다. Furnace 네이티브 `Entity#location()` 의 yaw 는 그대로 몸 방향이다. NMS shim `Entity#getYRot`(몸)·`getYHeadRot`(머리)·`getBukkitYaw` 도 맞췄다. 플레이어는 바닐라가 몸=머리라 그대로.
- **R4 id → 엔티티 역조회**: ProtocolLib `getEntityModifier(world).read(0)`·`getEntityFromID` 는 NMS 월드에서 `Entity getEntity(int)` 를 찾는다(`EntityUtilities#getEntity`, 5.4.0 확인). shim `Level#getEntity(int)`/`ServerLevel#getEntity(int)` 를 더했다(Spigot 이름 `a(int)` 은 대응표가 이어 준다). 서버는 월드(차원)마다 `id → 서버 엔티티` 색인(`World#entityIndex`, 레벨이 엔티티를 넣고 뺄 때 같이 고친다 — region 사이를 넘어가는 중에도 남는다)을 갖고, API `World#entityById(int)`(비동기, 소유 region 이 읽은 새 핸들, 이동 중이면 3번까지 다시)·`entityByIdNow(int)`(소유 스레드 또는 빌림 구간, 아니면 ThreadOwnershipException; 플레이어는 어느 스레드나)로 연다.
  - 호환 `getEntity(int)` (검토 R1·R3 반영): Paper·Folia 의 `EntityLookup#get(int)` 는 어느 스레드에서나 기다림 없이 돌려준다. Furnace 는 그 엔티티의 Bukkit 객체를 이미 알면(같은 UUID 의 `Compat` 캐시, `World#entityUuidById` 로 id→UUID 만 읽는다) 어느 스레드에서나 곧바로 마지막 스냅샷의 shim 을 준다. 소유 스레드(빌림 구간 포함)면 새 스냅샷으로 갈아 낀 뒤 준다. **처음 보는 엔티티**만 스냅샷을 그 region 이 떠야 해서 틱 스레드가 아닌 스레드(네트워크·비동기)는 최대 1초(`-Dfurnace.compat.entityLookupWaitMillis`) 기다리고, 틱 스레드(region·전역)가 남의 region 의 처음 보는 엔티티를 찾으면 두 region 이 서로를 기다리는 교착을 막으려고 ThreadOwnershipException 이다(`FurnaceScheduler#isTickThread` 로 가른다 — 이 한 가지가 Paper·Folia 와 다르다).
  - 비동기 `entityById`: region 경계를 넘은 틱에는 엔티티가 새 청크에 있어도 옛 region 저장소에서 아직 안 빠졌다(틱 끝 `handOffEntities`) — 새 칸의 주인에 없으면 지난 틱 칸(`xo`,`zo`)의 주인에서 한 번 더 찾는다. `entityByIdNow` 는 소유 검사에 저장소 소속(`contains`)도 본다.
  - 남은 것(낮음): 플레이어는 색인에 없어 접속자를 훑는다(O(접속자) — 가짜 엔티티 id 조회가 아주 많으면 플레이어 id 색인으로).
  - 같은 UUID 의 같은 `CraftEntity`·`getHandle()` 을 돌려준다(새 스냅샷 값으로 갈아 낀 뒤).
- **R3 차원 이동 뒤 옛 Bukkit 객체의 id**: 맞추지 않았다 — `docs/user-todo/entries/2026-11-12-entity-id-lookup.md` 에 기록.
