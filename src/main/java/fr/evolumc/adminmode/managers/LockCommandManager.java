package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;

import java.util.HashSet;
import java.util.Set;

/**
 * Bloque temporairement une commande pour tous les joueurs non-OP.
 */
public class LockCommandManager {

    private final AdminMode plugin;
    private final Set<String> lockedCommands = new HashSet<>();

    public LockCommandManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public boolean lock(String command) {
        return lockedCommands.add(command.toLowerCase());
    }

    public boolean unlock(String command) {
        return lockedCommands.remove(command.toLowerCase());
    }

    public boolean isLocked(String command) {
        return lockedCommands.contains(command.toLowerCase());
    }

    public Set<String> getLockedCommands() {
        return java.util.Collections.unmodifiableSet(lockedCommands);
    }
}
