// Paper PaperDatapackManager·PaperDatapack 에 해당 — furnace-api Furnace.dataPacks() 위의 얇은 보기.
package org.domi.furnace.compat.paper;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackManager;
import io.papermc.paper.datapack.DatapackSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.FeatureFlag;
import org.domi.furnace.api.datapack.DataPacks;

public final class CompatDatapacks implements DatapackManager {

    public static final CompatDatapacks INSTANCE = new CompatDatapacks();

    private CompatDatapacks() {}

    private static DataPacks packs() { return Compat.furnace().dataPacks(); }

    @Override
    public void refreshPacks() { packs().refresh(); }

    @Override
    public Datapack getPack(String name) {
        if (name == null) throw new NullPointerException("name");
        return packs().find(name).map(Pack::new).orElse(null);
    }

    @Override
    public Collection<Datapack> getPacks() { return wrap(packs().available()); }

    @Override
    public Collection<Datapack> getEnabledPacks() { return wrap(packs().enabled()); }

    private static Collection<Datapack> wrap(List<DataPacks.Pack> list) {
        List<Datapack> out = new ArrayList<>(list.size());
        for (DataPacks.Pack p : list) out.add(new Pack(p));
        return Collections.unmodifiableList(out);
    }

    /** 사본 하나 — Paper 처럼 만들 때의 켜짐 상태를 지닌다. equals 는 Object 그대로(Paper 와 같다). */
    static final class Pack implements Datapack {

        private final DataPacks.Pack pack;

        Pack(DataPacks.Pack pack) { this.pack = pack; }

        @Override
        public String getName() { return pack.id(); }

        @Override
        public boolean isRequired() { return pack.required(); }

        @Override
        public Compatibility getCompatibility() { return Compatibility.valueOf(pack.compatibility().name()); }

        @Override
        public Set<FeatureFlag> getRequiredFeatures() {
            Set<FeatureFlag> out = new LinkedHashSet<>();
            for (String k : pack.requestedFeatures()) {
                String key = k.indexOf(':') < 0 ? "minecraft:" + k : k;
                for (FeatureFlag f : FLAGS) if (f.getKey().toString().equals(key)) out.add(f);
            }
            return Collections.unmodifiableSet(out);
        }

        @Override
        public boolean isEnabled() { return pack.enabled(); }

        @Override
        public void setEnabled(boolean enabled) { packs().setEnabled(pack.id(), enabled); }

        @Override
        public DatapackSource getSource() {
            return switch (pack.source()) {
                case DEFAULT -> DatapackSource.DEFAULT;
                case BUILT_IN -> DatapackSource.BUILT_IN;
                case FEATURE -> DatapackSource.FEATURE;
                case WORLD -> DatapackSource.WORLD;
                case SERVER -> DatapackSource.SERVER;
                case PLUGIN -> PLUGIN;
            };
        }

        @Override
        public String toString() { return "Datapack{" + pack.id() + "}"; }
    }

    /** Furnace 플러그인 jar 팩(Paper 의 이름 모를 출처처럼 새 이름으로). */
    private static final DatapackSource PLUGIN = new io.papermc.paper.datapack.DatapackSourceImpl("plugin");

    /** Paper PaperFeatureFlagProviderImpl 의 바닐라 플래그 표(폐기된 둘은 없다). */
    private static final List<FeatureFlag> FLAGS = List.of(FeatureFlag.VANILLA, FeatureFlag.BUNDLE, FeatureFlag.TRADE_REBALANCE);
}
