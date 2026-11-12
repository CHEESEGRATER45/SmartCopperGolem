package dev.scg;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps vanilla items to a category name. Built once from categories.yml.
 * An item belongs to at most one category (first one listed wins).
 */
public final class CategoryRegistry {

    private final Map<Item, String> categoryByItem = new IdentityHashMap<>();
    private final Map<String, Integer> sizes = new LinkedHashMap<>();
    private final List<String> unresolved = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();

    private CategoryRegistry() {
    }

    public static CategoryRegistry build(Map<String, ? extends Collection<String>> raw) {
        CategoryRegistry reg = new CategoryRegistry();
        List<Identifier> all = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
        for (Map.Entry<String, ? extends Collection<String>> category : raw.entrySet()) {
            String name = category.getKey();
            int count = 0;
            for (String entry : category.getValue()) {
                List<Item> items = resolve(entry, all);
                if (items.isEmpty()) {
                    reg.unresolved.add(name + ": " + entry);
                    continue;
                }
                for (Item item : items) {
                    String existing = reg.categoryByItem.get(item);
                    if (existing == null) {
                        reg.categoryByItem.put(item, name);
                        count++;
                    } else if (!existing.equals(name)) {
                        reg.conflicts.add(idOf(item) + " is in '" + existing + "' and '" + name + "' (kept '" + existing + "')");
                    }
                }
            }
            reg.sizes.put(name, count);
        }
        return reg;
    }

    /** Category name of the item, or null if the item is in no category. */
    public String categoryOf(Item item) {
        return categoryByItem.get(item);
    }

    public int categoryCount() {
        return sizes.size();
    }

    public int itemCount() {
        return categoryByItem.size();
    }

    public Map<String, Integer> sizes() {
        return sizes;
    }

    /** Entries from categories.yml that matched no item (typos, items that don't exist in this version). */
    public List<String> unresolved() {
        return unresolved;
    }

    public List<String> conflicts() {
        return conflicts;
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static List<Item> resolve(String raw, List<Identifier> all) {
        List<Item> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return out;
        }
        if (s.indexOf('*') >= 0 || s.indexOf('?') >= 0) {
            String path = s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
            Pattern p = glob(path);
            for (Identifier id : all) {
                if ("minecraft".equals(id.getNamespace()) && p.matcher(id.getPath()).matches()) {
                    add(out, BuiltInRegistries.ITEM.getValue(id));
                }
            }
        } else {
            Identifier id = Identifier.tryParse(s.indexOf(':') >= 0 ? s : "minecraft:" + s);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                add(out, BuiltInRegistries.ITEM.getValue(id));
            }
        }
        return out;
    }

    private static void add(List<Item> out, Item item) {
        if (item != Items.AIR) {
            out.add(item);
        }
    }

    private static Pattern glob(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }
}
