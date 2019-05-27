package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class User {
    private final TPControl plugin;

    public String username;
    public UUID uuid;

    // Ask-mode stuff
    String last_applicant;
    long last_applicant_time;

    private File prefs_path;
    private final YamlConfiguration yaml;
    private boolean dirty = false;
    private Location lastLocation;

    /** Valid characters for homes */
    private static Pattern validChars = Pattern.compile("^[A-Za-z_]+[A-Za-z_0-9]*");

    private final String HOMES = "homes";

    private final Map<String, Home> homes = new HashMap<String, Home>();

    public User(TPControl instance, Player p) {
        this(instance, p.getUniqueId(), p.getName());
    }

    public User(TPControl instance, UUID uuid, String name) {
        plugin = instance;
        username = name;
        this.uuid = uuid;

        prefs_path = new File(plugin.getDataFolder(), "users");
        if (!prefs_path.exists()) {
            prefs_path.mkdir();
        }
        prefs_path = new File(prefs_path, uuid.toString() + ".yml");

        yaml = YamlConfiguration.loadConfiguration(prefs_path);
        yaml.addDefault("mode", plugin.config.DEFAULT_MODE);

        // Populate the homes table. We keep it in deserialized form
        // for quick access.
        ConfigurationSection s = yaml.getConfigurationSection(HOMES);
        if (s != null) {
            for (String homeName : s.getKeys(false)) {
                homes.put(homeName.toLowerCase(),
                          new Home(homeName, s));
            }
        }
        yaml.set(HOMES, null); // Clear this to save memory.
                               // We put it back on flush.
    }

    public void save() {
        if (!dirty) {
            return;
        }

        // Serialize all the home data
        ConfigurationSection s = yaml.createSection(HOMES);
        homes.values().forEach(home -> home.toYaml(s));

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

    private boolean addToList(String name, String username) {
        List<String> l = getList(name);

        String uuid = plugin.getUUIDCache().getUUID(username).toString();

        if (l.contains(uuid)) {
            return false;
        }

        l.add(uuid);
        yaml.set(name, l);
        dirty = true;
        return true;
    }

    private boolean delFromList(String name, String username) {
        List<String> l = getList(name);

        String uuid = null;

        // 'username' could be a UUID
        if (l.contains(username)) {
            uuid = username;
        } else {
            uuid = plugin.getUUIDCache().getUUID(username).toString();
            if (!l.contains(uuid)) {
                return false;
            }
        }

        l.remove(uuid);
        yaml.set(name, l);
        dirty = true;
        return true;
    }

    // Friends...

    public boolean addFriend(String username) {
        return addToList("friends", username);
    }

    public boolean delFriend(String username) {
        return delFromList("friends", username);
    }

    public List<String> getFriends() {
        return getList("friends");
    }

    // Blocked...

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
        if (getFriends().contains(uuid)) {
            relation = "friends";
        }
        if (getBlocked().contains(uuid)) {
            relation = "blocked";
        }

        return plugin.config.getCalculatedMode(mode, relation);
    }

    // For 'ask' mode:
    public void lodgeRequest(Player applicant) {
        String applicant_username = ChatColor.stripColor(applicant.getName()).toLowerCase();
        Date t = new Date();
        if (applicant_username.equals(last_applicant) && t.getTime() < last_applicant_time + 1000L * plugin.config.ASK_EXPIRE) {
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
     * Set a users home. This can be a new home.
     * 
     * @param name Home name
     * @param loc Location to set
     * @param visibility Optional visibility
     */
    public void setHome(String name, Location loc, HomeVisibility vis) {
        // Get the home
        Home h = homes.get(name.toLowerCase());
        if (h == null) {
            h = new Home(name, loc, vis);
            homes.put(name, h);
        } else {
            h.setName(name); // Update the case of the name.
            h.setLocation(loc);
            if (vis != null) {
                h.setVisibility(vis);
            }
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
        Home h = homes.get(name.toLowerCase());
        if (h == null) {
            throw new FormattedUserException(ChatColor.RED + "Home " + name + " not found.");
        }
        return h.getLocation();
    }

    /**
     * Get the visibility of a given home
     * 
     * @param name
     * @return
     */
    public HomeVisibility getHomeVisibility(String name) {
        Home h = homes.get(name.toLowerCase());
        if (h == null) {
            throw new FormattedUserException(ChatColor.RED + "Home " + name + " not found.");
        }
        return h.getVisibility();
    }

    /**
     * Delete a home location
     * 
     * @param name
     * @return Name of the delete home
     */
    public String deleteHome(String name) {
        Home h = homes.get(name.toLowerCase());
        if (h == null) {
            throw new FormattedUserException(ChatColor.RED + "Home " + name + " not found.");
        }
        homes.remove(name.toLowerCase());
        dirty = true;
        return h.getName();
    }

    /**
     * Get a set of names of all the homes.
     * 
     * @return The homes.
     */
    public Set<String> getHomeNames() {
        /*
         * Set<String> s = new HashSet<String>(); for(Home h : homes.values()) {
         * // Get the canonical name s.add(h.getName()); } return s;
         */
        // This is supposed to be more readable in java 8? o.O
        return homes.values().stream()
        .map(home -> home.getName())
        .collect(Collectors.toSet());
    }

    /**
     * Load/Store the data from the "home" configuration sections. This is
     * necessary to preserve case AND get case insensitive hash tables.
     *
     */
    private class Home {
        private String name;
        /** case sensitive name */
        private Location loc;
        /** Location of this home */
        private HomeVisibility vis;
        /** Visibility of this home */

        private final String LOCATION = "location";
        private final String VISIBILITY = "visibility";

        /**
         * Create a new home from a yaml configuration section.
         * 
         * @param name Name of the section to read.
         * @param s Section with all the home data.
         */
        public Home(String name, ConfigurationSection s) {
            s = s.getConfigurationSection(name);
            this.name = name;
            this.loc = (Location) s.get(LOCATION);
            this.vis = HomeVisibility.valueOf(
                                              s.getString(VISIBILITY,
                                                          HomeVisibility.UNLISTED.toString()));
        }

        /** Create a new home */
        public Home(String name, Location loc, HomeVisibility vis) {
            if (!validChars.matcher(name).matches()) {
                throw new FormattedUserException(ChatColor.RED + "ERROR: Invalid home name");
            }
            this.name = name;
            this.loc = loc;
            if (vis == null) {
                this.vis = HomeVisibility.UNLISTED;
            } else {
                this.vis = vis;
            }
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
            dirty = true;
        }

        public Location getLocation() {
            return loc;
        }

        public void setLocation(Location loc) {
            if (loc == null) {
                throw new IllegalArgumentException("loc cannot be null");
            }
            this.loc = loc;
            dirty = true;
        }

        public HomeVisibility getVisibility() {
            return vis;
        }

        public void setVisibility(HomeVisibility vis) {
            if (vis == null) {
                throw new IllegalArgumentException("vis cannot be null");
            }
            this.vis = vis;
            dirty = true;
        }

        /**
         * Write config to yaml. This method will create its own sub-section to
         * save its own keys.
         * 
         * @param s The "homes" section.
         */
        public void toYaml(ConfigurationSection s) {
            s = s.createSection(name);
            s.set(LOCATION, loc);
            s.set(VISIBILITY, vis.toString());
        }
    }
}
