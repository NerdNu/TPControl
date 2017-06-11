package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class User {
    private TPControl plugin;

    public String username;
    public UUID uuid;

    //Ask-mode stuff
    String last_applicant;
    long last_applicant_time;

    private File prefs_path;
    private YamlConfiguration yaml;
    private boolean dirty = false;
    private Location lastLocation;
    
    /** Valid characters for homes */
    private static Pattern validChars = Pattern.compile("^[A-Za-z_]+[A-Za-z_0-9]*");
    
    private final String HOMES = "homes.";
    private final String HOMES_NO_DOT = "homes";
    private final String LOCATION = ".location";
    private final String VISIBILITY = ".visibility";

    public User (TPControl instance, Player p) {
        this(instance, p.getUniqueId(), p.getName());
    }
    
    public User(TPControl instance, UUID uuid, String name) {
        plugin = instance;
        username = name;
        this.uuid = uuid;

        prefs_path = new File(plugin.getDataFolder(), "users");
        if(!prefs_path.exists()) {
            prefs_path.mkdir();
        }
        prefs_path = new File(prefs_path, uuid.toString() + ".yml");

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

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
	private boolean delFromList(String name, String username) {
        List<String> l = getList(name);

        String uuid = null;

        // 'username' could be a UUID
        if (l.contains(username)) {
        	uuid = username;
        } else {
	        Player perp = plugin.getPlayer(username);
	        if (perp != null)
	            uuid = perp.getUniqueId().toString();
	        else
	            uuid = Bukkit.getOfflinePlayer(username).getUniqueId().toString();
	        
	        if(!l.contains(uuid)) {
	            return false;
	        }
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
    
    /**
     * Set a users home.
     * 
     * @param name
     * @param loc
     * @param visibility
     */
    public void setHome(String name, Location loc, HomeVisibility visibility) {
        if(!validChars.matcher(name).matches()) {
            throw new FormattedUserException(ChatColor.RED + "ERROR: Invalid home name");
        }
        
        // Set the location
        yaml.set(HOMES + name + LOCATION, loc);
        if(visibility != null) {
            yaml.set(HOMES + name + VISIBILITY, visibility.toString());
        }
        dirty = true;
        
    }

    /**
     * Get the location of a home from this player.
     * 
     * @param name
     * @return
     */
    public Location getHome(String name) {
        Object o = yaml.get(HOMES + name + LOCATION);
        if(o == null || !(o instanceof Location)) {
            throw new FormattedUserException(ChatColor.RED + "Home " + name + " not found.");
        }
        return (Location)o;
    }
    
    /**
     * Get the visibility of a given home
     * 
     * @param name
     * @return
     */
    public HomeVisibility getHomeVisibility(String name) {
        getHome(name); // Ensure the home exists.
        String vis = yaml.getString(HOMES + name + VISIBILITY, HomeVisibility.UNLISTED.toString());
        return HomeVisibility.valueOf(vis);
    }
    
    /**
     * Delete a home location
     * @param name
     */
    public void deleteHome(String name) {
        // Call getHome to see if the home exists. It will throw exceptions
        // if there is not home.
        getHome(name);
        yaml.set(HOMES + name, null);
    }
    
    /**
     * Get a set of names of all the homes.
     * 
     * @return The homes.
     */
    public Set<String> getHomeNames() {
        ConfigurationSection s = yaml.getConfigurationSection(HOMES_NO_DOT);
        if (s == null) {
            return new HashSet<String>();
        } else {
            return s.getKeys(false);
        }
    }
}

