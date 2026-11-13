## 2026-11-13 비계·액체 문맥 충돌 모양 (T-018 R6)

PR #305(T-018) 검토 R6 에서 나눈 정확성 수정. 바닐라 1.21.1 에서 충돌 모양이 엔티티 문맥에 달린 블록 중 Furnace 가 데이터 모양만 쓰던 둘을 이식했다.

### 바닐라(1.21.1 소스 extracted_minecraft_data client1.21.1 와 서버 jar 실행으로 확인)
- `ScaffoldingBlock.getCollisionShape`: `isAbove(Shapes.block(), pos, true) && !isDescending()` → STABLE_SHAPE(기둥 넷 + 윗판),
  아니면 `distance != 0 && bottom && isAbove(BELOW_BLOCK(최대 Y 0), pos, true)` → UNSTABLE_SHAPE_BOTTOM(높이 2/16 판), 그 밖에는 빈 모양.
- `LiquidBlock.getCollisionShape`: `isAbove(STABLE_SHAPE(높이 0.5), pos, true) && level == 0 && canStandOnFluid(위 칸 유체, 이 칸 유체)` → STABLE_SHAPE.
  `canStandOnFluid` = 엔티티 술어(LivingEntity.canStandOnFluid — 1.21.1 에서 덮어쓰는 것은 Strider(용암)뿐) && 위 칸 유체가 같은 종류가 아님.
- `EntityCollisionContext`: 만들 때 `isDescending()`(= isShiftKeyDown, 플레이어 포함), 발 높이 `getY()`, 주 손 아이템, 유체 술어를 붙잡는다.
  `isAbove(shape, pos, d)` = `bottom > pos.y + shape.max(Y) − 1.0E-5F`. `CollisionContext.empty()` 는 isAbove 가 d 를 그대로, 웅크림 아님, 유체 술어 거짓.
  그래서 문맥 없음의 비계 = 안정 모양(= BlockInfo 표의 모양), 액체 = 빈 모양.
- 문맥을 엔티티로 만드는 곳: BlockCollisions(이동·noCollision·getCollisions — 플레이어도), ClipContext(entity)(투사체·몹 시야·폭발 노출·떨어지는 블록·
  브리즈·폭죽·물길 isClearForMovementBetween), BlockItem.canPlace(설치하는 플레이어), TryFindLand(NearWater), entityCanStandOn(Face)
  (셜커·셜커 탄환·철 골렘 스폰·물에 빠진 좀비·날기 길찾기 isStableDestination·InsideBrownianWalk), Entity.isColliding.
  VISUAL 광선(시련 생성기·감지기)과 Paper rayTraceBlocks 는 empty.

### Furnace
- `block/CollisionContext`: 위 값을 담는 문맥. `of(Rider)`(엔티티·플레이어), `of(Rider, 발높이)`(옮긴 뒤 묻는 곳), `of(BlockActor)`, `atBottom`(엔더 드래곤 부위),
  `of(발높이, 웅크림, 물, 용암)`(시험). `BlockBehavior.getCollisionShape(state, level, pos, ctx)` 하나로 합쳤다(옛 `getEntityCollisionShape(Entity)`·
  `getCollisionShape(BlockActor)` 둘을 없앰). 문맥 없음은 `CollisionContext.EMPTY`.
- 플레이어가 처음으로 자기 문맥을 쓴다: 전에는 Entity 가 아니라 늘 문맥 없음이었다(비계 = 언제나 안정 모양, 가루눈 = 언제나 빔).
  이동 검증의 `noCollision(player, 옛 상자)`·`isPlayerCollidingWithAnythingNew` 는 바닐라가 player.move 뒤에 부르므로 옮긴 자리 높이(mv[1])를 문맥 발 높이로 넘긴다.
  후렴과 텔레포트(randomTeleport) 판정도 도착 높이.
- `ChunkLevel.CollisionTable`: 비계·물·용암이 DYNAMIC 이 됐다. 물·용암에는 FLUID 표시 — 유체 위에 설 수 있는 몹(스트라이더)이 아니면 동작을 부르지 않고 건너뛴다
  (물속 몹·배·플레이어 수집 비용이 그대로). 문맥 객체는 문맥 블록을 처음 만날 때 만든다.
- 함께 고친 바닐라 차이: 시련 생성기 calculatePositionAbove 는 COLLIDER 가 아니라 VISUAL 광선. entityCanStandOn 을 쓰는 곳 6개가 지지 모양(isFaceSturdy)을 보던 것을
  문맥 충돌 모양의 면으로(잎·영혼 모래 등에서 갈린다). TryFindLandNearWater 의 아래 칸 모양은 바닐라처럼 위 칸 자리(p)에서 묻는다.

### 검증
- `ContextCollisionShapeVanillaTest`: 바닐라 jar 를 직접 돌린 정답표 3100건(비계 32 상태 × 발 높이 17 × 웅크림, 물·용암 5 수준 × 위 칸 유체 6 × 발 높이 8 × 유체 술어 4,
  문맥 없음 포함 — `tools/vanilla-data/ExportContextCollisionShapes.java`). 1/16 칸 채움과, Furnace 가 정확한 격자를 둔 모양의 getCoords(Y)까지 불일치 0.
- `ContextCollisionBehaviorTest`: 플레이어가 비계 위에 서고 웅크려 내려가며 안에서 오를 때 되돌리지 않음, 스트라이더가 용암 원천 위(+0.5)에 섬, 화살이 비계 옆을 지나감,
  가죽 장화 플레이어의 가루눈.
- `CollisionScanParityTest`: 문맥 동작 5개(움직이는 피스톤·가루눈·비계·물·용암)와 FLUID 표시, 생성기에 스트라이더·웅크림·용암 웅덩이를 넣어 옛 사본과 차분 —
  FLUID 건너뛰기를 "늘 건너뜀"으로 바꾸면 4개 시험이 잡는다.

### 남은 차이·함정
- 안정 모양(데이터 모양)의 Y 격자는 바닐라(1/8 전부)와 다르다(Furnace 는 상자 minY·maxY). 엔티티 문맥에서는 안정 모양이 엔티티가 윗면 위에 있을 때만 나오므로
  윗면 말고 다른 격자 좌표는 오르기 후보(0 이상)가 될 수 없어 결과가 같다. 문맥 없는 collide(엔티티 없는 이동)만 다를 수 있는데 게임에서는 부르지 않는다.
- 비계 윤곽 모양(getShape: 비계를 들고 있으면 꽉 찬 블록)의 isHoldingItem 문맥은 아직 없다 — 서버 쪽은 OUTLINE 광선(아이템 쓰기 시선)에서만 보며 비계를 든 채 하는 일이 없다.
- 플레이어 fallDistance 는 PlayerVitals 값(가루눈 0.9 모양). 바닐라와 갱신 시점이 같은지는 이동 검증 쪽 기존 가정을 따른다.
