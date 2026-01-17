# HexControl

A 3D hex-based interface for managing multiple AI agents (Claude, ChatGPT, Gemini, etc.) from a unified visual control panel.

## Overview

HexControl provides a game-like 3D interface where each hexagon represents an AI agent that you can interact with. Click on hexagons to open chat windows, configure agents, and manage multiple AI conversations simultaneously.

## Features

- **3D Hex Grid**: Visual representation of AI agents as colored hexagons
- **Camera Controls**: Pan (middle-click drag) and zoom (scroll wheel) to navigate
- **Agent Selection**: Click hexes to select and interact with agents
- **Agent Menu**: Configure, chat, and manage each AI agent
- **Multiple Agent Types**: Support for Claude, ChatGPT, Gemini, Copilot, Llama, Mistral

## Requirements

- Java 17+
- OpenGL 3.3+

## Running

```bash
./gradlew run
```

## Controls

| Input | Action |
|-------|--------|
| Left Click | Select hex / Open menu |
| Right Click | Close menu |
| Middle Click + Drag | Pan camera |
| Scroll Wheel | Zoom in/out |
| 1-9 Keys | Quick-select agent by number |
| ESC | Close menu / Exit |

## Project Structure

```
src/main/java/sh/vibecraft/hexcontrol/
├── HexControlApp.java      # Entry point
├── core/
│   └── Engine.java         # Main engine loop
├── render/
│   ├── Camera.java         # 3D camera with pan/zoom
│   ├── Renderer.java       # OpenGL shader rendering
│   └── HexGrid.java        # Hexagon grid management
├── input/
│   └── InputHandler.java   # Mouse/keyboard input
├── ui/
│   └── MenuSystem.java     # Agent interaction menus
└── agent/
    ├── AIAgent.java        # Agent data model
    ├── AgentType.java      # Supported AI types
    └── AgentStatus.java    # Agent status enum
```

## Next Steps

- [ ] Text rendering for labels and menus
- [ ] Chat window integration
- [ ] API connections to AI services
- [ ] Agent configuration persistence
- [ ] Multi-window support
