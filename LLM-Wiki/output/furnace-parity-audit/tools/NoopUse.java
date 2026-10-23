// 오버월드 바이옴 feature 중 Furnace 미구현(Noop) 타입을 쓰는 placed_feature 를 집계하는 도구
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public class NoopUse {
  static final Set<String> HANDLED = Set.of("minecraft:ore","minecraft:scattered_ore","minecraft:spring_feature","minecraft:disk","minecraft:random_patch","minecraft:flower","minecraft:simple_block","minecraft:seagrass","minecraft:kelp","minecraft:freeze_top_layer","minecraft:geode","minecraft:tree","minecraft:random_selector","minecraft:random_boolean_selector","minecraft:simple_random_selector","minecraft:underwater_magma","minecraft:multiface_growth","minecraft:forest_rock","minecraft:vines");
  static Path base = Path.of("C:/Users/gagam/IdeaProjects/Furnace/furnace-server/src/main/resources/worldgen/1.21.1");
  static JsonObject load(String kind, String id) throws Exception {
    String n = id.startsWith("minecraft:") ? id.substring(10) : id;
    return JsonParser.parseString(Files.readString(base.resolve("worldgen").resolve(kind).resolve(n + ".json"))).getAsJsonObject();
  }
  static Set<String> unhandledLeaves;
  // 반환: 이 placed/configured feature가 포함하는 미구현 타입 집합
  static void scanPlaced(JsonElement pe, Set<String> out) throws Exception {
    JsonObject p = pe.isJsonPrimitive() ? load("placed_feature", pe.getAsString()) : pe.getAsJsonObject();
    scanConfigured(p.get("feature"), out);
  }
  static void scanConfigured(JsonElement ce, Set<String> out) throws Exception {
    JsonObject c = ce.isJsonPrimitive() ? load("configured_feature", ce.getAsString()) : ce.getAsJsonObject();
    String type = c.get("type").getAsString();
    if (!HANDLED.contains(type)) { out.add(type); return; }
    JsonObject cfg = c.has("config") ? c.getAsJsonObject("config") : new JsonObject();
    switch (type) {
      case "minecraft:random_selector" -> { for (JsonElement f : cfg.getAsJsonArray("features")) scanPlaced(f.getAsJsonObject().get("feature"), out); scanPlaced(cfg.get("default"), out); }
      case "minecraft:simple_random_selector" -> { for (JsonElement f : cfg.getAsJsonArray("features")) scanPlaced(f, out); }
      case "minecraft:random_boolean_selector" -> { scanPlaced(cfg.get("feature_true"), out); scanPlaced(cfg.get("feature_false"), out); }
      case "minecraft:random_patch" -> scanPlaced(cfg.get("feature"), out);
      default -> {}
    }
  }
  public static void main(String[] a) throws Exception {
    JsonArray params = JsonParser.parseString(Files.readString(base.resolve("biome_parameters.json"))).getAsJsonArray();
    LinkedHashSet<String> biomes = new LinkedHashSet<>();
    for (JsonElement e : params) biomes.add(e.getAsJsonObject().get("biome").getAsString());
    Map<String, Set<String>> placedToBad = new TreeMap<>(); // placed id -> unhandled types
    Map<String, Set<String>> placedBiomes = new TreeMap<>();
    Set<String> allPlaced = new TreeSet<>();
    for (String b : biomes) {
      JsonObject bj = load("biome", b);
      for (JsonElement step : bj.getAsJsonArray("features")) for (JsonElement pid : step.getAsJsonArray()) {
        String id = pid.getAsString(); allPlaced.add(id);
        placedBiomes.computeIfAbsent(id, k -> new TreeSet<>()).add(b.replace("minecraft:",""));
        if (!placedToBad.containsKey(id)) { Set<String> out = new TreeSet<>(); try { scanPlaced(pid, out); } catch (Exception ex) { out.add("ERR:" + ex.getMessage()); } placedToBad.put(id, out); }
      }
    }
    System.out.println("overworld biomes=" + biomes.size() + " distinct placed features=" + allPlaced.size());
    Map<String, Set<String>> byType = new TreeMap<>();
    int bad = 0;
    for (var e : placedToBad.entrySet()) if (!e.getValue().isEmpty()) { bad++; for (String t : e.getValue()) byType.computeIfAbsent(t, k -> new TreeSet<>()).add(e.getKey().replace("minecraft:","")); }
    System.out.println("placed features touching an unimplemented type (root or nested child)=" + bad + "/" + allPlaced.size());
    for (var e : byType.entrySet()) { System.out.println("\n[" + e.getKey() + "] used by " + e.getValue().size() + " placed features:"); for (String p : e.getValue()) System.out.println("   " + p + "   biomes=" + placedBiomes.get("minecraft:" + p).size()); }
  }
}
