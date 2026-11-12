package dev.scg;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Makes copper golems deliver items by category.
 *
 * The golems keep their vanilla brain, pathfinding and animations. On load, each golem's vanilla
 * item-transport behaviour is replaced by {@link CategoryTransportBehavior}, which differs from vanilla in
 * exactly one place: how it decides whether a chest accepts the item the golem is carrying.
 */
public final class SmartCopperGolemPlugin extends JavaPlugin implements Listener, TabExecutor {

    private volatile CategoryMatcher matcher;
    private int sweepTaskId = -1;

    @Override
    public void onEnable() {
        try {
            CategoryTransportBehavior.verify();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "This server's copper golem code doesn't match what SmartCopperGolem "
                    + "was built for, so it will stay off and golems will behave like vanilla. "
                    + "Please report this with your server version.", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        if (!new File(getDataFolder(), "categories.yml").exists()) {
            saveResource("categories.yml", false);
        }
        reloadSettings();

        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("scg");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        // Golems that are already loaded.
        int patched = sweep();
        getLogger().info("Category matching active (" + matcher.mode() + "). Patched " + patched + " loaded golem(s).");
        restartSweep();
    }

    @Override
    public void onDisable() {
        if (matcher == null) {
            return; // never finished enabling, nothing was patched
        }
        if (sweepTaskId != -1) {
            getServer().getScheduler().cancelTask(sweepTaskId);
        }
        // Put vanilla behaviour back so nothing of ours lingers in memory.
        for (World world : getServer().getWorlds()) {
            for (org.bukkit.entity.CopperGolem golem : world.getEntitiesByClass(org.bukkit.entity.CopperGolem.class)) {
                if (((CraftEntity) golem).getHandle() instanceof CopperGolem handle) {
                    GolemPatcher.unpatch(handle);
                }
            }
        }
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        patch(event.getEntity());
    }

    private boolean patch(Entity entity) {
        if (!(entity instanceof org.bukkit.entity.CopperGolem)) {
            return false;
        }
        if (((CraftEntity) entity).getHandle() instanceof CopperGolem golem) {
            return GolemPatcher.patch(golem, this::currentMatcher);
        }
        return false;
    }

    private CategoryMatcher currentMatcher() {
        return matcher;
    }

    /** Patches every loaded golem that isn't patched yet. */
    private int sweep() {
        int count = 0;
        for (World world : getServer().getWorlds()) {
            for (org.bukkit.entity.CopperGolem golem : world.getEntitiesByClass(org.bukkit.entity.CopperGolem.class)) {
                if (patch(golem)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void restartSweep() {
        if (sweepTaskId != -1) {
            getServer().getScheduler().cancelTask(sweepTaskId);
            sweepTaskId = -1;
        }
        long interval = getConfig().getLong("patch.sweep-interval-ticks", 1200L);
        if (interval > 0) {
            // Safety net: also catches golems that were mid-delivery when they loaded.
            sweepTaskId = getServer().getScheduler().runTaskTimer(this, this::sweep, interval, interval).getTaskId();
        }
    }

    private void reloadSettings() {
        reloadConfig();
        String modeName = getConfig().getString("transport.match-mode", "CATEGORY").trim().toUpperCase(Locale.ROOT);
        CategoryMatcher.Mode mode;
        try {
            mode = CategoryMatcher.Mode.valueOf(modeName);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Unknown transport.match-mode '" + modeName + "', using CATEGORY (valid: EXACT, ITEM, CATEGORY).");
            mode = CategoryMatcher.Mode.CATEGORY;
        }
        boolean allowEmpty = getConfig().getBoolean("transport.allow-empty-containers", true);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "categories.yml"));
        Map<String, List<String>> raw = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("categories");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                raw.put(name, section.getStringList(name));
            }
        }
        CategoryRegistry registry = CategoryRegistry.build(raw);
        this.matcher = new CategoryMatcher(mode, allowEmpty, registry);

        for (String line : registry.unresolved()) {
            getLogger().warning("categories.yml: no such item -> " + line);
        }
        for (String line : registry.conflicts()) {
            getLogger().warning("categories.yml: " + line);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "stats" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                reloadSettings();
                restartSweep();
                sweep();
                sender.sendMessage("SmartCopperGolem reloaded: " + describe());
            }
            case "stats" -> {
                int total = 0;
                int patched = 0;
                for (World world : getServer().getWorlds()) {
                    for (org.bukkit.entity.CopperGolem golem : world.getEntitiesByClass(org.bukkit.entity.CopperGolem.class)) {
                        total++;
                        if (((CraftEntity) golem).getHandle() instanceof CopperGolem handle && GolemPatcher.isPatched(handle)) {
                            patched++;
                        }
                    }
                }
                sender.sendMessage(describe());
                sender.sendMessage("Loaded copper golems: " + total + " (" + patched + " using category matching)");
                CategoryRegistry registry = matcher.registry();
                if (!registry.unresolved().isEmpty()) {
                    sender.sendMessage(registry.unresolved().size() + " categories.yml entr"
                            + (registry.unresolved().size() == 1 ? "y was" : "ies were") + " skipped (no such item). See the console.");
                }
            }
            default -> {
                sender.sendMessage("Usage: /" + label + " <reload|stats>");
                return false;
            }
        }
        return true;
    }

    private String describe() {
        CategoryMatcher m = matcher;
        return "Mode " + m.mode() + ", " + m.registry().categoryCount() + " categories covering "
                + m.registry().itemCount() + " items, empty containers "
                + (m.allowEmptyContainers() ? "accepted" : "refused");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "stats")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
