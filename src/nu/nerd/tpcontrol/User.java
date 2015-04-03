package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class User {
    private TPControl plugin;

    public String username;

    //Ask-mode stuff
    String last_applicant;
    long last_applicant_time;

    private File prefs_path;
    private YamlConfiguration yaml;
    private boolean dirty = false;
    private Location lastLocation;

    public User (TPControl instance, Player p) {
        //player = instance.getServer().getPlayer(u);
        plugin = instance;
        username = p.getName();

        prefs_path = new File(plugin.getDataFolder(), "users");
        if(!prefs_path.exists()) {
            prefs_path.mkdir();
        }
        prefs_path = new File(prefs_path, p.getUniqueId().toString() + ".yml");

        yaml = YamlConfiguration.loadConfiguration(prefs_path);
        yaml.addDefault("mode", plugin.config.DEFAULT_MODE);
    }

    public void save() {
        if(!dirty) {
            return;
        }

//        plugin.getLogger().info("Saving user: " + username);

        try {
            yaml.save(prefs_path);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        dirty = false;
    }

    public String getUsername() {
        return username;
    }

    //
    // Last Location handling.
    //
    public void setLastLocation(Location last) {
        yaml.set("last_location", last);
        this.lastLocation = last;
        dirty = true;
        this.save();
    }

    public Location getLastLocation() {
        if (this.lastLocation == null)
            this.lastLocation = (Location) yaml.get("last_location");

        return this.lastLocation;
    }

    //
    // Black/whitelist handling.
    //

    private List<String> getList(String name) {
        return yaml.getStringList(name);
    }

    private boolean addToList(String name, String username) {
        List<String> l = getList(name);

        String uuid = null;
        Player perp = plugin.getPlayer(username);
        if (perp != null)
            uuid = perp.getUniqueId().toString();
        else
            uuid = Bukkit.getOfflinePlayer(username).getUniqueId().toString();

        if(l.contains(uuid)) {
            return false;
        }

        l.add(uuid);
        yaml.set(name,l);
        dirty = true;
        return true;
    }

    private boolean delFromList(String name, String username) {
        List<String> l = getList(name);

        String uuid = null;
        Player perp = plugin.getPlayer(username);
        if (perp != null)
            uuid = perp.getUniqueId().toString();
        else
            uuid = Bukkit.getOfflinePlayer(username).getUniqueId().toString();

        if(!l.contains(uuid)) {
            return false;
        }

        l.remove(uuid);
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
        String uuid = applicant.getUniqueId().toString();
        if(getFriends().contains(uuid)) {
            relation = "friends";
        }
        if(getBlocked().contains(uuid)) {
            relation = "blocked";
        }

        return plugin.config.getCalculatedMode(mode, relation);
    }

    //For 'ask' mode:
    public void lodgeRequest(Player applicant) {
        String applicant_username = ChatColor.stripColor(applicant.getName()).toLowerCase();
        Date t = new Date();
        if(applicant_username.equals(last_applicant) && t.getTime() < last_applicant_time + 1000L*plugin.config.ASK_EXPIRE) {
            plugin.messagePlayer(applicant, "Don't spam /tp!");
            return;
        }
        last_applicant = applicant_username;
        last_applicant_time = t.getTime();
        Player player = plugin.getPlayer(this.username);
        if (player != null)
            plugin.messagePlayer(player, applicant.getName() + " wants to teleport to you. Please use /tpallow or /tpdeny.");
    }
}
