package org.bukkit.craftbukkit.util;

import org.bukkit.UnsafeValues;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatFormat;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * {@link UnsafeValues} 구현. 클래스 이름은 Paper·CraftBukkit 과 같다({@code Bukkit.getUnsafe().getClass()} 로 서버를 알아보는 코드가 있다).
 * 상태는 없고 부를 때마다 지금 떠 있는 Furnace 서버에 묻는다.
 */
@SuppressWarnings("deprecation")
public final class CraftMagicNumbers implements UnsafeValues {

    public static final CraftMagicNumbers INSTANCE = new CraftMagicNumbers();

    private CraftMagicNumbers() {}

    private static Furnace f() { return Compat.furnace(); }

    /** 1.21.1 의 SharedConstants 값(바닐라 서버 jar version.json 의 world_version). */
    static int dataVersionOf(String minecraftVersion) {
        if ("1.21.1".equals(minecraftVersion)) return 3955;
        throw new UnsupportedFeature("UnsafeValues#getDataVersion", "마인크래프트 " + minecraftVersion + " 의 데이터 버전 표가 없다");
    }

    @Override
    public int getDataVersion() { return dataVersionOf(f().minecraftVersion()); }

    @Override
    public int getProtocolVersion() { return f().packets().protocolVersion(); }

    @Override
    public boolean isSupportedApiVersion(String apiVersion) {
        if (apiVersion == null) return false;
        return !CompatFormat.isNewerThanServerApi(apiVersion);
    }

    @Override
    public void checkSupported(PluginDescriptionFile pdf) throws InvalidPluginException {
        String v = pdf.getAPIVersion();
        if (v != null && CompatFormat.isNewerThanServerApi(v)) throw new InvalidPluginException("Unsupported API version " + v);
    }

    @Override
    public String getMainLevelName() {
        for (var w : f().worlds()) if ("minecraft:overworld".equals(w.dimension())) return w.name();
        throw new IllegalStateException("오버월드가 아직 없다");
    }

    @Override
    public int nextEntityId() { return f().packets().nextEntityId(); }
}
