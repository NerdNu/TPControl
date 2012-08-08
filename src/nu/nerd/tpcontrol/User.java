package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class User {
    private TPControl plugin;
    
    public String username;
    private Player player;
    
    //Ask-mode stuff
    String last_applicant;
    long last_applicant_time;
    
    private File prefs_path;
    private YamlConfiguration yaml;
    private boolean dirty = false;
    
    public User (TPControl instance, Player p) {
        //player = instance.getServer().getPlayer(u);
        plugin = instance;
        player = p;
            
        
        prefs_path = new File(plugin.getDataFolder(), "users");
        if(!prefs_path.exists()) {
            prefs_path.mkdir();
        }
        prefs_path = new File(prefs_path, player.getName().toLowerCase() + ".yml");
        
        
        yaml = YamlConfiguration.loadConfiguration(prefs_path);
        yaml.addDefault("mode", plugin.config.DEFAULT_MODE);
    }
    
    public void save() {
        if(!dirty) {
            return;
        }
        
        try {
            yaml.save(prefs_path);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        dirty = false;
    }
    
    //
    // Black/whitelist handling.
    //
    
    private List<String> getList(String name) {
        return yaml.getStringList(name);
    }
    
    private boolean addToList(String name, String username) {
        List<String> l = getList(name);
        username = username.toLowerCase();
        if(l.contains(username)) {
            return false;
        }
        
        l.add(username);
        yaml.set(name,l);
        dirty = true;
        return true;
    }
    
    private boolean delFromList (String name, String username) {
        List<String> l = getList(name);
        username = username.toLowerCase();
        if(!l.contains(username)) {
            return false;
        }
        
        l.remove(username);
        yaml.set(name,l);
        dirty = true;
        return true;
    }
    
    //Friends...
    
    public boolean addFriend(String username) {
        return addToList("friends", username);
    }
    public boolean delFriend(String username) {
        return delFromList("friends", username);
    }
    public List<String> getFriends() {
        return getList("friends");
    }
    
    //Blocked...
    
    public boolean addBlocked(String username) {
        return addToList("blocked", username);
    }
    public boolean delBlocked(String username) {
        return delFromList("blocked", username);
    }
    public List<String> getBlocked() {
        return getList("blocked");
    }
    
    //
    // Player mode (allow|ask|deny)
    //
    
    // Set this player's absolute mode
    
    public void setMode(String mode) {
        yaml.set("mode", mode);
        dirty = true;
    }
    public String getMode() {
        return yaml.getString("mode");
    }
    
    // Get the calculated mode for an applicant teleporting
    // to us - i.e., take into account black/whitelisting.
    public String getCalculatedMode(Player applicant) {
        String mode = getMode();
        String relation = "default";
        String username = applicant.getName().toLowerCase();
        if(getFriends().contains(username)) {
            relation = "friends";
        }
        if(getBlocked().contains(username)) {
            relation = "blocked";
        }
        
        return plugin.config.getCalculatedMode(mode, relation);
    }
    
    //For 'ask' mode:
    public void lodgeRequest(Player applicant) {
        String username = applicant.getName().toLowerCase();
        Date t = new Date();
        if(username.equals(last_applicant) && t.getTime() < last_applicant_time + 1000L*plugin.config.ASK_EXPIRE) {
            plugin.messagePlayer(applicant, "Don't spam /tp!");
            return;
        }
        last_applicant = username;
        last_applicant_time = t.getTime();
        plugin.messagePlayer(player, applicant.getName() + " wants to teleport to you. Please use /tpallow or /tpdeny.");
        
    }
}
