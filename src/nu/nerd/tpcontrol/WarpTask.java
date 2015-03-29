package nu.nerd.tpcontrol;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WarpTask extends BukkitRunnable {
    protected TPControl plugin;
    protected Warp warp;
    protected Player player;

    public WarpTask(TPControl plugin, Warp warp, Player player) {
        this.plugin = plugin;
        this.warp = warp;
        this.player = player;
    }

    public Warp getWarp() {
        return warp;
    }

    public void setWarp(Warp warp) {
        this.warp = warp;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void run() {
        this.player.sendMessage(ChatColor.GOLD + "Warping to " + this.warp.getName());
        this.player.teleport(this.warp.getLocation());
        if (this.plugin.warp_warmups.containsKey(this.player)) {
            this.plugin.warp_warmups.remove(this.player);
        }
    }
}
