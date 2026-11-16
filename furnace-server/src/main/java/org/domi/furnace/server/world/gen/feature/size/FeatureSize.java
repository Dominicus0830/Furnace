package org.domi.furnace.server.world.gen.feature.size;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize")
public interface FeatureSize {
    int getSizeAtHeight(int totalHeight, int currentHeight);
    int minClippedHeight();
}
