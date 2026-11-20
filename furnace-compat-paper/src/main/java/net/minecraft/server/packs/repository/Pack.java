package net.minecraft.server.packs.repository;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * 팩 하나의 정보(Mojang 이름 shim). 이름표·메타·고르는 규칙을 들고 읽어 주는 값 객체다. 팩 내용을 여는 {@link #open} 은 Furnace 에 팩 저장소가 없어
 * UnsupportedNmsCall 이다. readMetaAndCreate·getChatLink 같은 읽기 도우미는 두지 않았다(호출 위치에서 실패).
 */
public class Pack {

    private final PackLocationInfo location;
    private final ResourcesSupplier resources;
    private final Metadata metadata;
    private final PackSelectionConfig selectionConfig;

    public Pack(PackLocationInfo location, ResourcesSupplier resources, Metadata metadata, PackSelectionConfig selectionConfig) {
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    public PackLocationInfo location() { return this.location; }

    public PackSelectionConfig selectionConfig() { return this.selectionConfig; }

    public String getId() { return this.location.id(); }

    public Component getTitle() { return this.location.title(); }

    public Component getDescription() { return this.metadata.description(); }

    public PackCompatibility getCompatibility() { return this.metadata.compatibility(); }

    public FeatureFlagSet getRequestedFeatures() { return this.metadata.requestedFeatures(); }

    public PackResources open() { return this.resources.openFull(this.location, this.metadata); }

    public boolean isRequired() { return this.selectionConfig.required(); }

    public boolean isFixedPosition() { return this.selectionConfig.fixedPosition(); }

    public Position getDefaultPosition() { return this.selectionConfig.defaultPosition(); }

    public PackSource getPackSource() { return this.location.source(); }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof Pack p && this.location.equals(p.location); }

    @Override
    public int hashCode() { return this.location.hashCode(); }

    /** 팩 메타(바닐라 record). */
    public record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {}

    /** 목록에서 놓는 자리. insert 는 두지 않았다. */
    public enum Position {
        TOP,
        BOTTOM;

        public Position opposite() { return this == TOP ? BOTTOM : TOP; }
    }

    /** 팩 내용을 여는 공급자. */
    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo location);

        PackResources openFull(PackLocationInfo location, Metadata metadata);
    }
}
