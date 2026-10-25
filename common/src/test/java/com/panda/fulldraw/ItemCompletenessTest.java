package com.panda.fulldraw;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every item the mod registers has an English name, a client item whose models and textures exist, and a way to get it
 * in survival: a recipe's result, a loot table entry or a trade. Spawn eggs come from the creative menu.
 */
class ItemCompletenessTest {
    private static final Path JAVA = Path.of("src", "main", "java", "com", "panda", "fulldraw", "registry");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "fulldraw");
    private static final Path DATA = Path.of("src", "main", "resources", "data");

    /** The ids in ModItems: each {@code registerItem("id"} and each block item, named after its block's constant. */
    private static Set<String> items() throws IOException {
        Set<String> ids = new TreeSet<>();
        String src = Files.readString(JAVA.resolve("ModItems.java"));
        Matcher m = Pattern.compile("registerItem\\(\"([a-z_]+)\"").matcher(src);
        while (m.find()) ids.add(m.group(1));
        Matcher b = Pattern.compile("registerSimpleBlockItem\\(ModBlocks\\.([A-Z_]+)\\)").matcher(src);
        while (b.find()) ids.add(b.group(1).toLowerCase(java.util.Locale.ROOT));
        return ids;
    }

    private static String allJson(String folder) throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> s = Files.walk(DATA)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".json") && p.toString().replace('\\', '/').contains("/" + folder + "/")).toList())
                all.append(Files.readString(p)).append('\n');
        }
        return all.toString();
    }

    @Test
    void everyItemHasANameAModelAndASource() throws IOException {
        Set<String> ids = items();
        assertTrue(ids.size() >= 20, "expected the mod's items, found " + ids);
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String recipes = allJson("recipe"), loot = allJson("loot_table"), trades = allJson("villager_trade");
        List<String> problems = new ArrayList<>();
        for (String id : ids) {
            String key = "fulldraw:" + id;
            if (!lang.contains("\"item.fulldraw." + id + "\"") && !lang.contains("\"block.fulldraw." + id + "\"")) problems.add(id + ": no English name");

            Path client = ASSETS.resolve("items/" + id + ".json");
            if (!Files.exists(client)) {
                problems.add(id + ": no client item");
            } else {
                Matcher models = Pattern.compile("\"model\"\\s*:\\s*\"fulldraw:([a-z_/0-9]+)\"").matcher(Files.readString(client));
                while (models.find()) {
                    Path model = ASSETS.resolve("models/" + models.group(1) + ".json");
                    if (!Files.exists(model)) {
                        problems.add(id + ": missing model " + models.group(1));
                        continue;
                    }
                    Matcher textures = Pattern.compile("\"fulldraw:((?:item|block)/[a-z_/0-9]+)\"").matcher(Files.readString(model));
                    while (textures.find())
                        if (!Files.exists(ASSETS.resolve("textures/" + textures.group(1) + ".png")) && !Files.exists(ASSETS.resolve("models/" + textures.group(1) + ".json")))
                            problems.add(id + ": missing texture " + textures.group(1));
                }
            }

            if (id.endsWith("_spawn_egg")) continue;
            boolean crafted = Pattern.compile("\"result\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"" + key + "\"").matcher(recipes).find();
            boolean dropped = loot.contains("\"name\": \"" + key + "\"") || loot.contains("\"name\":\"" + key + "\"");
            boolean sold = trades.contains("\"" + key + "\"");
            if (!crafted && !dropped && !sold) problems.add(id + ": no recipe, loot or trade gives it");
        }
        assertTrue(problems.isEmpty(), "items incomplete: " + problems);
    }
}
