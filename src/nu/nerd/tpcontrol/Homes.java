package nu.nerd.tpcontrol;

import java.io.File;
import java.io.IOException;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class Homes {
    private TPControl plugin;
    private File yamlfile;
    private YamlConfiguration yaml;

    public Homes(TPControl instance) {
        plugin = instance;
        yamlfile = new File(plugin.getDataFolder(), "homes.yml");
        yaml = YamlConfiguration.loadConfiguration(yamlfile);
    }

    public void addhome(String name, CommandSender p, double x, double y, double z) {
    	name = name.toLowerCase();
    	p.sendMessage("Path: " + plugin.getDataFolder().getAbsolutePath());
        if(yaml.getStringList(p.getName()).size() == 16) {
            p.sendMessage(ChatColor.RED + "You have 16 homes already. Consider deleting one or more.");
            p.sendMessage(ChatColor.RED + "Remember that exceptional builds may have a warp.");
        } else {
            yaml.set(p.getName() + "." + name + ".x", x);
            yaml.set(p.getName() + "." + name + ".y", y);
            yaml.set(p.getName() + "." + name + ".z", z);

            try {
                yaml.save(yamlfile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            p.sendMessage(ChatColor.GREEN + "Added home '" + name + "'.");
        }
    }

    public void delhome(String name, CommandSender p) {
    	name = name.toLowerCase();
    	if(yaml.getConfigurationSection(p.getName()).getKeys(false).contains(name)) {
	    	yaml.set(p.getName() + "." + name, null);

	    	try {
	            yaml.save(yamlfile);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }

	        p.sendMessage(ChatColor.GREEN + "Successfully deleted home '" + name + "'.");
    	} else {
    		p.sendMessage(ChatColor.RED + "That home doesn't exist.");
    	}
    }

    public void listhomes(CommandSender p) {
        String homelist = "List of homes: ";
        for(String key : yaml.getConfigurationSection(p.getName()).getKeys(false)) {
            homelist += key.toString() + ", ";
        }
        p.sendMessage(homelist.substring(0, (homelist.length() - 2)));
    }

    public void home(String name, CommandSender p) {
    	name = name.toLowerCase();
        if(yaml.getConfigurationSection(p.getName()).getKeys(false).contains(name)) {
            double x = yaml.getDouble(p.getName() + "." + name + ".x");
            double y = yaml.getDouble(p.getName() + "." + name + ".y");
            double z = yaml.getDouble(p.getName() + "." + name + ".z");

            Player player = (Player)p;
            p.sendMessage(ChatColor.GREEN + "Teleporting you to '" + name + "'.");
            player.teleport(new Location(player.getWorld(), x, y, z));
        } else {
            p.sendMessage(ChatColor.RED + "Invalid home. Use /homes to see your available homes.");
        }
    }
}
