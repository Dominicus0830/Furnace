# 월드 밖 NMS 엔티티의 Bukkit 엔티티 — 구현 끝, 남은 빈칸 (2026-11-27)

**코디네이터 확정(도미니코 위임): Paper 처럼 맞춘다**(`2026-11-27-entity-detached-bukkit.md`)를 구현했습니다. 이제 `EntityType#create` 로 만들어 아직 월드에 넣지 않은 엔티티에서 `getBukkitEntity()` 를 꺼내면 그 종류의 Bukkit 엔티티가 나옵니다(이름·무적·갑옷 거치대 크기 같은 쓰기를 기록해 두었다가 월드에 넣을 때 그대로 적용, 넣은 뒤에도 같은 객체).

| 무엇 | Paper 는 | Furnace 는 | 추천 |
|---|---|---|---|
| 월드 밖 엔티티에 물약 효과를 걸거나 지울 때 `EntityPotionEffectEvent` | 낸다(월드 밖도) | 월드 안 엔티티에도 아직 안 낸다(월드 밖도 같음) | 월드 안·밖 함께 호환 계층에 이벤트를 만든다 — 별도 작업 |
| `remove()` 의 `EntityRemoveEvent` | 낸다 | 월드 안·밖 모두 안 낸다 | 위와 함께 |
| 월드 밖 엔티티의 탑승(`addPassenger` 등) | 된다 | 분명한 오류(`UnsupportedFeature`) | 월드 밖끼리 태우는 서버 조작이 필요 — 필요한 플러그인이 나올 때 |
| 월드 밖 엔티티의 `getEntityId`·`teleport`·NBT·영속 데이터·스케줄러 | 된다 | 분명한 오류 | 필요한 플러그인이 나올 때 |

- 안 하면? 플러그인이 이 값을 부르면 조용히 틀린 값이 아니라 무엇이 빠졌는지 알려 주는 오류를 받습니다. 이벤트가 안 나가는 두 가지는 월드 안 엔티티도 같아 새로 생긴 차이는 아닙니다.
- 근거·표: `docs/feature-parity/context-notes/2026-11-27-detached-bukkit-entity.md`.
