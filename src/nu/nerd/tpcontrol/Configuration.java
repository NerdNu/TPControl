package nu.nerd.tpcontrol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Map<String, String> MODE_MAP = new HashMap<String, String>();

    public Map<String, Warp> WARPS = new HashMap<String, Warp>();

    public Configuration(TPControl instance) {
        plugin = instance;
    }
    public void save() {
        plugin.saveConfig();
    }
    public void load() {
        plugin.reloadConfig();
        
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

                String worldName = warpConfig.getString("world", "world");
                double x = warpConfig.getDouble("x", 0);
                double y = warpConfig.getDouble("y", 0);
                double z = warpConfig.getDouble("z", 0);
                float yaw = (float) warpConfig.getDouble("yaw", 0);
                float pitch = (float) warpConfig.getDouble("pitch", 0);
                Integer warmup = warpConfig.getInt("warmup.length");
                Boolean warmupCancelOnMove = warpConfig.getBoolean("warmup.cancel-on-move");
                Boolean warmupCancelOnDamage = warpConfig.getBoolean("warmup.cancel-on-damage");
                Integer cooldown = warpConfig.getInt("cooldown.length");
                List<String> shortcuts = warpConfig.getStringList("shortcuts");

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

                    if (shortcuts != null) {
                        warp.setShortcuts(shortcuts);
                    }

                    WARPS.put(warpName, warp);
                }
            }
        }
    }

    public String getCalculatedMode(String mode, String relationship) {
        return MODE_MAP.get(mode+"."+relationship);
    }
}
