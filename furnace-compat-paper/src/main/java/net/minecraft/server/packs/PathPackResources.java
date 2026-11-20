package net.minecraft.server.packs;

import java.nio.file.Path;
import net.minecraft.server.packs.repository.Pack;

/** 폴더 팩(Mojang 이름 shim). 폴더를 읽는 팩 내용은 없다 — 공급자만 만들 수 있고 열면 UnsupportedNmsCall 이다. */
public final class PathPackResources {

    private PathPackResources() {}

    public static class PathResourcesSupplier implements Pack.ResourcesSupplier {
        private final Path content;

        public PathResourcesSupplier(Path content) { this.content = content; }

        /** 호환 모듈 전용. */
        public Path furnacePath() { return content; }

        @Override
        public PackResources openPrimary(PackLocationInfo location) { throw opened(); }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) { throw opened(); }

        private static org.domi.furnace.compat.nms.UnsupportedNmsCall opened() {
            return new org.domi.furnace.compat.nms.UnsupportedNmsCall("PathPackResources", "Furnace has no pack repository, so a pack folder cannot be opened"
                    + " as NMS pack resources - use the Paper datapack API");
        }
    }
}
