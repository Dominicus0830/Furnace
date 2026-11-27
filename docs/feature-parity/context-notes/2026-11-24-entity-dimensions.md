# 엔티티 몸 크기 읽기(영역 2, 2026-11-24)

- shim `Entity#getBbWidth`·`getBbHeight`·`getEyeHeight()` 는 월드의 엔티티면 `EntityLook` 의 서버 값(주인 스레드, 플레이어는 아무 스레드), 아직 월드에 없는 엔티티면
  `DetachedEntityData#dimensions()` — 빈 레벨(SandboxLevel)에서 기록을 재생한 서버 엔티티의 값이다. 크기 규칙(아기·자세·크기 칸 → refreshDimensions)은 서버 엔티티
  클래스 하나에만 있다. 버리는 엔티티를 전역 번호 발급기로 만들면 엔티티 번호가 바닐라와 어긋나므로 빈 레벨 재생만 쓴다.
- 서버 `Player.eyeHeight()` 는 바닐라처럼 float(1.62F·1.27F·0.4F·0.2F)다.

## 후속(이번 PR 범위 밖, 가정 — 바닐라 jar 로 확인 필요)
- 서버 `Player.eyeY()` 는 double 리터럴(1.62)을 더한다. 바닐라 `getEyeY` 는 float 눈높이를 넓혀 더하므로(1.62F = 1.6200000047683716) 마지막 자리가 다를 수 있다.
- 서버 `Display.setSize` 는 몸 크기도 바꾼다. 바닐라 1.21.1 Display 의 너비·키 칸은 그리기 범위용이고 몸 크기(종류 0×0)는 바꾸지 않는 것으로 안다.
