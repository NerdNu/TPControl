package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

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
        
    }
    
    public String getCalculatedMode(String mode, String relationship) {
        return MODE_MAP.get(mode+"."+relationship);
    }
    
    public void initwarps() {
        YamlConfiguration yaml;
        File yamlpath = new File(plugin.getDataFolder(), "warps.yml");
        yaml = YamlConfiguration.loadConfiguration(yamlpath);
        try {
            yaml.save(yamlpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void inithomes() {
        YamlConfiguration yaml;
        File yamlpath = new File(plugin.getDataFolder(), "homes.yml");
        yaml = YamlConfiguration.loadConfiguration(yamlpath);
        try {
            yaml.save(yamlpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
