package nu.nerd.tpcontrol;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class GhostCommand extends Command {
    public GhostCommand(String name) {
        super(name);
    }

    @Override
    public boolean execute(CommandSender cs, String string, String[] strings) {
        return false;
    }
}
