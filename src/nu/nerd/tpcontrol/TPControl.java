package nu.nerd.tpcontrol;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TPControl extends JavaPlugin {
    Logger log = Logger.getLogger("Minecraft");
    
    
    //private final TPControlListener cmdlistener = new TPControlListener(this);
    public final Configuration config = new Configuration(this);
    
    private HashMap<String,User> user_cache = new HashMap<String, User>();
    
    
    
    
    
    public void onEnable(){
        log = this.getLogger();
        
        //Load config
        File config_file = new File(getDataFolder(), "config.yml");
        if(!config_file.exists()) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        config.load();
        
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
    
    public void onDisable(){
        log.info("TPControl has been disabled.");
    }
    
    public Player getPlayer(String name) {
        Player[] players = getServer().getOnlinePlayers();

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
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        //
        // /tp
        //
        if (command.getName().equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            Player p1 = (Player)sender;
            if(!canTP(p1)) {
                p1.sendMessage(ChatColor.RED + "You do not have permission to teleport!");
                return true;
            }
            
            if(args.length != 1) {
                p1.sendMessage(ChatColor.RED + "Usage: /tp <player>");
                return true;
            }
            
            Player p2 = getPlayer(args[0]);
            if(p2 == null) {
                p1.sendMessage(ChatColor.RED + "Could not find player " + args[0] + "!");
                return true;
            }
            
            User u2 = getUser(p2);
            String mode;
            
            if(canOverride(p1, p2)) {
                mode = "allow";
            } else {
                mode = u2.getCalculatedMode(p1); //Get the mode to operate under (allow/ask/deny)
            }
            
            
            if (mode.equals("allow")) {
                p1.sendMessage(ChatColor.GREEN + "Teleporting you to " + p2.getName() + ".");
                teleport(p1, p2);
            } 
            else if (mode.equals("ask")) {
                u2.lodgeRequest(p1);
            } 
            else if (mode.equals("deny")) {
                p1.sendMessage(ChatColor.RED + p2.getName() + " has teleportation disabled.");
            }
            return true;
        }
        //
        // /tppos [world] x y z
        //
        else if (command.getName().equalsIgnoreCase("tppos")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            
            Player p = (Player)sender;
            
            if (args.length < 3 || args.length > 4) {
                p.sendMessage(ChatColor.RED + "Invalid paramaters. Syntax: /tppos [world] x y z");
                return true;
            }
            
            World w = null;
            if (args.length == 4) {
                w = getServer().getWorld(args[0]);
                args = new String[] { args[1], args[2], args[3] };
                if (w == null) {
                    p.sendMessage(ChatColor.RED + "An invalid world was provided.");
                    return true;
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
                p.sendMessage(ChatColor.RED + "Invalid paramaters. Syntax: /tppos [world] x y z");
                return true;
            }
            
            p.teleport(new Location(w, x, y, z));
            
            return true;
        }
        //
        // /tphere <player>
        //
        else if (command.getName().equalsIgnoreCase("tphere")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            
            
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            if(args.length != 1) {
                p2.sendMessage(ChatColor.RED + "Usage: /tphere <player>");
                return true;
            }
            
            Player p1 = getPlayer(args[0]);
            if(p1 == null) {
                p2.sendMessage(ChatColor.RED + "Couldn't find player " + args[0] + ".");
                return true;
            }
            
            if(canTP(p2) && canOverride(p2, p1)) {
                p1.sendMessage(ChatColor.GREEN + p2.getName() + " teleported you to them.");
                p2.sendMessage(ChatColor.GREEN + "Teleporting " + p1.getName() + " to you.");
                teleport(p1, p2);
                return true;
            } else {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
        }
        //
        // /tpmode allow|ask|deny
        //
        else if (command.getName().equalsIgnoreCase("tpmode")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);
            
            if(args.length != 1 || (!args[0].equals("allow") &&
                                    !args[0].equals("ask") &&
                                    !args[0].equals("deny"))) {
                p2.sendMessage(ChatColor.RED + "Usage: /tpmode allow|ask|deny");
                p2.sendMessage(ChatColor.GOLD + "You are currently in " + u2.getMode().toUpperCase() + " mode.");
            } else {
                u2.setMode(args[0]);
                p2.sendMessage(ChatColor.GOLD + "You are now in " + args[0].toUpperCase() + " mode.");
            }
            return true;
        }
        //
        // /tpallow
        //
        else if (command.getName().equalsIgnoreCase("tpallow")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);

            //Check the field exists...
            if(u2.last_applicant == null) {
                p2.sendMessage(ChatColor.RED + "Error: No one has attempted to tp to you lately!");
                return true;
            }
            
            //Check it hasn't expired
            Date t = new Date();
            if(t.getTime() > u2.last_applicant_time + 1000L*config.ASK_EXPIRE) {
                p2.sendMessage(ChatColor.RED + "Error: /tp request has expired!");
                return true;
            }
            
            
            Player p1 = getPlayer(u2.last_applicant);
            
            if(p1 == null) {
                p2.sendMessage(ChatColor.RED + "Error: " + u2.last_applicant + " is no longer online.");
                return true;
            }
            
            u2.last_applicant = null;
            p1.sendMessage(ChatColor.GREEN + "Teleporting you to " + p2.getName() + ".");
            p2.sendMessage(ChatColor.GREEN + "Teleporting " + p1.getName() + " to you.");
            teleport(p1, p2);
            
            
            return true;
        }
        //
        // /tpdeny
        //
        else if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);

            if(u2.last_applicant == null) {
                p2.sendMessage(ChatColor.RED + "Error: No one has attempted to tp to you lately!");
                return true;
            }
            
            
            p2.sendMessage(ChatColor.RED + "Denied" + ChatColor.GOLD + " a request from " + u2.last_applicant + ".");
            p2.sendMessage(ChatColor.GOLD + "Use '/tpblock " + u2.last_applicant + "' to block further requests");
            u2.last_applicant = null;
            
            return true;
        }
        //
        // /tpfriend <player>
        //
        else if (command.getName().equalsIgnoreCase("tpfriend")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);
            if(args.length != 1) {
                p2.sendMessage(ChatColor.RED + "Usage: /tpfriend <player>");
                return true;
            }
            if(u2.addFriend(args[0])) {
                p2.sendMessage(ChatColor.GREEN + args[0] + " added as a friend.");
            } else {
                p2.sendMessage(ChatColor.RED + "Error: " + args[0] + " is already a friend.");
            }
            return true;
        }
        //
        // /tpunfriend <player>
        //
        else if (command.getName().equalsIgnoreCase("tpunfriend")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }

            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);
            if(args.length != 1) {
                p2.sendMessage(ChatColor.RED + "Usage: /tpunfriend <player>");
                return true;
            }
            if(u2.delFriend(args[0])) {
                p2.sendMessage(ChatColor.GREEN + args[0] + " removed from friends.");
            } else {
                p2.sendMessage(ChatColor.RED + "Error: " + args[0] + " not on friends list.");
            }
            return true;
        }
        //
        // /tpblock <player>
        //
        else if (command.getName().equalsIgnoreCase("tpblock")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }
            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission");
                return true;
            }
            
            User u2 = getUser(p2);
            if(args.length != 1) {
                p2.sendMessage(ChatColor.RED + "Usage: /tpblock <player>");
                return true;
            }
            if(u2.addBlocked(args[0])) {
                p2.sendMessage(ChatColor.GOLD + args[0] + " was " + ChatColor.RED + " blocked " + ChatColor.GOLD + "from teleporting to you.");
            } else {
                p2.sendMessage(ChatColor.RED + "Error: " + args[0] + " is already blocked.");
            }
            return true;
        }
        //
        // /tpunblock <player>
        //
        else if (command.getName().equalsIgnoreCase("tpunblock")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This cannot be run from the console!");
                return true;
            }

            Player p2 = (Player)sender;
            
            if(!canTP(p2)) {
                p2.sendMessage(ChatColor.RED + "You do not have permission!");
                return true;
            }
            
            User u2 = getUser(p2);
            if(args.length != 1) {
                p2.sendMessage(ChatColor.RED + "Usage: /tpunblock <player>");
                return true;
            }
            if(u2.delBlocked(args[0])) {
                p2.sendMessage(ChatColor.GOLD + args[0] + " was " + ChatColor.GREEN + " unblocked " + ChatColor.GOLD + "from teleporting to you.");
            } else {
                p2.sendMessage(ChatColor.RED + "Error: " + args[0] + " is not blocked.");
            }
            return true;
        }
        return false;
    }
    
    //Pull a user from the cache, or create it if necessary
    private User getUser(Player p) {
        User u = user_cache.get(p.getName().toLowerCase());
        if(u == null) {
            u = new User(this, p);
            user_cache.put(p.getName().toLowerCase(), u);
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
    
    private void teleport(Player p1, Player p2) {
        p1.teleport(p2.getLocation());
        if (p2.isFlying()) {
            p1.setFlying(true);
        }
    }
}