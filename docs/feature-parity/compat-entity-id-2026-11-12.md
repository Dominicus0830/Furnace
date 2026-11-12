# 호환 Entity#getEntityId (2026-11-12)

PR #246 검토에서 나온 공백: 호환 Bukkit `Entity` 에 `getEntityId()` 가 없어 엔티티 id 를 쓰는 ProtocolLib 플러그인이 `NoSuchMethodError` 로 죽었다.

- Furnace API `Entity#entityId()` 추가(핸들을 만든 순간의 서버 엔티티 id, 플레이어 포함). 네트워크 패킷이 쓰는 번호와 같다.
- Bukkit `Entity#getEntityId()` = 그 값(Paper 와 같다). NMS shim `Entity#getId()` 도 같은 값을 돌려준다(이전엔 `UnsupportedNmsCall`).
- Paper 의 `Entity#getLocation(Location)`(주어진 Location 을 채워 돌려줌, null 이면 null) 추가.
- 점검했으나 이 PR 에서 넣지 않은 흔한 Entity 메서드(속도·바닥 여부·불 틱·살아 있던 틱·경계 상자·크기·장면 태그·`getFacing` 등)는 Furnace 공개 API 에 해당 읽기·쓰기가 없어 별도 작업이다(위 이름들이 여전히 없다).
