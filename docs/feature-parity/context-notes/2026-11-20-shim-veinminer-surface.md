# 2026-11-20 shim 5번 묶음 함정

- `PlayerInventory.replace` 는 객체 동일성 비교다. 새 스택 객체를 만드는 쪽(손 칸 되쓰기)은 값 비교로 바꿔야 한다.
- 새 Brigadier 대역 소스 세트는 컴파일·시험 전용. `ArgumentType` 구현 클래스는 `CompatLinkTest` 가 건너뛴다.
- 오프라인(`--offline`) 로는 `realWorld` 설정이 풀리지 않아 compat 시험이 안 돈다 — 온라인으로 돌린다.
