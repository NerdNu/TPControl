package nu.nerd.tpcontrol;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.wimbli.WorldBorder.BorderData;
import com.wimbli.WorldBorder.WorldBorder;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.SimplePluginManager;

public class TPControl extends JavaPlugin implements Listener {
    Logger log = Logger.getLogger("Minecraft");

    //private final TPControlListener cmdlistener = new TPControlListener(this);
    public final Configuration config = new Configuration(this);

    private HashMap<String,User> user_cache = new HashMap<String, User>();
    public HashMap<Player, WarpTask> warp_warmups = new HashMap<Player, WarpTask>();
    private UUIDCache uuidcache = null;

    public Economy economy = null;
    private WorldBorder worldBorder = null;
    private WorldGuardPlugin worldGuard = null;

    @Override
    public void onEnable(){
        log = this.getLogger();

        // Setup Vault Economy
        if (getServer().getPluginManager().getPlugin("Vault") != null)  {
            RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (economyProvider != null) {
                this.economy = economyProvider.getProvider();
            }
        }

        Plugin plugin = getServer().getPluginManager().getPlugin("WorldBorder");
        if (plugin != null && plugin instanceof WorldBorder)
        	worldBorder = (WorldBorder)plugin;
        
        plugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if (plugin != null && plugin instanceof WorldGuardPlugin)
        	worldGuard = (WorldGuardPlugin)plugin;

        //Listen to events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);

        //Load config
        File config_file = new File(getDataFolder(), "config.yml");
        if(!config_file.exists()) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        config.load();
        
        uuidcache = new UUIDCache(this, new File(this.getDataFolder(), "uuidcache.yml"));

        //TODO: Can we get away with async?
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                for(User u : user_cache.values()) {
                    u.save();
                }
            }
        }, config.SAVE_INTERVAL*20, config.SAVE_INTERVAL*20);

        log.info("TPControl has been enabled!");
    }

    @Override
    public void onDisable(){
        for(User u : user_cache.values()) {
            u.save();
        }
        uuidcache.close();
        uuidcache = null;
        log.info("TPControl has been disabled.");
    }

    public Player getPlayer(String name) {
        Collection<? extends Player> players = getServer().getOnlinePlayers();

        Player found = null;
        String lowerName = name.toLowerCase();
        int delta = Integer.MAX_VALUE;
        for (Player player : players) {
            if (ChatColor.stripColor(player.getName()).toLowerCase().startsWith(lowerName)) {
                int curDelta = player.getName().length() - lowerName.length();
                if (curDelta < delta) {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) break;
            }
        }
        return found;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled())
            return;

        if (event.getPlayer().hasPermission("tpcontrol.back") &&
            (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND)
        ) {
            User u = getUser(event.getPlayer());
            u.setLastLocation(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock().getType() == Material.WALL_SIGN) {
            Sign s = (Sign) event.getClickedBlock().getState();
            Player p = event.getPlayer();
            if (event.getItem() != null && event.getItem().getType() == Material.DIAMOND_HOE) {
                // Registering a sign
                if (s.getLines().length > 1) {
                    if (!s.getLine(0).isEmpty() && p.hasPermission("tpcontrol.level.admin") && s.getLine(0).equalsIgnoreCase("[WARP]")) {
                        s.setLine(3, ChatColor.GREEN + "." + ChatColor.BLACK);
                        s.update();
                        p.sendMessage("Warp sign created.");
                        return;
                    }
                }
            }
            if (s.getLines().length > 1) {
                if (!s.getLine(3).isEmpty() && s.getLine(3).equalsIgnoreCase(ChatColor.GREEN + "." + ChatColor.BLACK)) {
                    if (!s.getLine(1).isEmpty() && s.getLine(1).matches("\\s*-*\\d+\\s*,\\s*-*\\d+\\s*,\\s*-*\\d+\\s*")) {
                        String[] sCo = s.getLine(1).split(",");
                        // Change our strings to ints, remember to remove spaces because people can be stupid about fomatting
                        Location tp = new Location(p.getWorld(), Integer.parseInt(sCo[0].trim()), Integer.parseInt(sCo[1].trim()), Integer.parseInt(sCo[2].trim()));
                        p.teleport(tp);
                    } else {
                        p.sendMessage("Warp sign incorrect.");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p1 = event.getPlayer();
        if (this.warp_warmups.containsKey(p1)) {
            WarpTask warpTask = this.warp_warmups.get(p1);
            if (warpTask.getWarp().doesWarmupCancelOnMove()) {
                Location l1 = event.getFrom();
                Location l2 = event.getTo();
                if ((int)l1.getX() != (int)l2.getX() || (int)l1.getY() != (int)l2.getY() || (int)l1.getZ() != (int)l2.getZ()) {
                    this.warp_warmups.get(p1).cancel();
                    this.warp_warmups.remove(p1);
                    warpTask.getWarp().removeWarmup(p1);
                    messagePlayer(p1, "Cancelling warp.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p1 = (Player) event.getEntity();
            if (this.warp_warmups.containsKey(p1)) {
                WarpTask warpTask = this.warp_warmups.get(p1);
                if (warpTask.getWarp().doesWarmupCancelOnDamage()) {
                    this.warp_warmups.get(p1).cancel();
                    this.warp_warmups.remove(p1);
                    messagePlayer(p1, "Cancelling warp.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p1 = event.getPlayer();
        if (this.warp_warmups.containsKey(p1)) {
            this.warp_warmups.get(p1).cancel();
            this.warp_warmups.remove(p1);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        
        try{
        	switch (command.getName().toLowerCase()) {
    		case "tpcontrol": cmdTPControl(sender, args); return true;
    		case "tp": cmdTP(sender, args); return true;
    		case "back": cmdBack(sender); return true;
    		case "tppos": cmdTPPos(sender, args); return true;
    		case "tphere": cmdTPHere(sender, args); return true;
    		case "tpmode": cmdTPMode(sender, args); return true;
    		case "tpallow": cmdTPAllow(sender); return true;
    		case "tpdeny": cmdTPDeny(sender); return true;
    		case "tpfriend": cmdTPFriend(sender, args); return true;
    		case "tpunfriend": cmdTPUnfriend(sender, args); return true;
    		case "tpblock": cmdBlock(sender, args); return true;
    		case "tpunblock": cmdUnblock(sender, args); return true;
    		case "warps": cmdWarps(sender); return true;
    		case "warp": cmdWarp(sender, args); return true;
    		case "setwarp": cmdSetWarp(sender, args); return true;
    		case "delwarp": cmdDelWarp(sender, args); return true;
    		case "cancelwarp": cmdCancelWarp(sender); return true;
    		case "randloc": cmdRandLoc(sender, args); return true;
            case "home": return cmdHome(sender, args);
    		case "sethome": return cmdSetHome(sender, args);
    		case "delhome": return cmdDelHome(sender, args);
    		case "listhomes": return cmdListHomes(sender, args);
    		default: return false;
        	}
        } catch (FormattedUserException e) {
            e.print(sender);
            return true;
        }

    }

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTPControl(CommandSender sender, String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
		    config.load();
		    sender.sendMessage("Reloaded config.");
		    return;
		}
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTP(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
		Player p1 = (Player)sender;
		if(!canTP(p1)) {
		    messagePlayer(p1, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		if(args.length != 1) {
		    messagePlayer(p1, "Usage: /tp <player>", ChatColor.GOLD);
		    return;
		}
	
		Player p2 = getPlayer(args[0]);
		if(p2 == null) {
		    messagePlayer(p1, "Couldn't find player "+ args[0], ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);
		String mode;
	
		if(canOverride(p1, p2)) {
		    mode = "allow";
		} else {
		    mode = u2.getCalculatedMode(p1); //Get the mode to operate under (allow/ask/deny)
		}
	
	
		if (mode.equals("allow")) {
		    messagePlayer(p1, "Teleporting you to " + p2.getName() + ".", ChatColor.GREEN);
		    teleport(p1, p2);
		}
		else if (mode.equals("ask")) {
		    messagePlayer(p1, "A request has been sent to " + p2.getName() + ".", ChatColor.GREEN);
		    u2.lodgeRequest(p1);
		}
		else if (mode.equals("deny")) {
		    messagePlayer(p1, p2.getName() + " has teleportation disabled.", ChatColor.RED);
		}
	}

	/**
	 * @param sender
	 */
	private void cmdBack(CommandSender sender) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		Player p = (Player)sender;
	
		if (!p.hasPermission("tpcontrol.back")) {
		    messagePlayer(p, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u = getUser(p);
	
		Location l = u.getLastLocation();
	
		if (l != null) {
		    u.setLastLocation(null);
		    p.teleport(l);
		}
		else {
		    p.sendMessage(ChatColor.RED + "No last location saved for you.");
		}
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTPPos(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		if (args.length < 3 || args.length > 4) {
		    sender.sendMessage("Invalid paramaters. Syntax: /tppos [world] x y z");
		    return;
		}
	
		Player p = (Player)sender;
		World w = null;
		if (args.length == 4) {
		    w = getServer().getWorld(args[0]);
		    args = new String[] { args[1], args[2], args[3] };
		    if (w == null) {
		        sender.sendMessage(ChatColor.RED + "An invalid world was provided.");
		        return;
		    }
		}
		else {
		    w = p.getWorld();
		}
	
		double x, y, z;
	
		try {
		    x = Double.parseDouble(args[0]);
		    y = Double.parseDouble(args[1]);
		    z = Double.parseDouble(args[2]);
		} catch (NumberFormatException ex) {
		    sender.sendMessage("Invalid paramaters. Syntax: /tppos [world] x y z");
		    return;
		}
	
		p.teleport(new Location(w, x, y, z));
	}

	/**
	 * @param sender
	 * @param args
	 * @return
	 */
	private boolean cmdTPHere(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return true;
		}
	
	
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return true;
		}
	
		if(args.length != 1) {
		    messagePlayer(p2, "Usage: /tphere <player>", ChatColor.GOLD);
		    return true;
		}
	
		Player p1 = getPlayer(args[0]);
		if(p1 == null) {
		    messagePlayer(p2, "Couldn't find player "+ args[0], ChatColor.RED);
		    return true;
		}
	
		if(canTP(p2) && canOverride(p2, p1)) {
		    messagePlayer(p1, p2.getName() + " teleported you to them.", ChatColor.GREEN);
		    messagePlayer(p2, "Teleporting " + p1.getName() + " to you.", ChatColor.GREEN);
		    teleport(p1, p2);
		    return true;
		} else {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return true;
		}
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTPMode(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);
	
		if(args.length != 1 || (!args[0].equals("allow") &&
		                        !args[0].equals("ask") &&
		                        !args[0].equals("deny"))) {
		    messagePlayer(p2, "Usage: /tpmode allow|ask|deny", ChatColor.GOLD);
		    messagePlayer(p2, "You are currently in " + u2.getMode().toUpperCase() + " mode.", ChatColor.GOLD);
		} else {
		    u2.setMode(args[0]);
		    messagePlayer(p2, "You are now in " + args[0].toUpperCase() + " mode.", ChatColor.GOLD);
		}
	}

	/**
	 * @param sender
	 */
	private void cmdTPAllow(CommandSender sender) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission." , ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);
	
		//Check the field exists...
		if(u2.last_applicant == null) {
		    messagePlayer(p2, "Error: No one has attempted to tp to you lately!", ChatColor.RED);
		    return;
		}
	
		//Check it hasn't expired
		Date t = new Date();
		if(t.getTime() > u2.last_applicant_time + 1000L*config.ASK_EXPIRE) {
		    messagePlayer(p2, "Error: /tp request has expired!", ChatColor.RED);
		    return;
		}
	
	
		Player p1 = getPlayer(u2.last_applicant);
	
		if(p1 == null) {
		    messagePlayer(p2, "Error: "+u2.last_applicant+" is no longer online.");
		    return;
		}
	
		u2.last_applicant = null;
		messagePlayer(p1, "Teleporting you to " + p2.getName() + ".", ChatColor.GREEN);
		messagePlayer(p2, "Teleporting " + p1.getName() + " to you.", ChatColor.GREEN);
		teleport(p1, p2);
	}

	/**
	 * @param sender
	 */
	private void cmdTPDeny(CommandSender sender) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);
	
		if(u2.last_applicant == null) {
		    messagePlayer(p2, "Error: No one has attempted to tp to you lately!", ChatColor.RED);
		    return;
		}
	
	
		messagePlayer(p2, "Denied a request from "+u2.last_applicant+".", ChatColor.RED);
		messagePlayer(p2, "Use '/tpblock "+u2.last_applicant+"' to block further requests", ChatColor.RED);
		u2.last_applicant = null;
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTPFriend(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);

		if(args.length == 0) {
			PrettyPrintUUIDList(sender, "Friends", u2.getFriends());
		} else if (args.length == 1) {
			// Add a new friend
			if(u2.addFriend(args[0])) {
			    messagePlayer(p2, args[0] + " added as a friend.", ChatColor.GREEN);
			} else {
			    messagePlayer(p2, "Error: " + args[0] + " is already a friend.", ChatColor.GOLD);
			}
		} else {
			// Invalid arguments
			messagePlayer(p2, "Usage: /tpfriend [<player>]", ChatColor.GOLD);
		}
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdTPUnfriend(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);

		if(args.length == 0) {
			PrettyPrintUUIDList(sender, "Friends", u2.getFriends());
		} else if(args.length == 1) {
			if(u2.delFriend(args[0])) {
			    messagePlayer(p2, args[0] + " removed from friends.", ChatColor.GREEN);
			} else {
			    messagePlayer(p2, "Error: " + args[0] + " not on friends list.", ChatColor.GOLD);
			}
		} else {
		    messagePlayer(p2, "Usage: /tpunfriend [<player>]", ChatColor.RED);
		}
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdBlock(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);

		if(args.length == 0) {
			PrettyPrintUUIDList(sender, "Blocked: ", u2.getBlocked());
		} else if (args.length == 1) {
			if(u2.addBlocked(args[0])) {
			    messagePlayer(p2, args[0] + " was blocked from teleporting to you.", ChatColor.GREEN);
			} else {
			    messagePlayer(p2, "Error: " + args[0] + " is already blocked.", ChatColor.GOLD);
			}
		} else {
		    messagePlayer(p2, "Usage: /tpblock [<player>]", ChatColor.GOLD);
		}

	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdUnblock(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		Player p2 = (Player)sender;
	
		if(!canTP(p2)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		User u2 = getUser(p2);
		
		if(args.length == 0) {
			PrettyPrintUUIDList(sender, "Blocked: ", u2.getBlocked());
		} else if (args.length == 1) {
			if(u2.delBlocked(args[0])) {
			    messagePlayer(p2, args[0] + " was unblocked from teleporting to you.", ChatColor.GREEN);
			} else {
			    messagePlayer(p2, "Error: " + args[0] + " is not blocked.", ChatColor.GOLD);
			}
		} else {
		    messagePlayer(p2, "Usage: /tpunblock [<player>]", ChatColor.GOLD);
		}

	}

	/**
	 * 
	 * Handle the /warps command
	 * 
	 * @param sender
	 */
	private void cmdWarps(CommandSender sender) {
		Player player = null;
		if (sender instanceof Player)
		    player = (Player)sender;
	
		StringBuilder sb = new StringBuilder();
		int j = 0;
		Iterator<Warp> i = this.config.WARPS.values().iterator();
		while (i.hasNext()) {
		    Warp warp = (Warp) i.next();
	
		    if (player == null || canWarp(player, warp)) {
		        if (j % 2 == 0)
		            sb.append(ChatColor.GRAY);
		        else
		            sb.append(ChatColor.WHITE);
	
		        sb.append(warp.getName()).append(", ");
		        j++;
		    }
		}
		String list = sb.toString();
		if (list.length() >= 2)
		    list = list.substring(0, list.length() - 2);
	
		sender.sendMessage(ChatColor.GOLD + "Warps: " + list);
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdWarp(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		final Player p2 = (Player)sender;
		
		if(args.length == 0) {
			cmdWarps(sender);
			return;
		}
	
		if(args.length != 1) {
		    messagePlayer(p2, "Usage: /warp <warp>", ChatColor.GOLD);
		    return;
		}
	
		final Warp w1 = this.config.WARPS.get(args[0].toLowerCase());
	
		if(w1 == null) {
		    messagePlayer(p2, "That warp does not exist.", ChatColor.RED);
		    return;
		}
	
		if(!canWarp(p2, w1)) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		if(economy != null) {
		    boolean low_en = w1.getBalanceMin() != null;
		    boolean low = low_en;
		    if (low_en)
		        low = economy.getBalance(p2) >= w1.getBalanceMin();
	
		    boolean high_en = w1.getBalanceMax() != null;
		    boolean high = high_en;
		    if (high_en)
		        high = economy.getBalance(p2) <= w1.getBalanceMax();
	
		    if (low_en && low) {
		        messagePlayer(p2, "Your balance is too low.", ChatColor.RED);
		    }
		    if (high_en && high) {
		        messagePlayer(p2, "Your balance is too high.", ChatColor.RED);
		    }
	
		    if (low_en && !low || high_en && !high) {
		        return;
		    }
		}
	
		Date now = new Date();
		Date p2cd = w1.getCooldown(p2);
		if (p2cd != null && !now.after(new Date(p2cd.getTime() + w1.getCooldown() * 1000))) {
		    messagePlayer(p2, "You must wait ", ChatColor.RED);
		    return;
		}
	
		if (w1.getWarmup() > 0) {
		    Date p2wu = w1.getWarmup(p2);
		    if (p2wu != null) {
		        int elapsed = (int) ((now.getTime() - p2wu.getTime()) / 1000);
		        int remaining = w1.getWarmup() - elapsed;
		        messagePlayer(p2, "You have " + remaining + " second(s) of warmup remaining.", ChatColor.RED);
	
		        return;
		    }
		    else {
		        messagePlayer(p2, "There is a " + w1.getWarmup() + " second warmup for this warp.", ChatColor.RED);
		        if (w1.doesWarmupCancelOnMove()) {
		            messagePlayer(p2, "You must not move during this time", ChatColor.RED);
		        }
	
		        WarpTask warpTask = new WarpTask(this, w1, p2);
		        this.warp_warmups.put(p2, warpTask);
		        warpTask.runTaskLater(this, w1.getWarmup() * 20);
	
		        w1.setWarmup(p2, now);
	
		        return;
		    }
		}
	
		if (w1.allowBack) {
		    User u = getUser(p2);
		    u.setLastLocation(p2.getLocation());
		}
	
		p2.teleport(w1.getLocation());
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdSetWarp(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		final Player p2 = (Player)sender;
	
		if(!p2.hasPermission("tpcontrol.setwarp")) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		if(args.length < 1) {
		    messagePlayer(p2, "Usage: /setwarp <name> [option] [values...]", ChatColor.GOLD);
		    return;
		}
	
		Warp w1 = this.config.WARPS.get(args[0].toLowerCase());
	
		if (w1 == null) {
		    w1 = new Warp(args[0], p2.getLocation());
		    this.config.WARPS.put(args[0].toLowerCase(), w1);
		}
	
		String option = "location";
		if (args.length >= 2) {
		    option = args[1];
		}
	
		if (option.equalsIgnoreCase("location")) {
		    w1.setLocation(p2.getLocation());
		    messagePlayer(p2, "Set location for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("warmup")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> warmup <number>", ChatColor.RED);
		        return;
		    }
	
		    int warmup = Integer.parseInt(args[2]);
		    w1.setWarmup(warmup);
		    messagePlayer(p2, "Set warmup for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("warmup-cancel-on-move")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> warmup-cancel-on-move <true/false>", ChatColor.RED);
		        return;
		    }
	
		    boolean warmupCancelOnMove = Boolean.parseBoolean(args[2]);
		    w1.setWarmupCancelOnMove(warmupCancelOnMove);
		    messagePlayer(p2, "Set warmup cancel-on-move for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("warmup-cancel-on-damage")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> warmup-cancel-on-damage <true/false>", ChatColor.RED);
		        return;
		    }
	
		    boolean warmupCancelOnDamage = Boolean.parseBoolean(args[2]);
		    w1.setWarmupCancelOnDamage(warmupCancelOnDamage);
		    messagePlayer(p2, "Set warmup cancel-on-damage for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("cooldown")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> cooldown <number>", ChatColor.RED);
		        return;
		    }
	
		    int cooldown = Integer.parseInt(args[2]);
		    w1.setCooldown(cooldown);
		    messagePlayer(p2, "Set cooldown for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("balance-min")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> balance-min <number>", ChatColor.RED);
		        return;
		    }
	
		    int balanceMin = Integer.parseInt(args[2]);
		    w1.setBalanceMin(balanceMin);
		    messagePlayer(p2, "Set min balance for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("balance-max")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> balance-max <number>", ChatColor.RED);
		        return;
		    }
	
		    int balanceMax = Integer.parseInt(args[2]);
		    w1.setBalanceMax(balanceMax);
		    messagePlayer(p2, "Set max balance for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("allow-back")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> allow-back <true/false>", ChatColor.RED);
		        return;
		    }
	
		    boolean allowBack = Boolean.parseBoolean(args[2]);
		    w1.setAllowBack(allowBack);
		    messagePlayer(p2, "Set allow-back for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("public")) {
		    if (args.length != 3) {
		        messagePlayer(p2, "Usage: /setwarp <name> public <true/false>", ChatColor.RED);
		        return;
		    }
		    boolean pub = Boolean.parseBoolean(args[2]);
		    w1.setPublic(pub);
		    messagePlayer(p2, "Set public to " + pub + " for warp " + args[0], ChatColor.GOLD);
		}
		else if (option.equalsIgnoreCase("shortcuts")) {
		    List<String> shortcuts = new ArrayList<String>();
		    for (int i = 2; i < args.length; i++) {
		        shortcuts.add(args[i]);
		    }
		    w1.setShortcuts(shortcuts);
		    for (String shortcut : shortcuts) {
		        ShortcutCommand shortcutCommand = new ShortcutCommand(shortcut, this, w1);
		        registerCommand(new String[] { shortcut });
		        getCommand(shortcut).setExecutor(shortcutCommand);
		    }
	
		    messagePlayer(p2, "Set shortcuts for warp " + args[0], ChatColor.GOLD);
		}
	
		getConfig().set("warps." + w1.getName() + ".location.world", w1.getLocation().getWorld().getName());
		getConfig().set("warps." + w1.getName() + ".location.x", w1.getLocation().getX());
		getConfig().set("warps." + w1.getName() + ".location.y", w1.getLocation().getY());
		getConfig().set("warps." + w1.getName() + ".location.z", w1.getLocation().getZ());
		getConfig().set("warps." + w1.getName() + ".location.yaw", w1.getLocation().getYaw());
		getConfig().set("warps." + w1.getName() + ".location.pitch", w1.getLocation().getPitch());
		getConfig().set("warps." + w1.getName() + ".warmup.length", w1.getWarmup());
		getConfig().set("warps." + w1.getName() + ".warmup.cancel-on-move", w1.doesWarmupCancelOnMove());
		getConfig().set("warps." + w1.getName() + ".warmup.cancel-on-damage", w1.doesWarmupCancelOnDamage());
		getConfig().set("warps." + w1.getName() + ".cooldown.length", w1.getCooldown());
		getConfig().set("warps." + w1.getName() + ".economy.balance.min", w1.getBalanceMin());
		getConfig().set("warps." + w1.getName() + ".economy.balance.max", w1.getBalanceMax());
		getConfig().set("warps." + w1.getName() + ".shortcuts", w1.getShortcuts());
		getConfig().set("warps." + w1.getName() + ".allow-back", w1.doesAllowBack());
		saveConfig();
	}

	/**
	 * @param sender
	 * @param args
	 */
	private void cmdDelWarp(CommandSender sender, String[] args) {
		if(!sender.hasPermission("tpcontrol.delwarp")) {
		    sender.sendMessage(ChatColor.RED + "You do not have permission.");
		    return;
		}
	
		if(args.length != 1) {
		    sender.sendMessage(ChatColor.RED + "Usage: /delwarp <name>");
		    return;
		}
	
		Warp w1 = this.config.WARPS.get(args[0].toLowerCase());
	
		if(w1 != null) {
		    this.config.WARPS.remove(args[0].toLowerCase());
		    sender.sendMessage(ChatColor.RED + "Removed warp " + args[0]);
		}
		else {
		    sender.sendMessage(ChatColor.RED + "Warp does not exist.");
		}
	
		getConfig().set("warps." + args[0], null);
		saveConfig();
	}

	/**
	 * @param sender
	 */
	private void cmdCancelWarp(CommandSender sender) {
		if (!(sender instanceof Player)) {
		    sender.sendMessage("This cannot be run from the console!");
		    return;
		}
	
		final Player p2 = (Player)sender;
	
		if(!p2.hasPermission("tpcontrol.cancelwarp")) {
		    messagePlayer(p2, "You do not have permission.", ChatColor.RED);
		    return;
		}
	
		if (warp_warmups.containsKey(p2)) {
		    messagePlayer(p2, "Cancelling warp.", ChatColor.RED);
		    WarpTask wt1 = warp_warmups.get(p2);
		    wt1.cancel();
		    warp_warmups.remove(p2);
	
		    return;
		}
	
		messagePlayer(p2, "You have no warp to cancel.", ChatColor.RED);
	}
	
	/**
	 * Warp to a random location
	 * @param sender
	 * @param args
	 */
	private void cmdRandLoc(CommandSender sender, String[] args) {
		// Check perms
		if (!(sender instanceof Player)) {
			sender.sendMessage("Etherial beings cannot be teleported. Sorry :(");
			return;
		}
		
		if (!sender.hasPermission("tpcontrol.randloc")) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to use /randloc");
			return;
		}
		
		// Figure out the world size and setup
		Player p = (Player)sender;
		RegionManager regionManager = null;
		BorderData borderData = null;
		
		if (worldGuard != null)
			regionManager = worldGuard.getRegionManager(p.getWorld());

		if (worldBorder != null)
			borderData = worldBorder.getWorldBorder(p.getWorld().getName());

		// Figure out the world shape
		if (borderData == null) {
			p.sendMessage(ChatColor.RED + "ERROR: Cannot get world size from WorldBorder plugin");
			return;
		}
		
		// Pick random locations until we find a good one
		for(int i = 0; i < 100; i++) {
			double x = (2.0*Math.random() - 1.0) * borderData.getRadiusX() + borderData.getX();
			double z = (2.0*Math.random() - 1.0) * borderData.getRadiusZ() + borderData.getZ();

			// Reject all points outside the border. We need this because circular
			// borders are smaller than our random location.
			if(!borderData.insideBorder(x, z))
				continue;

			// Calculate y offset
			double y = (double)p.getWorld().getHighestBlockYAt((int)x, (int)z) + 0.1;
			Location dest = new Location(p.getWorld(), x, y, z);

			// If we have a region manager, ensure our location is not inside a region
			if(regionManager != null) {
				ApplicableRegionSet set = regionManager.getApplicableRegions(dest);
				if (set.size() > 0) {
					continue; // Find another random location
				}
			}
			// Wrap it up
			p.teleport(dest);
			return;
		}
		
		p.sendMessage("Could not find a free spot. Try again later");
		return;
	}
	
	/**
	 * Go home
	 * 
	 * @param sender
	 * @param args
	 */
	public boolean cmdHome(CommandSender sender, String[] args) {
	    Player p = castPlayer(sender);
	    String homeName = null; // home to teleport to
	    User user = null; // User to get the home from.
	    
	    // Parse arguments
	    if(args.length == 0) {
	        // Go straight home
	        homeName = "default";
	        user = getUser(p);
	    } else if (args.length == 1) {
	        // Go to named home.
	        homeName = args[0];
	        user = getUser(p);
	    } else if (args.length == 2) {
	        // Go to the named home of another player.
	        user = getUser(args[0]);
	        homeName = args[1];
	        // Check for private homes.
	        if(!p.hasPermission("tpcontrol.homeadmin")) {
    	        if (user.getHomeVisibility(homeName) == HomeVisibility.PRIVATE) {
    	            throw new FormattedUserException(ChatColor.RED + "Home " + homeName + " not found.");
    	        }
	        }
	        
	    } else if (args.length >= 3) {
	        throw new FormattedUserException(ChatColor.RED +
	                "USAGE: /home\n" +
	                "USAGE: /home <name>\n" +
	                "USAGE: /home <player> <name>\n");
	    }

	    // Teleport to the saved location.
	    Location loc = user.getHome(homeName);
	    p.teleport(loc);
	    return true;
	}
	
	/**
	 * Set a home position at a player's present location.
	 * 
	 * @param sender
	 * @param args
	 */
	public boolean cmdSetHome(CommandSender sender, String[] args) {
        Player p = castPlayer(sender);
        
        String homeName = "default";
        HomeVisibility visibility = null;
        
        if(args.length >= 1) {
            homeName = args[0];
        }
        if(args.length >= 2) {
            try{
            visibility = HomeVisibility.valueOf(args[1].toUpperCase());
            } catch (Exception e) {
                return false;
            }
        }
        
        User u = getUser(p);
        u.setHome(homeName, p.getLocation(), visibility);
        p.sendMessage(ChatColor.GRAY + "Home set.");
        return true;
	}
	
	/**
	 * Delete a home
	 * 
	 * @param sender
	 * @param args
	 */
    public boolean cmdDelHome(CommandSender sender, String[] args) {
	    Player p = castPlayer(sender);

	    // Delete your own home
	    if(args.length == 1) {
	        String homeName = args[0];
	        getUser(p).deleteHome(homeName);
	        p.sendMessage(ChatColor.GRAY + "Home " + homeName + " cleared.");

	    // Delete another's home
	    } else if(args.length == 2) {
	        if(!p.hasPermission("tpcontrol.homeadmin")) {
	            throw new FormattedUserException(ChatColor.RED + "Usage: /delhome <name>");
	        }
	        String playerName = args[0];
	        String homeName = args[1];
	        getUser(playerName).deleteHome(homeName);
	        p.sendMessage(ChatColor.GRAY + "Home " + homeName + " cleared.");

	    // Invalid arguments
	    } else {
	        if(p.hasPermission("tpcontrol.homeadmin")) {
	            p.sendMessage(ChatColor.RED + "Usage: /delhome [player] <name>");
	        } else {
	            p.sendMessage(ChatColor.RED + "Usage: /delhome <name>");
	        }
	    }
	    return true;
	}
	
	/**
	 * List a player's homes
	 * @param sender
	 * @param args optional player name
	 */
	public boolean cmdListHomes(CommandSender sender, String[] args) {
	    Player p = castPlayer(sender);
	    User u = null;
	    boolean show_unlisted = false; // show unlisted and private homes
	    String player_desc = null;

	    if (args.length == 0) {
	        u = getUser(p); // List your own homes.
	        show_unlisted = true;
	        player_desc = "Your";
	    }
	    if (args.length == 1) {
	        u = getUser(args[0]);
	        show_unlisted = p.hasPermission("tpcontrol.homeadmin");
	        player_desc = u.getUsername();
	    }

	    p.sendMessage(ChatColor.GRAY + player_desc + " homes:");
	    for(String homeName : u.getHomeNames()) {
	        HomeVisibility vis = u.getHomeVisibility(homeName);
	        if (vis == HomeVisibility.PUBLIC || show_unlisted) {
	            p.sendMessage(ChatColor.GRAY + "  " + homeName + " [" + vis.toString() + "]");
	        }
	    }
	    return true;
	}

	/**
	 * Cast a command sender to a player. If not, throw a
	 * FormattedUserException explaining that only players are allowed.
	 * 
	 * @param sender
	 * @return A player
	 */
	public Player castPlayer(CommandSender sender) {
	    if (sender instanceof Player) {
	        return (Player)sender;
	    } else {
	        throw new FormattedUserException("Silly console, trix are for kids.");
	    }
	}
	

	//Pull a user from the cache, or create it if necessary
    public User getUser(Player p) {
        return getUser(p.getName());
    }
    
    //Pull a user from the cache, or create it if necessary
    public User getUser(String name) {
        User u = user_cache.get(name.toLowerCase());
        if(u == null) {
            UUID uuid = uuidcache.getUUID(name);
            name = uuidcache.getName(uuid); // Lookup canonical name (with correct case)
            if(uuid == null) {
                throw new FormattedUserException(ChatColor.RED + "Cannot find player " + name + ".");
            }
            u = new User(this, uuid, name);
            user_cache.put(name.toLowerCase(), u);
        }
        return u;
    }

    //Checks if p1 can override p2
    private boolean canOverride(Player p1, Player p2) {
        for(int j = config.GROUPS.size() - 1; j >= 0; j--){
            String g = config.GROUPS.get(j);
            if(p2.hasPermission("tpcontrol.level."+g)) {
                return false;
            }
            else if (p1.hasPermission("tpcontrol.level."+g)) {
                return true;
            }
        }
        return false;
    }

    private boolean canTP(Player p1) {
        for(String g : config.GROUPS) {
            if(g.equals(config.MIN_GROUP) && p1.hasPermission("tpcontrol.level."+g)) {
                return true;
            }
            else if(p1.hasPermission("tpcontrol.level."+g)) {
                return true;
            }
        }
        return false;
    }

    private boolean canWarp(Player p1, Warp w1) {
        return (p1.hasPermission("tpcontrol.warp.*") || p1.hasPermission("tpcontrol.warp." + w1.getName())) || w1.isPublic();
    }

    public void messagePlayer(Player p, String m) {
        messagePlayer(p, m, ChatColor.GRAY);
    }

    public void messagePlayer(Player p, String m, ChatColor color) {
//        p.sendMessage(ChatColor.GRAY + "[TP] " + color + m);
        p.sendMessage(color + m);
    }

    private void teleport(Player p1, Player p2) {
        Location loc = p2.getLocation();
        if(p2.isFlying() && !p1.isFlying()) {
            while (true) {
                if (loc.getBlock().getType() != Material.AIR){
                    loc.setY(loc.getY()+1);
                    break;
                }
                else if (loc.getY()-1 <= 0) {
                    Block b = loc.getWorld().getHighestBlockAt(loc);
                    if (b != null) {
                        loc.setY(b.getLocation().getY() + 1);
                    }
                    else {
                        loc = p2.getLocation();
                    }
                    break;
                }
                loc.setY(loc.getY() - 1);
            }
        }

        p1.teleport(loc);
    }

    public void registerCommand(String... aliases) {
        PluginCommand command = getNewCommand(aliases[0]);
        command.setAliases(Arrays.asList(aliases));
        getCommandMap().register(getDescription().getName(), command);
    }

    private PluginCommand getNewCommand(String name) {
        PluginCommand command = null;
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(new Class[] { String.class, Plugin.class });
            c.setAccessible(true);
            command = (PluginCommand) c.newInstance(new Object[]{name, this});
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "exception creating command", ex);
        }
        return command;
    }

    private CommandMap getCommandMap() {
        CommandMap commandMap = null;
        try {
            if ((Bukkit.getPluginManager() instanceof SimplePluginManager)) {
                Field f = SimplePluginManager.class.getDeclaredField("commandMap");
                f.setAccessible(true);
                commandMap = (CommandMap) f.get(Bukkit.getPluginManager());
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "exception getting command map", ex);
        }
        return commandMap;
    }
    
    /**
     * Print a pretty list with a gold title and alternating colored list items
     * 
     * @param sender Command Sender to print the list to
     * @param title Title of the list
     * @param list List to print
     */
    public void PrettyPrintUUIDList(CommandSender sender, String title, List<String> list) {

		StringBuilder sb = new StringBuilder();
		sb.append(ChatColor.GOLD);
		sb.append(title);
		sb.append(": ");

		int i = 0;
		for(String s : list) {
	        if (i % 2 == 0)
	            sb.append(ChatColor.GRAY);
	        else
	            sb.append(ChatColor.WHITE);

	        String name = uuidcache.getName(UUID.fromString(s));
	        if(name != null) {
	        	sb.append(name);
	        } else {
	        	sb.append(s);
	        }

	        if (i < list.size() - 1)
	        	sb.append(", ");

	        i++;
		}
		sender.sendMessage(sb.toString());
    }
}
