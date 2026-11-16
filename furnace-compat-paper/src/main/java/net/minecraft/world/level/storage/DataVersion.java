package net.minecraft.world.level.storage;

/** 데이터 판 번호와 계열(Mojang 이름 shim, N2b-6) — 바닐라 그대로. 1.21.1 은 3955, 계열 "main". */
public class DataVersion {

    public static String MAIN_SERIES = "main";

    private final int version;
    private final String series;

    public DataVersion(int version) { this(version, MAIN_SERIES); }

    public DataVersion(int version, String series) {
        this.version = version;
        this.series = series;
    }

    public boolean isSideSeries() { return !series.equals(MAIN_SERIES); }

    public String getSeries() { return series; }

    public int getVersion() { return version; }

    public boolean isCompatible(DataVersion other) { return getSeries().equals(other.getSeries()); }
}
