package sh.vibecraft.hexcontrol.render;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.AIAgent;
import sh.vibecraft.hexcontrol.agent.AgentType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a grid of hexagons, each representing an AI agent slot.
 */
public class HexGrid {

    private List<HexCell> cells;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;

    // Hex grid spacing (flat-topped hexagons)
    private static final float HEX_SIZE = 1.0f;
    private static final float HEX_WIDTH = HEX_SIZE * 2.0f;
    private static final float HEX_HEIGHT = (float) (Math.sqrt(3.0) * HEX_SIZE);
    private static final float HORIZONTAL_SPACING = HEX_WIDTH * 0.75f;
    private static final float VERTICAL_SPACING = HEX_HEIGHT;

    public HexGrid(int count) {
        cells = new ArrayList<>();
        generateGrid(count);
    }

    private void generateGrid(int count) {
        // Generate hexes in a spiral pattern from center
        // Using axial coordinates (q, r) for hex grid

        // Center hex
        addHex(0, 0, createDefaultAgent(0));

        int ring = 1;
        int added = 1;

        // Axial direction vectors for the 6 directions
        int[][] directions = {
            {1, 0}, {1, -1}, {0, -1},
            {-1, 0}, {-1, 1}, {0, 1}
        };

        while (added < count) {
            int q = 0;
            int r = -ring;

            for (int dir = 0; dir < 6 && added < count; dir++) {
                for (int step = 0; step < ring && added < count; step++) {
                    addHex(q, r, createDefaultAgent(added));
                    added++;

                    q += directions[dir][0];
                    r += directions[dir][1];
                }
            }
            ring++;
        }
    }

    private void addHex(int q, int r, AIAgent agent) {
        // Convert axial to world coordinates
        float x = HEX_SIZE * (3.0f / 2.0f * q);
        float z = HEX_SIZE * ((float) Math.sqrt(3.0) * (r + q / 2.0f));

        HexCell cell = new HexCell(q, r, x, z, agent);
        cells.add(cell);
    }

    private AIAgent createDefaultAgent(int index) {
        // Create sample agents for demonstration
        AgentType[] types = AgentType.values();
        AgentType type = types[index % types.length];

        return new AIAgent(
            "Agent " + (index + 1),
            type,
            type.getDefaultColor()
        );
    }

    public void update(float deltaTime) {
        for (HexCell cell : cells) {
            cell.update(deltaTime);
        }
    }

    public void render(Renderer renderer) {
        for (int i = 0; i < cells.size(); i++) {
            HexCell cell = cells.get(i);
            float highlight = 0.0f;

            if (i == selectedIndex) {
                highlight = 1.0f;
            } else if (i == hoveredIndex) {
                highlight = 0.5f;
            }

            renderer.drawHex(
                cell.getWorldX(),
                cell.getWorldZ(),
                HEX_SIZE * 0.95f, // Slight gap between hexes
                cell.getColor(),
                highlight
            );
        }
    }

    /**
     * Find which hex is at the given world position.
     * Returns the index of the hex, or -1 if none.
     */
    public int getHexAt(float worldX, float worldZ) {
        for (int i = 0; i < cells.size(); i++) {
            HexCell cell = cells.get(i);
            float dx = worldX - cell.getWorldX();
            float dz = worldZ - cell.getWorldZ();
            float distance = (float) Math.sqrt(dx * dx + dz * dz);

            // Check if within hex radius (using inner radius for better feel)
            if (distance < HEX_SIZE * 0.866f) { // sqrt(3)/2
                return i;
            }
        }
        return -1;
    }

    public void setHovered(int index) {
        this.hoveredIndex = index;
    }

    public void setSelected(int index) {
        this.selectedIndex = index;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public HexCell getCell(int index) {
        if (index >= 0 && index < cells.size()) {
            return cells.get(index);
        }
        return null;
    }

    public List<HexCell> getCells() {
        return cells;
    }

    public void cleanup() {
        // Nothing to clean up for now
    }

    /**
     * Represents a single hexagon cell in the grid.
     */
    public static class HexCell {
        private int q, r; // Axial coordinates
        private float worldX, worldZ; // World position
        private AIAgent agent;

        // Animation state
        private float pulsePhase = 0.0f;

        public HexCell(int q, int r, float worldX, float worldZ, AIAgent agent) {
            this.q = q;
            this.r = r;
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.agent = agent;
            this.pulsePhase = (float) (Math.random() * Math.PI * 2);
        }

        public void update(float deltaTime) {
            pulsePhase += deltaTime * 2.0f;
        }

        public int getQ() { return q; }
        public int getR() { return r; }
        public float getWorldX() { return worldX; }
        public float getWorldZ() { return worldZ; }
        public AIAgent getAgent() { return agent; }

        public Vector3f getColor() {
            // Add subtle pulse to agent color
            float pulse = (float) (1.0f + Math.sin(pulsePhase) * 0.05f);
            Vector3f baseColor = agent.getColor();
            return new Vector3f(
                baseColor.x * pulse,
                baseColor.y * pulse,
                baseColor.z * pulse
            );
        }
    }
}
