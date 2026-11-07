package fr.evolumc.adminmode.managers;

import java.util.*;

/**
 * Option 9 — Préférences HUD par admin.
 * Chaque élément du HUD peut être activé/désactivé individuellement.
 */
public class HudPreferenceManager {

    public enum HudElement {
        TPS("TPS", "§7TPS"),
        ONLINE("En ligne", "§7En ligne"),
        FROZEN("Gelés", "§7Gelés"),
        SESSION("Session", "§7Session"),
        VANISH("Vanish", "§7Vanish");

        private final String label;
        private final String display;

        HudElement(String label, String display) {
            this.label = label;
            this.display = display;
        }

        public String getLabel() { return label; }
        public String getDisplay() { return display; }
    }

    // UUID admin → set d'éléments désactivés (tout activé par défaut)
    private final Map<UUID, Set<HudElement>> disabled = new HashMap<>();

    public boolean isEnabled(UUID uuid, HudElement element) {
        Set<HudElement> set = disabled.get(uuid);
        return set == null || !set.contains(element);
    }

    public void toggle(UUID uuid, HudElement element) {
        Set<HudElement> set = disabled.computeIfAbsent(uuid, k -> new HashSet<>());
        if (set.contains(element)) set.remove(element);
        else set.add(element);
    }

    public void enable(UUID uuid, HudElement element) {
        Set<HudElement> set = disabled.get(uuid);
        if (set != null) set.remove(element);
    }

    public void disable(UUID uuid, HudElement element) {
        disabled.computeIfAbsent(uuid, k -> new HashSet<>()).add(element);
    }
}
