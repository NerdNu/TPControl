package nu.nerd.tpcontrol;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;


/**
 * 
 * Maintain a two way cache between players and UUIDs.
 * 
 * The yaml cache keeps everything in RAM for maximum
 * speed. Every time a player logs in, their UUID will
 * be added to the cache.
 * 
 * NOTE: The _uuid_to_name map has the canonical name.
 * the names in _name_to_uuid are squashed to lower case.
 *
 */
public class UUIDCache implements AutoCloseable {
    
    private JavaPlugin _plugin;
    private MyListener _listener;
    private File _configFile;

    /** Secondary mapping from name to UUID. Some entries may be missing if duplicate names appear (Caused by player renaming) */
    private TreeMap<String, UUID> _name_to_uuid = new TreeMap<String, UUID>(String.CASE_INSENSITIVE_ORDER);

    /** Primary mapping from UUID and name */
    private Map<UUID, String> _uuid_to_name = new ConcurrentHashMap<UUID, String>();

    /** Try to not leak these in the server */
    private BukkitTask _task = null;
    
    /** Flag set if things need to be saved to disk */
    private boolean dirty = false;

    public UUIDCache(JavaPlugin plugin, File config) {
        _plugin = plugin;
       _listener = new MyListener();
       _configFile = config;

        // Load in the player cache
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(_configFile);
        for(String UUIDString : yaml.getKeys(false)) {
            String name = yaml.getString(UUIDString);
            
            // Parse UUID
            UUID uuid;
            try {
                uuid = UUID.fromString(UUIDString);
            } catch (IllegalArgumentException e) {
                _plugin.getLogger().warning("Culling invalid UUID \"" + UUIDString + "\".");
                continue;
            }

            // Ensure there are no duplicate player names.
            if(_name_to_uuid.containsKey(name)) {
                _plugin.getLogger().warning("Culling duplicate player name \"" + name + "\".");
                // Remove both player names. This will be fixed on next log-in.
                _name_to_uuid.remove(name);
                continue;
            }

            // Setup the mapping.
            _uuid_to_name.put(uuid, name);
            _name_to_uuid.put(name, uuid);
        }
        
        _plugin.getServer().getPluginManager().registerEvents(_listener, _plugin);
        _task = _plugin.getServer().getScheduler().runTaskTimer(_plugin, new CacheFlusher(), 20*60, 20*60);
    }
    
    /**
     * Close everything down. This _really_ should be called
     * during plugin.Unload().
     * @throws Exception
     */
    @Override
    public void close() {
        // Release spigot resources
        HandlerList.unregisterAll(_listener);
        if(_task != null) {
            _task.cancel();
            _task = null;
        }

        // cleanup
        flush(false);
        _uuid_to_name = null;
        _name_to_uuid = null;
    }

    /**
     * Get the name associated with this UUID, or null.
     * 
     * @param uuid
     * @return
     */
    public String getName(UUID uuid) {
        return _uuid_to_name.get(uuid);
    }
    
    /**
     * Get the UUID associated with this name, or null.
     * 
     * Partial player names are accepted.
     * 
     * @param name
     * @return UUID, or null if not found.
     */
    public UUID getUUID(String name) {
        // Quick check
        UUID uuid = _name_to_uuid.get(name);
        if (uuid != null) {
            return uuid;
        }

        // Pull out the first 2 entries bigger than name.
        // The equal too case has already been checked.
        Map.Entry<String, UUID> entry1 = _name_to_uuid.higherEntry(name);
        String entry2 = null;
        if (entry1 != null) {
            entry2 = _name_to_uuid.higherKey(entry1.getKey());
        }

        // See if the requested name is a prefix
        boolean entry1prefix = false;
        boolean entry2prefix = false;
        if(entry1 != null) {
            entry1prefix = entry1.getKey().toLowerCase().startsWith(name.toLowerCase());
        }
        if(entry2 != null) {
            entry2prefix = entry2.toLowerCase().startsWith(name.toLowerCase());
        }
       
        // Return the expanded name if we have it bounded.
        if (entry1prefix && !entry2prefix) {
            return entry1.getValue();
        } else {
            return null;
        }
    }
    
    /**
     * Get the UUID associated with this name, or null.
     * 
     * Partial player names are NOT accepted.
     * 
     * @param name
     * @return UUID, or null if not found.
     */
    public UUID getUUIDExact(String name) {
        return _name_to_uuid.get(name);
    }
    
    /**
     * Remove a UUID from the cache.
     * @param uuid
     */
    public void untrack(UUID uuid) {
        clearUUID(uuid);
    }

    /**
     * Merge and update a player into the cache.
     * 
     * @param uuid Player's uuid
     * @param name Player's name
     */
    private void updateCache(Player p) {
        UUID uuid = p.getUniqueId();
        String name = p.getName();

        // First, see if there are any changes so we don't dirty the cache
        String refName = _uuid_to_name.get(uuid);
        UUID refUUID = _name_to_uuid.get(name);
        if(name.equals(refName) && uuid.equals(refUUID)) {
            return;
        }

        // Its important not to overwrite a uuid or name mapping because player
        // can change and then reuse old names. UUID is considered De-Facto.
        //
        // It is even possible for two players to switch names and uuids!!! :O

        // Clear the uuid and player chains
        clearUUID(uuid); // this uuid could map to another player's name.
        clearName(name); // this name could map to another player's uuid.

        // Now map it correctly.
        _uuid_to_name.put(uuid, name);
        _name_to_uuid.put(name, uuid);
        dirty = true;
    }
    
    /**
     * Clear uuid->name->uuid chains.
     * 
     * NOTE: _uuid_to_name may contain null keys.
     * 
     * @param uuid Chain to clear.
     */
    private void clearUUID(UUID uuid) {
        if(_uuid_to_name.containsKey(uuid)) {
            String name = _uuid_to_name.get(uuid);
            if(name != null) {
                _uuid_to_name.put(uuid, null); // null UUID->name so we keep tracking it.
                clearName(name);
                dirty = true;
            }
        }
    }
    
    /**
     * Clear name->uuid->name chains.
     * 
     * @param name Chain to clear.
     */
    private void clearName(String name) {
        if(_name_to_uuid.containsKey(name)) {
            UUID uuid = _name_to_uuid.get(name);
            _name_to_uuid.remove(name); // Remove name->UUID
            clearUUID(uuid);
            dirty = true;
        }
    }
    
    /**
     * Flush the cache to disk.
     * @param async True to save asynchronously
     */
    private void flush(boolean async) {
        if (!dirty) {
            return;
        }

        if (async == false) {
            try {
                // Make a new blank config.
                YamlConfiguration yaml = new YamlConfiguration();
                for (UUID uuid : _uuid_to_name.keySet()) {
                    yaml.set(uuid.toString(), _uuid_to_name.get(uuid));
                }
                yaml.save(_configFile);
                dirty = false;
            } catch (Exception ex) {
                _plugin.getLogger().severe(
                        "Cannot save player UUID Cache! " + ex.toString());
            }
        } else {
            // Save on async thread
            _plugin.getServer().getScheduler().runTaskAsynchronously(_plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Make a new blank config.
                            YamlConfiguration yaml = new YamlConfiguration();
                            for (UUID uuid : _uuid_to_name.keySet()) {
                                yaml.set(uuid.toString(), _uuid_to_name.get(uuid));
                            }
                            yaml.save(_configFile);
                            dirty = false; // Writing to boolean's from
                                           // different threads is A-OK ;)
                        } catch (Exception ex) {
                            _plugin.getLogger().severe("Cannot save player UUID Cache! " + ex.toString());
                        }
                    }
                }
            );
        }
    }

    /**
     * Watch for player join events.
     */
    private class MyListener implements Listener {
    
        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerJoin(PlayerJoinEvent e) {
            // Cache all players
            updateCache(e.getPlayer());
        }
    }
    
    /**
     * Flush the cache every now and again.
     */
    private class CacheFlusher implements Runnable {

        @Override
        public void run() {
            flush(true);
        }
        
    }
}
