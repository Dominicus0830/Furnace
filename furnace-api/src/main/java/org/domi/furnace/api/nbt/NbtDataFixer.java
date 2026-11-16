// 옛 판(DataVersion) 데이터를 이 서버의 판(1.21.1 = 3955)으로 올리는 데이터 픽서(바닐라 DataFixerUpper 의 이 서버 구현) — Furnace.server().dataFixer().
// 구조물 템플릿 읽기와 같은 고침 표를 쓴다. 지원 범위는 {@link #minSupportedVersion()} ~ {@link #currentVersion()} 이고, 밖의 판은 고치지 않고 그대로 돌려준다.
package org.domi.furnace.api.nbt;

public interface NbtDataFixer {

    /** 이 서버의 DataVersion(1.21.1 = 3955). */
    int currentVersion();

    /** 고칠 수 있는 가장 옛 DataVersion(1.13 평탄화 직후 1452). */
    int minSupportedVersion();

    /** 그 DataVersion 의 데이터를 고칠 수 있는가(이미 최신이면 고칠 것 없음 — true). */
    boolean supports(int dataVersion);

    /** 블록 이름({@code minecraft:grass} → {@code minecraft:short_grass}). 못 고치는 판이면 그대로. */
    String fixBlockName(String name, int fromVersion);

    /** 아이템 이름({@code minecraft:rose_red} → {@code minecraft:red_dye}). 못 고치는 판이면 그대로. */
    String fixItemName(String name, int fromVersion);

    /** 블록 상태 {@code {Name, Properties}} 를 고친 사본(원본은 그대로). 못 고치는 판이면 사본 그대로. */
    NbtCompound fixBlockState(NbtCompound state, int fromVersion);

    /** 아이템 칸({@code id}·{@code Count}/{@code count}·{@code tag}/{@code components})을 고친 사본. 못 고치는 판이면 사본 그대로. */
    NbtCompound fixItemStack(NbtCompound item, int fromVersion);

    /** 블록 엔티티({@code id} 가 있는 꼴) 를 고친 사본. 못 고치는 판이면 사본 그대로. */
    NbtCompound fixBlockEntity(NbtCompound blockEntity, int fromVersion);

    /** 엔티티({@code id} 가 있는 꼴, 탑승자 포함) 를 고친 사본. 못 고치는 판이면 사본 그대로. */
    NbtCompound fixEntity(NbtCompound entity, int fromVersion);
}
