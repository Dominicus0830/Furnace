package org.domi.furnace.server.world.gen.feature.size;

public record TwoLayersFeatureSize(int limit, int lowerSize, int upperSize, int minClippedHeight) implements FeatureSize {
    public TwoLayersFeatureSize(int limit, int lowerSize, int upperSize) {
        this(limit, lowerSize, upperSize, -1);
    }

    @Override
    public int getSizeAtHeight(int totalHeight, int currentHeight) {
        return currentHeight < limit ? lowerSize : upperSize;
    }
}
