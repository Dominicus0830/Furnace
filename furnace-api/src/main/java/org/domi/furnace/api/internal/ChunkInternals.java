// 청크 내부 읽기 다리(N2a) — Bukkit·Paper 호환 계층의 NMS 청크 shim(LevelChunk·LevelChunkSection·PalettedContainer·Heightmap)이
// 서버 청크를 "보기"로 읽는 길. 블록 상태 번호(BlockTypes.stateId 와 같은 번호)·바이옴 레지스트리 번호 단위로 다룬다.
//
// 불안정 내부 다리다(Internals 와 같은 등급, 호환 계층 전용) — 일반 플러그인은 World·Block API 를 쓴다. 쓰기는 단일 칸 두 개뿐이다:
// 바닐라 Level.setBlock(플래그·깊이 그대로)과 LevelChunk.setBlockState. 섹션·팔레트 직접 쓰기는 없다(N2b).
package org.domi.furnace.api.internal;

import java.util.List;
import org.domi.furnace.api.world.World;

/**
 * 청크 섹션·하이트맵·바이옴의 번호 단위 읽기. "Now" 가 붙은 것은 그 청크를 가진 region 스레드(전역 빌림 구간 포함)에서만 되고,
 * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}. 청크가 로드돼 있지 않으면 표시값(null·-1·{@link Integer#MIN_VALUE})을
 * 돌려줄 뿐 청크를 만들거나 불러오지 않는다. 섹션 번호는 0 = 맨 아래 섹션(바닐라 getSectionIndex).
 */
public interface ChunkInternals {

    /** 그 차원의 가장 낮은 블록 y(바닐라 getMinBuildHeight). 아무 스레드. */
    int minY(World world);

    /** 그 차원의 높이(블록 수, 바닐라 getHeight). 아무 스레드. */
    int height(World world);

    /** 지금 로드돼 있는가(최근 값). 아무 스레드. */
    boolean isLoaded(World world, int chunkX, int chunkZ);

    /** 지금 스레드가 그 청크를 가진 region 스레드인가(전역 빌림 구간이면 빌려 본다). 아무 스레드. */
    boolean ownsChunk(World world, int chunkX, int chunkZ);

    /** 그 칸의 블록 상태 번호. 높이 밖이면 공기(0)가 아니라 그 값 그대로 요청하지 말 것 — 높이 밖은 부르는 쪽이 거른다. 로드돼 있지 않으면 -1. */
    int stateIdNow(World world, int x, int y, int z);

    /** 섹션의 블록 상태 번호 4096개(YZX, 새 배열). 로드돼 있지 않으면 null. */
    int[] sectionStatesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 섹션의 공기 아닌 칸 수(air·cave_air·void_air 는 공기). 0 인지(바닐라 hasOnlyAir)는 바닐라와 같지만 값은 바닐라 nonEmptyBlockCount 와
     * 다를 수 있다(바닐라는 유체 있는 칸을 한 번 더 센다). 로드돼 있지 않으면 -1.
     */
    int sectionNonAirNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션에 무작위 틱 받는 칸(블록 또는 그 유체)이 있는가(바닐라 isRandomlyTicking). 로드돼 있지 않으면 false. */
    boolean sectionRandomlyTickingNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션의 바이옴 레지스트리 번호 64개(4×4×4, YZX). 로드돼 있지 않으면 null. */
    int[] sectionBiomesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 바이옴 레지스트리의 키 목록(번호 순, 데이터 팩 바이옴 포함). 아무 스레드. */
    List<String> biomeKeys();

    /**
     * 하이트맵의 첫 빈 칸 y(바닐라 Heightmap.getFirstAvailable = 가장 높은 해당 블록 y + 1). heightmap 은 바닐라 직렬화 키
     * (WORLD_SURFACE·OCEAN_FLOOR·MOTION_BLOCKING·MOTION_BLOCKING_NO_LEAVES). 로드돼 있지 않으면 {@link Integer#MIN_VALUE},
     * 모르는 키면 IllegalArgumentException.
     */
    int firstAvailableNow(World world, String heightmap, int x, int z);

    /** 청크를 저장 대상으로 표시한다(바닐라 setUnsaved(true)). 로드돼 있지 않으면 false. */
    boolean markUnsavedNow(World world, int chunkX, int chunkZ);

    /**
     * 바닐라 Level.setBlock(pos, state, flags, maxUpdateDepth) 그대로 — 플래그 1·2·16·32·64 와 남은 깊이, 클라이언트 알림은 틱 끝에 모아 보내기,
     * 길찾기·POI 갱신까지. 바뀌었으면 true, 높이 밖·로드돼 있지 않음·같은 상태면 false.
     */
    boolean setBlockNow(World world, int x, int y, int z, int stateId, int flags, int maxUpdateDepth);

    /**
     * 바닐라 LevelChunk.setBlockState 그대로 — 그 칸만 바꾸고 놓기·치우기 반응·블록 엔티티·빛·하이트맵·저장 표시까지(이웃·모양 갱신, 클라이언트 알림,
     * POI 없음). 옛 상태 번호, 바뀌지 않았거나(같은 상태·공기뿐인 섹션에 공기) 치우기 반응이 칸을 갈아 치웠거나 높이 밖·로드돼 있지 않으면 -1.
     */
    int setBlockStateNow(World world, int x, int y, int z, int stateId, boolean moved);
}
