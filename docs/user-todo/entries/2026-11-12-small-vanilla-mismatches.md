# 엔티티·AI 작은 바닐라 차이 묶음 (O05 C2·C4) — 2026-11-12

속성 계산 순서를 바닐라와 맞췄다(O06 C2·C3 는 PR #274). 근거·대조는 `docs/feature-parity/context-notes/2026-11-12-small-vanilla-mismatches.md`.

## 결정할 것 (작업을 막지 않음)

1. **데이터 팩 블록 태그를 게임 동작(엔티티·블록 로직의 `B.isTag`)에 반영할지.** O05 C4 는 "/reload 때 `B.isTag` 캐시를 비워야 한다"였는데 확인해 보니 비울 캐시가 없다. `B.isTag` 는 jar 에 든 바닐라 블록 태그(`BlockTags`)만 읽고 `/reload` 는 이를 건드리지 않아, 캐시와 원본이 늘 같다. 대신 더 큰 차이가 드러났다: 데이터 팩이 `data/<ns>/tags/block/*.json` 으로 블록 태그(예: `minecraft:climbable`, `minecraft:wall_post_override` 에 블록 추가)를 바꿔도 서버 동작에는 반영되지 않는다(클라이언트로 보내는 태그 목록에만 실린다).
   추천: 따로 TASK 로 연다(`TagSet` "minecraft:block" 을 `B.isTag` 에 연결하고 `DataPackManager` 가 태그를 걸 때 캐시를 비운다 — 블록 id 번호 체계가 서로 같은지부터 확인). 바닐라 서버는 반영하므로 데이터 팩을 쓰는 서버에서 차이가 난다. 모양은 작지만 `B.isTag` 호출처가 많아(엔티티 73곳 등) 시험 범위가 넓다.
