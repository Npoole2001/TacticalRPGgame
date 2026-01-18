package sh.vibecraft.hexcontrol.orchestrator;

import sh.vibecraft.hexcontrol.agent.Agent;
import sh.vibecraft.hexcontrol.agent.AgentStatus;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.ui.AlertSystem;

import java.util.*;

/**
 * Global controls for managing all agents at once.
 * Per spec: "Global pause / resume all agents (hotkey P)"
 */
public class GlobalControls {

    private boolean allPaused = false;
    private final Map<String, AgentStatus> pausedStates;
    private AlertSystem alertSystem;

    // Pause/resume callbacks
    private Runnable onPauseAll;
    private Runnable onResumeAll;

    public GlobalControls() {
        this.pausedStates = new HashMap<>();
    }

    public void setAlertSystem(AlertSystem alertSystem) {
        this.alertSystem = alertSystem;
    }

    public void setOnPauseAll(Runnable callback) {
        this.onPauseAll = callback;
    }

    public void setOnResumeAll(Runnable callback) {
        this.onResumeAll = callback;
    }

    /**
     * Toggle pause state for all agents.
     */
    public void togglePauseAll(HexGrid hexGrid) {
        if (allPaused) {
            resumeAll(hexGrid);
        } else {
            pauseAll(hexGrid);
        }
    }

    /**
     * Pause all running agents.
     */
    public void pauseAll(HexGrid hexGrid) {
        if (allPaused) return;

        pausedStates.clear();
        int pausedCount = 0;

        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && agent.getStatus() == AgentStatus.RUNNING) {
                pausedStates.put(agent.getId(), agent.getStatus());
                agent.setStatus(AgentStatus.PAUSED);
                pausedCount++;
            }
        }

        allPaused = true;

        if (alertSystem != null) {
            alertSystem.warning("All Agents Paused", pausedCount + " agent(s) paused");
        }

        if (onPauseAll != null) {
            onPauseAll.run();
        }

        System.out.println("[GlobalControls] Paused " + pausedCount + " agents");
    }

    /**
     * Resume all previously paused agents.
     */
    public void resumeAll(HexGrid hexGrid) {
        if (!allPaused) return;

        int resumedCount = 0;

        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && pausedStates.containsKey(agent.getId())) {
                AgentStatus previousStatus = pausedStates.get(agent.getId());
                agent.setStatus(previousStatus);
                resumedCount++;
            }
        }

        pausedStates.clear();
        allPaused = false;

        if (alertSystem != null) {
            alertSystem.success("All Agents Resumed", resumedCount + " agent(s) resumed");
        }

        if (onResumeAll != null) {
            onResumeAll.run();
        }

        System.out.println("[GlobalControls] Resumed " + resumedCount + " agents");
    }

    /**
     * Pause a specific agent.
     */
    public void pauseAgent(Agent agent) {
        if (agent == null || agent.getStatus() != AgentStatus.RUNNING) return;

        agent.setStatus(AgentStatus.PAUSED);

        if (alertSystem != null) {
            alertSystem.info("Agent Paused", agent.getName());
        }
    }

    /**
     * Resume a specific agent.
     */
    public void resumeAgent(Agent agent) {
        if (agent == null || agent.getStatus() != AgentStatus.PAUSED) return;

        agent.setStatus(AgentStatus.RUNNING);

        if (alertSystem != null) {
            alertSystem.info("Agent Resumed", agent.getName());
        }
    }

    /**
     * Stop all agents (cannot be resumed with resumeAll).
     */
    public void stopAll(HexGrid hexGrid) {
        int stoppedCount = 0;

        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null &&
                (agent.getStatus() == AgentStatus.RUNNING ||
                 agent.getStatus() == AgentStatus.PAUSED ||
                 agent.getStatus() == AgentStatus.BLOCKED)) {
                agent.setStatus(AgentStatus.IDLE);
                stoppedCount++;
            }
        }

        pausedStates.clear();
        allPaused = false;

        if (alertSystem != null) {
            alertSystem.warning("All Agents Stopped", stoppedCount + " agent(s) stopped");
        }

        System.out.println("[GlobalControls] Stopped " + stoppedCount + " agents");
    }

    /**
     * Get count of currently running agents.
     */
    public int getRunningCount(HexGrid hexGrid) {
        int count = 0;
        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && agent.getStatus() == AgentStatus.RUNNING) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get count of paused agents.
     */
    public int getPausedCount(HexGrid hexGrid) {
        int count = 0;
        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && agent.getStatus() == AgentStatus.PAUSED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get count of agents with errors.
     */
    public int getErrorCount(HexGrid hexGrid) {
        int count = 0;
        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && agent.getStatus() == AgentStatus.ERROR) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get count of blocked agents.
     */
    public int getBlockedCount(HexGrid hexGrid) {
        int count = 0;
        for (var cell : hexGrid.getCells()) {
            Agent agent = cell.getAgent();
            if (agent != null && agent.getStatus() == AgentStatus.BLOCKED) {
                count++;
            }
        }
        return count;
    }

    public boolean isAllPaused() {
        return allPaused;
    }

    /**
     * Get summary of agent states.
     */
    public String getStatusSummary(HexGrid hexGrid) {
        int running = getRunningCount(hexGrid);
        int paused = getPausedCount(hexGrid);
        int blocked = getBlockedCount(hexGrid);
        int errors = getErrorCount(hexGrid);

        return String.format("Running: %d | Paused: %d | Blocked: %d | Errors: %d",
                running, paused, blocked, errors);
    }
}
