package nu.nerd.tpcontrol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class Configuration {
    public static String[] relationships = {"blocked", "default", "friends"};
    public static String[] modes = {"deny", "ask", "allow"};

    private final TPControl plugin;

    public String MIN_GROUP;
    public String DEFAULT_MODE;
    public int ASK_EXPIRE;
    public int SAVE_INTERVAL;
    public ArrayList<String> GROUPS;
    public Map<String, String> MODE_MAP;

    public Map<String, Warp> WARPS;

    public Configuration(TPControl instance) {
        plugin = instance;
    }
    public void save() {
        plugin.saveConfig();
    }
    public void load() {
        plugin.reloadConfig();

        MODE_MAP = new HashMap<>();
        WARPS = new TreeMap<>();

        String key;
        for(String m : modes) {
            for(String r : relationships) {
                key = m+"."+r;
                String val = plugin.getConfig().getString("permissions." + key);
                if(val == null) {
                    MODE_MAP.put(key, m);
                } else {
                    MODE_MAP.put(key, val);
                }
            }
        }

        DEFAULT_MODE = plugin.getConfig().getString("default-mode");
        MIN_GROUP = plugin.getConfig().getString("min-group");
        ASK_EXPIRE = plugin.getConfig().getInt("ask-expire");
        SAVE_INTERVAL = plugin.getConfig().getInt("save-interval");
        GROUPS = (ArrayList<String>)plugin.getConfig().getStringList("groups");

        ConfigurationSection warpsConfig = plugin.getConfig().getConfigurationSection("warps");
        if (warpsConfig != null) {
            for (String warpName : warpsConfig.getKeys(false)) {
                ConfigurationSection warpConfig = plugin.getConfig().getConfigurationSection("warps." + warpName);

                ConfigurationSection locationConfig = plugin.getConfig().getConfigurationSection("warps." + warpName + ".location");
                if (locationConfig != null) {
                    String worldName = locationConfig.getString("world", "world");
                    double x = locationConfig.getDouble("x", 0);
                    double y = locationConfig.getDouble("y", 0);
                    double z = locationConfig.getDouble("z", 0);
                    float yaw = (float) locationConfig.getDouble("yaw", 0);
                    float pitch = (float) locationConfig.getDouble("pitch", 0);

                    Integer warmup = warpConfig.getInt("warmup.length");
                    Boolean warmupCancelOnMove = warpConfig.getBoolean("warmup.cancel-on-move");
                    Boolean warmupCancelOnDamage = warpConfig.getBoolean("warmup.cancel-on-damage");
                    Integer cooldown = warpConfig.getInt("cooldown.length");
                    Integer balanceMin = null;
                    if (warpConfig.contains("economy.balance.min"))
                        balanceMin = warpConfig.getInt("economy.balance.min");
                    Integer balanceMax = null;
                    if (warpConfig.contains("economy.balance.max"))
                        balanceMax = warpConfig.getInt("economy.balance.max");
                    List<String> shortcuts = warpConfig.getStringList("shortcuts");
                    Boolean allowBack = warpConfig.getBoolean("allow-back");

                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        Location location = new Location(world, x, y, z, yaw, pitch);
                        Warp warp = new Warp(warpName, location);

                        if (warmup != null) {
                            warp.setWarmup(warmup);
                        }

                        if (warmupCancelOnMove != null) {
                            warp.setWarmupCancelOnMove(warmupCancelOnMove);
                        }

                        if (warmupCancelOnDamage != null) {
                            warp.setWarmupCancelOnDamage(warmupCancelOnDamage);
                        }

                        if (cooldown != null) {
                            warp.setCooldown(cooldown);
                        }

                        warp.setBalanceMin(balanceMin);
                        warp.setBalanceMax(balanceMax);

                        if (shortcuts != null) {
                            warp.setShortcuts(shortcuts);
                        }

                        if (allowBack != null) {
                            warp.setAllowBack(allowBack);
                        }

                        WARPS.put(warpName.toLowerCase(), warp);
                        for (String shortcut : shortcuts) {
                            ShortcutCommand shortcutCommand = new ShortcutCommand(shortcut, this.plugin, warp);
                            this.plugin.registerCommand(new String[]{shortcut});
                            this.plugin.getCommand(shortcut).setExecutor(shortcutCommand);
                        }
                    }
                }
            }
        }
    }

    public String getCalculatedMode(String mode, String relationship) {
        return MODE_MAP.get(mode+"."+relationship);
    }
}
