package nu.nerd.tpcontrol;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Warp {
    protected String name;
    protected Location location;

    protected int warmup = 0;
    protected boolean warmupCancelOnMove = false;
    protected boolean warmupCancelOnDamage = false;

    protected int cooldown = 0;

    protected Integer balanceMin = null;
    protected Integer balanceMax = null;

    protected Map<Player, Date> warmups = new HashMap<Player, Date>();
    protected Map<Player, Date> cooldowns = new HashMap<Player, Date>();

    protected boolean allowBack = false;

    protected List<String> shortcuts = new ArrayList<String>();

    public Warp(String name, Location location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getWarmup() {
        return warmup;
    }

    public void setWarmup(int warmup) {
        this.warmup = warmup;
    }

    public boolean doesWarmupCancelOnMove() {
        return warmupCancelOnMove;
    }

    public void setWarmupCancelOnMove(boolean warmupCancelOnMove) {
        this.warmupCancelOnMove = warmupCancelOnMove;
    }

    public boolean doesWarmupCancelOnDamage() {
        return warmupCancelOnDamage;
    }

    public void setWarmupCancelOnDamage(boolean warmupCancelOnDamage) {
        this.warmupCancelOnDamage = warmupCancelOnDamage;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public Date getWarmup(Player p1) {
        if (p1 != null && warmups.containsKey(p1)) {
            return warmups.get(p1);
        }

        return null;
    }

    public void setWarmup(Player p1, Date d1) {
        this.warmups.put(p1, d1);
    }

    public void removeWarmup(Player p1) {
        this.warmups.remove(p1);
    }

    public Date getCooldown(Player p1) {
        if (p1 != null && cooldowns.containsKey(p1)) {
            return cooldowns.get(p1);
        }

        return null;
    }

    public void setCooldown(Player p1, Date d1) {
        this.cooldowns.put(p1, d1);
    }

    public Integer getBalanceMin() {
        return balanceMin;
    }

    public void setBalanceMin(Integer balanceMin) {
        this.balanceMin = balanceMin;
    }

    public Integer getBalanceMax() {
        return balanceMax;
    }

    public void setBalanceMax(Integer balanceMax) {
        this.balanceMax = balanceMax;
    }

    public boolean doesAllowBack() {
        return allowBack;
    }

    public void setAllowBack(boolean allowBack) {
        this.allowBack = allowBack;
    }

    public List<String> getShortcuts() {
        return this.shortcuts;
    }

    public void setShortcuts(List<String> shortcuts) {
        this.shortcuts = shortcuts;
    }

    public void addShortcut(String shortcut) {
        this.shortcuts.add(shortcut);
    }

    public void removeShortcut(String shortcut) {
        this.shortcuts.remove(shortcut);
    }
}
