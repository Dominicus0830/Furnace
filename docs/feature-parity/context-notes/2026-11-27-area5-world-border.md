# 2026-11-27 영역5: NMS WorldBorder shim 을 서버 경계에 연결

- g4 짝 목록(WorldBorder 30건)의 짝은 서버 `world.WorldBorder` 로 이름·식이 같음을 읽어 확인했다(getDistanceToBorder·getStatus·lerp·피해·경고 등 바닐라 이식본).
- 다리: `ChunkInternals.borderSnapshot`(값 16개 한 묶음)·`borderDistance`·`borderSet`(op 0~7). 서버 경계는 synchronized 라 아무 스레드, 설정자는 서버 설정자를 그대로 불러 듣는 이 알림도 서버가 보낸다.
- `BorderStatus`는 enum 상수를 spec 에 적고 `getColor`만 손 구현(서버 `WorldBorder.Status` 와 순서 같음).
- 남은 것: `tick`·`applySettings`·듣는 이 등록·`getDistanceToBorder(Entity)`·AABB/ChunkPos 판정은 아직 던진다(경계 틱은 서버가 돌린다).
