package nu.nerd.tpcontrol;

import org.bukkit.command.CommandSender;

/**
 * Create a special exception to bail out of commands and get messages back to the
 * users without having to use C style programming. (Sticking it to Checked exceptions)
 * 
 */
public class FormattedUserException extends RuntimeException {

    /** The message to save */
    private String msg;

    /** Make a new exception with the message */
    public FormattedUserException(String string) {
        this.msg = string;
    }

    /** Get the message back */
    public String toString() {
        return msg;
    }
    
    /** Even better. Just send the message to the sender */
    public void print(CommandSender sender) {
        sender.sendMessage(msg);
    }

    /**
     * Strange thing Eclipse wants.
     */
    private static final long serialVersionUID = 193751687375451800L;
    

}
