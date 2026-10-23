# 2026-07-10 io — Anvil 팰릿 Name-only 근사 → Name+Properties 정확 왕복 (air 소실 버그)

## 작업 요약

AnvilChunkCodec 저장 왕복 후 블록 air 소실 버그 근본 원인 규명·수정. 커밋 `4f5cbf9`. 디버깅=소넷 워커, 검증·커밋=Fable.

## 문제 / 목적

`ChunkPipeline.generateFull` → `AnvilChunkCodec.toNbt/fromNbt` 왕복 후 블록 소실 — seed 1234 기준 (0,0) 22개·(-5,3) 31개·(7,-2) 73개. parity-verification-infra task의 ChunkPipelinePersistenceParityTest가 최초 발견.

## 해결 방법 / 구현 내용

근본 원인 2단 (증거 기반):
1. **`BlockRegistry.nameOf`가 non-default state id를 air로 오폴백** — `nameByDefaultId`는 블록당 default id 1개만 보유. worldgen이 만드는 non-default state(`tall_seagrass;half=upper`=2009, glow_lichen 변형 등)는 맵에 없어 팰릿 `Name`에 `minecraft:air` 기록 → 읽기서 air(0). 증거: PERSIST_MM 전 라인 `got=0`, mismatch id 전부 `blocks/1.21.1.txt`(default만) 미존재 + `blockstates_1.21.1.txt`(26684 state)엔 존재.
2. **팰릿에 `Properties` 미기록 (기존 설계 "속성 무시" 근사)** — 1차만 고치면 air 대신 default variant로 뭉개짐 (`exp=2009 got=2010`, 건수 22/31/73 그대로).

수정:
- `BlockRegistry`: 역방향 `keyByAnyId`(id→canonical key, 전체 26684 커버) + `canonicalKeyOf()` 추가, `nameOf` 전체 state id 커버
- `AnvilChunkCodec`: 팰릿 엔트리를 `Name`+`Properties`(비-default)로 기록, 읽기는 기존 미사용 exact 오버로드 `stateIdOrAir(name, props)` 사용
- `ChunkPipelinePersistenceParityTest`: 상한 22/31/73 → 0 exact 승격

검증: 3좌표 0/0/0 mismatch, full suite BUILD SUCCESSFUL 14m 11s.

## 핵심 코드

```java
// AnvilChunkCodec — 팰릿 엔트리: canonical key(name;k=v,...)를 Name + Properties로 분해
private NbtCompound paletteEntryOf(int stateId) {
    NbtCompound entry = new NbtCompound();
    String key = registry.canonicalKeyOf(stateId);
    if (key == null) { entry.putString("Name", registry.nameOf(stateId)); return entry; }
    int semi = key.indexOf(';');
    if (semi < 0) { entry.putString("Name", key); return entry; }
    entry.putString("Name", key.substring(0, semi));
    NbtCompound props = new NbtCompound();
    for (String kv : key.substring(semi + 1).split(",")) {
        int eq = kv.indexOf('=');
        if (eq < 0) continue;
        props.putString(kv.substring(0, eq), kv.substring(eq + 1));
    }
    entry.putCompound("Properties", props);
    return entry;
}
```

## 관련 파일

- [[2026-07-10_worldgen_parity-verification-infra-테스트3종]] — 버그 발견 경위(영속 게이트)
- [[2026-07-04_io_compression-regionfile-버퍼링]] — 같은 io 레이어
- [[레이어-영속성]]

## 태그

#Furnace #io #BugFix
