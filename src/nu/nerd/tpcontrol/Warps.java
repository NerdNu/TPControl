package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class Warps {
    private TPControl plugin;
    public File yamlfile;
    private YamlConfiguration yaml;

    public Warps(TPControl instance) {
        plugin = instance;
        yamlfile = new File(plugin.getDataFolder(), "warps.yml");
        yaml = YamlConfiguration.loadConfiguration(yamlfile);
    }

    public void addwarp(String name, CommandSender p, double x, double y, double z) {
    	name = name.toLowerCase();
        if(p.hasPermission("tpcontrol.level.admin")) {
            yaml.set(name + ".x", x);
            yaml.set(name + ".y", y);
            yaml.set(name + ".z", z);
            
            try {
                yaml.save(yamlfile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            p.sendMessage(ChatColor.GREEN + "Successfully added warp '" + name + "'.");
        }
    }

    public void delwarp(String name, CommandSender p) {
    	name = name.toLowerCase();
        if(p.hasPermission("tpcontrol.level.admin")) {
            yaml.set(name, null);
            try {
                yaml.save(yamlfile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            p.sendMessage(ChatColor.GREEN + "Successfully deleted warp '" + name + "'.");
        }
    }

    public void listwarps(CommandSender p) {
        String warplist = "List of warps: ";
        for(String key : yaml.getKeys(false)) {
            warplist += key.toString() + ", ";
        }
        p.sendMessage(warplist.substring(0, (warplist.length() - 2)));
    }

    public void warp(String name, CommandSender p) {
    	name = name.toLowerCase();
        if(yaml.getKeys(false).contains(name)) {
            double x = yaml.getDouble(name + ".x");
            double y = yaml.getDouble(name + ".y");
            double z = yaml.getDouble(name + ".z");
            Player player = (Player)p;
            p.sendMessage(ChatColor.GREEN + "Warping you to '" + name + "'.");
            player.teleport(new Location(player.getWorld(), x, y, z));
        } else {
            p.sendMessage(ChatColor.RED + "Invalid warp. Use /warplist to see the available warps.");
        }
    }
}
