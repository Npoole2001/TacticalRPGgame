package sh.vibecraft.hexcontrol;

import sh.vibecraft.hexcontrol.core.Engine;

/**
 * HexControl - A 3D hex-based interface for managing multiple AI agents.
 *
 * This application provides a visual interface where each hexagon represents
 * an AI agent (Claude, ChatGPT, etc.) that can be interacted with.
 */
public class HexControlApp {

    public static void main(String[] args) {
        Engine engine = new Engine();
        engine.run();
    }
}
