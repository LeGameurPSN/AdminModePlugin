package com.gamingmesh.jobs.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Stub minimal de JobsExpGainEvent pour satisfaire VeinMiner qui cherche
 * com/gamingmesh/jobs/api/JobsExpGainEvent au chargement.
 *
 * Le plugin Jobs d'EvoluMC (v1.0.10, home-made) n'expose pas cette classe,
 * ce qui provoque l'erreur :
 *   [VeinMiner] Failed to register events for class
 *   wtf.choco.veinminer.listener.JobsIntegrationListener
 *   because com/gamingmesh/jobs/api/JobsExpGainEvent does not exist.
 *
 * Cette classe-stub est compilée dans AdminMode.jar et placée dans le
 * classpath avant VeinMiner grâce à l'ordre de chargement Paper.
 * Elle ne fait rien : le listener de VeinMiner s'enregistre sans crasher
 * et continuera d'appeler fire() sur cet event, qui sera ignoré.
 *
 * À retirer si le plugin Jobs est un jour remplacé par le vrai Jobs
 * (com.gamingmesh.jobs) qui fournit la classe réelle.
 */
public class JobsExpGainEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
