package nu.nerd.tpcontrol;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ShortcutCommand implements CommandExecutor {
    protected String name;
    protected TPControl plugin;
    protected Warp warp;

    public ShortcutCommand(String name, TPControl plugin, Warp warp) {
        this.name = name;
        this.plugin = plugin;
        this.warp = warp;
    }

    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        GhostCommand ghostCommand = new GhostCommand("warp");
        this.plugin.onCommand(sender, ghostCommand, "warp", new String[] { this.warp.getName() });
        return false;
    }
}
