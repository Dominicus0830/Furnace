package io.papermc.paper.configuration.type;

/** Paper 엑스레이 막기 방식(Paper 1.21.1 jar 의 번호·설명 그대로). Furnace 는 엑스레이 막기가 없어 값으로만 쓴다. 설정 직렬화기(configurate)는 없다. */
public enum EngineMode {
    HIDE(1, "hide ores"), OBFUSCATE(2, "obfuscate"), OBFUSCATE_LAYER(3, "obfuscate layer");

    private final int id;
    private final String description;

    EngineMode(int id, String description) {
        this.id = id;
        this.description = description;
    }

    /** 번호로 찾기(없으면 null). */
    public static EngineMode valueOf(int id) {
        for (EngineMode m : values()) if (m.id == id) return m;
        return null;
    }

    public int getId() { return id; }

    public String getDescription() { return description; }
}
