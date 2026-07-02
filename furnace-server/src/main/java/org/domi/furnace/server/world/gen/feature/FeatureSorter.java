// biome별 feature 목록을 vanilla와 동일한 위상정렬로 step별 전역 순서/index로 변환한다.
package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FeatureSorter {

    private final List<StepFeatureData> featuresPerStep;

    private FeatureSorter(List<StepFeatureData> featuresPerStep) {
        this.featuresPerStep = List.copyOf(featuresPerStep);
    }

    public List<StepFeatureData> featuresPerStep() {
        return featuresPerStep;
    }

    /**
     * vanilla {@code net.minecraft.world.level.biome.FeatureSorter.buildFeaturesPerStep} 이식.
     * biome 목록 순서대로 feature를 훑어 (step, firstSeenIndex) 정렬키를 부여하고,
     * 같은 biome 내 인접 feature(k → k+1)를 ordering 제약 간선으로 두어 DFS 위상정렬한다.
     * 최종 index = step별 정렬 목록 내 위치 → vanilla setFeatureSeed index와 일치.
     */
    public static FeatureSorter build(BiomeFeatureData data) {
        record FeatureData(int featureIndex, int step, PlacedFeature feature) {}

        Comparator<FeatureData> cmp = Comparator
            .comparingInt(FeatureData::step)
            .thenComparingInt(FeatureData::featureIndex);

        IdentityHashMap<PlacedFeature, Integer> firstIndex = new IdentityHashMap<>();
        int[] counter = {0};
        Map<FeatureData, Set<FeatureData>> edges = new TreeMap<>(cmp);

        for (String biome : data.orderedBiomes()) {
            List<FeatureData> seq = new ArrayList<>();
            for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
                for (PlacedFeature pf : data.featuresForStep(biome, step)) {
                    int fi = firstIndex.computeIfAbsent(pf, k -> counter[0]++);
                    seq.add(new FeatureData(fi, step, pf));
                }
            }
            for (int k = 0; k < seq.size(); k++) {
                Set<FeatureData> set = edges.computeIfAbsent(seq.get(k), x -> new TreeSet<>(cmp));
                if (k < seq.size() - 1) {
                    FeatureData next = seq.get(k + 1);
                    if (!seq.get(k).equals(next)) set.add(next);
                }
            }
        }

        Set<FeatureData> visited = new TreeSet<>(cmp);
        Set<FeatureData> inProgress = new TreeSet<>(cmp);
        List<FeatureData> postOrder = new ArrayList<>();
        for (FeatureData node : edges.keySet()) {
            if (dfs(node, edges, visited, inProgress, postOrder)) {
                throw new IllegalStateException("Feature order cycle found");
            }
        }
        Collections.reverse(postOrder);

        ArrayList<StepFeatureData> result = new ArrayList<>(BiomeFeatureData.STEP_COUNT);
        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            ArrayList<PlacedFeature> ordered = new ArrayList<>();
            for (FeatureData fd : postOrder) {
                if (fd.step() == step) {
                    ordered.add(fd.feature());
                }
            }
            IdentityHashMap<PlacedFeature, Integer> index = new IdentityHashMap<>();
            for (int i = 0; i < ordered.size(); i++) {
                index.putIfAbsent(ordered.get(i), i);
            }
            result.add(new StepFeatureData(ordered, index));
        }
        return new FeatureSorter(result);
    }

    /** post-order DFS. 사이클이면 true. */
    private static <T> boolean dfs(T node, Map<T, Set<T>> edges,
            Set<T> visited, Set<T> inProgress, List<T> out) {
        if (visited.contains(node)) return false;
        if (!inProgress.add(node)) return true;
        for (T next : edges.getOrDefault(node, Set.of())) {
            if (dfs(next, edges, visited, inProgress, out)) return true;
        }
        inProgress.remove(node);
        visited.add(node);
        out.add(node);
        return false;
    }

    public record StepFeatureData(List<PlacedFeature> features, IdentityHashMap<PlacedFeature, Integer> indexMap) {
        public StepFeatureData {
            features = List.copyOf(features);
            indexMap = new IdentityHashMap<>(indexMap);
        }

        public int indexOf(PlacedFeature feature) {
            return indexMap.getOrDefault(feature, -1);
        }
    }
}
