package net.minecraft.world.level.chunk.storage;

/**
 * 지역 파일 청크의 압축 종류(Mojang 이름 shim, NMS shim 4번 묶음 — Orebfuscator 캐시). 번호·이름은 바닐라 1.21.1 과 같다. 실제 압축·풀기는 서버의
 * 지역 파일 구현이 하므로 스트림 감싸기({@code wrap})는 두지 않았다. {@link #VERSION_CUSTOM}(127)은 번호만 있고 서버가 쓰지 못한다.
 */
public class RegionFileVersion {

    public static final RegionFileVersion VERSION_GZIP = new RegionFileVersion(1, "gzip");
    public static final RegionFileVersion VERSION_DEFLATE = new RegionFileVersion(2, "deflate");
    public static final RegionFileVersion VERSION_NONE = new RegionFileVersion(3, "none");
    public static final RegionFileVersion VERSION_LZ4 = new RegionFileVersion(4, "lz4");
    public static final RegionFileVersion VERSION_CUSTOM = new RegionFileVersion(127, null);
    public static final RegionFileVersion DEFAULT = VERSION_DEFLATE;

    private final int id;
    private final String optionName;

    private RegionFileVersion(int id, String optionName) {
        this.id = id;
        this.optionName = optionName;
    }

    /** 바닐라: 그 번호의 종류(없으면 null). */
    public static RegionFileVersion fromId(int id) {
        return switch (id) {
            case 1 -> VERSION_GZIP;
            case 2 -> VERSION_DEFLATE;
            case 3 -> VERSION_NONE;
            case 4 -> VERSION_LZ4;
            case 127 -> VERSION_CUSTOM;
            default -> null;
        };
    }

    public static boolean isValidVersion(int id) { return fromId(id) != null; }

    /** 바닐라: server.properties region-file-compression 이 고른 종류. Furnace 만의 종류(zstd)면 바닐라 기본(deflate)으로 답한다. */
    public static RegionFileVersion getSelected() {
        RegionFileVersion v = fromId(org.domi.furnace.api.Furnace.server().internals().chunks().regionFileCompression());
        return v == null || v == VERSION_CUSTOM ? DEFAULT : v;
    }

    /** Paper: {@link #getSelected()} 와 같다. */
    public static RegionFileVersion getCompressionFormat() { return getSelected(); }

    public int getId() { return id; }

    @Override
    public String toString() { return optionName == null ? "custom" : optionName; }
}
