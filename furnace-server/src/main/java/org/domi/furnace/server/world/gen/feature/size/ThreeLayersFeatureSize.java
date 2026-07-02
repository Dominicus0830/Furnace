package org.domi.furnace.server.world.gen.feature.size;

public record ThreeLayersFeatureSize(int limit, int upperLimit, int lowerSize, int middleSize, int upperSize, int minClippedHeight)
        implements FeatureSize {
    public ThreeLayersFeatureSize(int limit, int upperLimit, int lowerSize, int middleSize, int upperSize) {
        this(limit, upperLimit, lowerSize, middleSize, upperSize, -1);
    }

    @Override
    public int getSizeAtHeight(int totalHeight, int currentHeight) {
        if (currentHeight < limit) return lowerSize;
        return currentHeight >= totalHeight - upperLimit ? upperSize : middleSize;
    }
}
