package org.domi.furnace.server.world.gen.feature.size;

public interface FeatureSize {
    int getSizeAtHeight(int totalHeight, int currentHeight);
    int minClippedHeight();
}
