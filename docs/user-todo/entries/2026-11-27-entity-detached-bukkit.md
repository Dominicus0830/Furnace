# 월드 밖 NMS 엔티티의 Bukkit 엔티티(getBukkitEntity) — 2026-11-27

근거: PR 659(앉은 자세 EntityToggleSitEvent), 독립 검토 승인(11-27).

**결정(2026-11-27, 코디네이터 확정 — 도미니코 위임): Paper 처럼 맞춘다. 엔티티 영역 할 일(DamageSource shim 다음).**

**한 줄 요약:** 플러그인이 `EntityType#create` 로 만들어 아직 월드에 넣지 않은 NMS 엔티티(FancyNpcs 같은 패킷 NPC)에서 Bukkit 엔티티를 꺼내면, Paper 는 꺼내 주지만 Furnace 는 아직 "월드에 없음" 오류를 냅니다.

| 무엇 | Paper 는 | 지금 Furnace 는 | 정한 방향 |
|---|---|---|---|
| 월드 밖 늑대·고양이·여우에 한 인자 `setInSittingPose(sit)`·`setSitting(sit)` | `EntityToggleSitEvent` 를 내고(플러그인이 취소 가능) 앉힌다 | `getBukkitEntity` 처럼 분명한 오류(UnsupportedNmsCall). FancyNpcs 가 쓰는 두 인자 꼴(이벤트 없음)은 동작한다 | 월드 밖 엔티티의 `getBukkitEntity` 를 지원해 Paper 처럼 이벤트를 낸다 |
| 그 밖의 `getBukkitEntity` 를 거치는 길(이벤트 생성 등) | 월드 밖 엔티티도 Bukkit 엔티티가 있다 | 같은 오류 | 위와 함께 |

- 안 하면? 월드 밖 엔티티에 이벤트를 내는 NMS 메서드를 부르는 플러그인이 오류를 받습니다(조용히 틀리지는 않습니다). 바꾸는 길이 막혀 있던 증명(G2 정답기)도 함께 풀립니다.
