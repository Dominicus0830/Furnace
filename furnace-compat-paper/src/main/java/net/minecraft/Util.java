package net.minecraft;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * 바닐라 {@code net.minecraft.Util} 의 순수 보조 함수들(Mojang 이름 shim, 효과·프로필·속성 묶음). FAWE 의 감시 스레드가 {@link #getMillis} 를 부르고,
 * 여러 플러그인이 목록·난수 보조를 쓴다. 난수를 쓰는 함수({@code shuffle}·{@code getRandom}…)는 바닐라와 같은 순서로 {@link RandomSource} 를 부른다.
 * 실행기({@code backgroundExecutor}·{@code ioPool})·OS 판별·IDE 멈춤·파일 안전 교체·데이터 고치기(DFU) 쪽 함수는 두지 않았다(부르면 없는 멤버).
 */
public class Util {

    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    public static final long NANOS_PER_MILLI = 1000000L;
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    public Util() {}

    // ---- 시간

    /** 바닐라: 단조 시계의 밀리초({@code getNanos() / 1_000_000}). 시각이 아니라 간격 재기용이다. */
    public static long getMillis() { return getNanos() / NANOS_PER_MILLI; }

    public static long getNanos() { return System.nanoTime(); }

    public static long getEpochMillis() { return java.time.Instant.now().toEpochMilli(); }

    /** {@code 2026-11-23_21.05.00} 꼴(지금 시각, 시스템 시간대). */
    public static String getFilenameFormattedDateTime() { return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now()); }

    // ---- 이름·오류

    /** 바닐라: {@code type.unregistered_sadface} 또는 {@code type.<이름공간>.<경로, / 를 . 로>}. */
    public static String makeDescriptionId(String type, ResourceLocation id) {
        return id == null ? type + ".unregistered_sadface" : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** 바닐라: 가장 안쪽 원인의 메시지, 없으면 그 예외의 toString. */
    public static String describeError(Throwable t) {
        if (t.getCause() != null) return describeError(t.getCause());
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    // ---- 만들기·합치기

    public static <T> T make(Supplier<T> supplier) { return supplier.get(); }

    public static <T> T make(T value, Consumer<? super T> initializer) {
        initializer.accept(value);
        return value;
    }

    public static <T> Optional<T> ifElse(Optional<T> optional, Consumer<T> present, Runnable absent) {
        if (optional.isPresent()) present.accept(optional.get());
        else absent.run();
        return optional;
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<T>> predicates) {
        List<? extends Predicate<T>> copy = List.copyOf(predicates);
        return switch (copy.size()) {
            case 0 -> t -> true;
            case 1 -> copy.get(0);
            case 2 -> copy.get(0).and(copy.get(1));
            default -> t -> {
                for (Predicate<T> p : copy) if (!p.test(t)) return false;
                return true;
            };
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<T>> predicates) {
        List<? extends Predicate<T>> copy = List.copyOf(predicates);
        return switch (copy.size()) {
            case 0 -> t -> false;
            case 1 -> copy.get(0);
            case 2 -> copy.get(0).or(copy.get(1));
            default -> t -> {
                for (Predicate<T> p : copy) if (p.test(t)) return true;
                return false;
            };
        };
    }

    public static <T> List<T> copyAndAdd(List<T> list, T value) { return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(value).build(); }

    public static <T> List<T> copyAndAdd(T value, List<T> list) { return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(value).addAll(list).build(); }

    /** 같은 키가 있으면 새 값이 이긴다(바닐라 buildKeepingLast). */
    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() { return java.util.stream.Collectors.toCollection(java.util.ArrayList::new); }

    public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() { return java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue); }

    // ---- 목록 찾기

    public static <T> T lastOf(List<T> list) { return list.get(list.size() - 1); }

    /** 바닐라: 목록이 8개 미만이면 선형 찾기, 아니면 해시 표(없으면 -1). */
    public static <T> ToIntFunction<T> createIndexLookup(List<T> list) {
        int size = list.size();
        if (size < LINEAR_LOOKUP_THRESHOLD) return list::indexOf;
        Object2IntMap<T> map = new Object2IntOpenHashMap<>(size);
        map.defaultReturnValue(-1);
        for (int i = 0; i < size; i++) map.put(list.get(i), i);
        return map;
    }

    /** 같은 객체(==)로 찾는다. 바닐라: 8개 미만이면 선형, 아니면 동일성 해시 표(없으면 -1). */
    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> list) {
        int size = list.size();
        if (size < LINEAR_LOOKUP_THRESHOLD) {
            return t -> {
                for (int i = 0; i < size; i++) if (list.get(i) == t) return i;
                return -1;
            };
        }
        Map<T, Integer> map = new IdentityHashMap<>(size);
        for (int i = 0; i < size; i++) map.put(list.get(i), i);
        return t -> map.getOrDefault(t, -1);
    }

    /** 바닐라: 목록에서 value 다음 원소(순환하지 않는다). value 가 null 이거나 없으면 처음 원소로 시작해 마지막을 돌려준다. */
    public static <T> T findNextInIterable(Iterable<T> iterable, T current) {
        var it = iterable.iterator();
        T first = it.next();
        if (current != null) {
            T seen = first;
            while (true) {
                if (seen == current) {
                    if (it.hasNext()) return it.next();
                    break;
                }
                if (it.hasNext()) seen = it.next();
                else break;
            }
        }
        return first;
    }

    /** 대칭인가: width×height 격자(행 우선)가 좌우로 뒤집어도 같은지. */
    public static <T> boolean isSymmetrical(int width, int height, List<T> list) {
        if (width == 1) return true;
        int half = width / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < half; x++) {
                int mirror = width - 1 - x;
                T a = list.get(x + y * width);
                T b = list.get(mirror + y * width);
                if (!a.equals(b)) return false;
            }
        }
        return true;
    }

    /** 코드포인트 offset 만큼 간 UTF-16 위치(서로게이트 쌍을 한 칸으로 센다). 음수 offset 은 뒤로. */
    public static int offsetByCodepoints(String text, int cursor, int offset) {
        int length = text.length();
        if (offset >= 0) {
            for (int i = 0; cursor < length && i < offset; i++) {
                if (Character.isHighSurrogate(text.charAt(cursor++)) && cursor < length && Character.isLowSurrogate(text.charAt(cursor))) cursor++;
            }
        } else {
            for (int i = offset; cursor > 0 && i < 0; i++) {
                cursor--;
                if (Character.isLowSurrogate(text.charAt(cursor)) && cursor > 0 && Character.isHighSurrogate(text.charAt(cursor - 1))) cursor--;
            }
        }
        return cursor;
    }

    // ---- 난수(바닐라와 같은 호출 순서)

    /** 뒤에서부터: {@code for (i = size; i > 1; i--) swap(i - 1, random.nextInt(i))}. */
    public static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size(); i > 1; i--) {
            int j = random.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> copy = new ObjectArrayList<>(array);
        shuffle(copy, random);
        return copy;
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> list = stream.collect(ObjectArrayList.toList());
        shuffle(list, random);
        return list;
    }

    public static <T> T getRandom(List<T> list, RandomSource random) { return list.get(random.nextInt(list.size())); }

    public static <T> T getRandom(T[] array, RandomSource random) { return array[random.nextInt(array.length)]; }

    public static int getRandom(int[] array, RandomSource random) { return array[random.nextInt(array.length)]; }

    public static <T> Optional<T> getRandomSafe(List<T> list, RandomSource random) { return list.isEmpty() ? Optional.empty() : Optional.of(getRandom(list, random)); }
}
