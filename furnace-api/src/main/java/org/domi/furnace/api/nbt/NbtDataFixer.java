// 옛 버전(DataVersion) 데이터를 이 서버의 버전(1.21.1 = 3955)으로 올리는 데이터 픽서다(바닐라 DataFixerUpper 의 이 서버 구현). Furnace.server().dataFixer() 로 얻는다.
// 구조물 템플릿 읽기와 같은 고침 표를 쓴다. 지원 범위는 {@link #minSupportedVersion()} 이상 {@link #currentVersion()} 이하이고, 범위 밖의 버전은 고치지 않고 그대로 돌려준다.
package org.domi.furnace.api.nbt;

/**
 * 옛 버전 데이터를 이 서버의 버전으로 올린다. 문서: {@code docs/api/data-fixer.md}.
 *
 * <p><b>지원 범위.</b> {@code fromVersion} 이 {@link #minSupportedVersion()}(1452, 1.13 평탄화 직후) 이상 {@link #currentVersion()}(3955) 이하일 때만 고친다.
 * 올리는 목표는 늘 {@link #currentVersion()} 이다(중간 버전으로 올리는 기능은 없다). 1451 이하(1.12 숫자 ID 시대)와 서버보다 새 버전은 <b>고칠 수 없는 버전</b>이라 고치지 않는다.
 *
 * <p><b>고칠 수 없는 버전 처리.</b> 예외를 던지지 않는다. 이름 메서드는 입력 문자열을 그대로, NBT 메서드는 입력의 <b>사본</b>을 돌려준다.
 * 이미 최신(3955)인 데이터도 같다. 먼저 {@link #supports(int)} 로 확인하면 "고쳤는지" 알 수 있다.
 *
 * <p><b>입력.</b> NBT 매개변수는 건드리지 않는다(항상 사본을 고쳐 돌려준다). 이름공간이 없는 이름({@code grass})은 {@code minecraft:} 를 붙여 본다.
 *
 * <p><b>스레드.</b> 어느 스레드에서나 부를 수 있다. 상태를 갖지 않고 고침 표는 읽기 전용이며, 월드·플레이어 상태를 건드리지 않는다.
 * 서버가 아직 데이터를 읽기 전(부트스트랩 이전)에는 부르지 않는다.
 */
public interface NbtDataFixer {

    /** 이 서버의 DataVersion(1.21.1 = 3955). */
    int currentVersion();

    /** 고칠 수 있는 가장 옛 DataVersion(1.13 평탄화 직후 1452). */
    int minSupportedVersion();

    /** 그 DataVersion 의 데이터를 고칠 수 있는가(이미 최신이면 고칠 것이 없으므로 true). */
    boolean supports(int dataVersion);

    /** 블록 이름을 고친다(예: {@code minecraft:grass} 는 {@code minecraft:short_grass} 가 된다). 고칠 수 없는 버전이면 그대로 둔다. */
    String fixBlockName(String name, int fromVersion);

    /** 아이템 이름을 고친다(예: {@code minecraft:rose_red} 는 {@code minecraft:red_dye} 가 된다). 고칠 수 없는 버전이면 그대로 둔다. */
    String fixItemName(String name, int fromVersion);

    /** 블록 상태 {@code {Name, Properties}} 를 고친 사본(원본은 그대로). 고칠 수 없는 버전이면 사본 그대로다. */
    NbtCompound fixBlockState(NbtCompound state, int fromVersion);

    /** 아이템 칸({@code id}·{@code Count}/{@code count}·{@code tag}/{@code components})을 고친 사본. 고칠 수 없는 버전이면 사본 그대로다. */
    NbtCompound fixItemStack(NbtCompound item, int fromVersion);

    /** 블록 엔티티({@code id} 가 있는 꼴)를 고친 사본. 고칠 수 없는 버전이면 사본 그대로다. */
    NbtCompound fixBlockEntity(NbtCompound blockEntity, int fromVersion);

    /** 엔티티({@code id} 가 있는 꼴, 탑승자 포함)를 고친 사본. 고칠 수 없는 버전이면 사본 그대로다. */
    NbtCompound fixEntity(NbtCompound entity, int fromVersion);
}
