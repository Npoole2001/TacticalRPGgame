# HexControl

A 3D hex-based interface for orchestrating multiple AI agents (Claude, ChatGPT, Gemini, etc.) from a unified visual control panel. Think Civ-style hex grid meets AI agent management.

## Overview

HexControl provides a gamified 3D interface where each hexagon represents an AI agent "zone." Agents can run tasks in parallel against a shared GitHub repository, each with independent goals, branches, and worktrees.

**Core Value**: Parallel orchestration + situational awareness with a playful but information-dense UI.

**Visual Theme**: Futuristic, minimalist, slightly glitchy, high-contrast, always-dark.

## Features

### MVP (Current)

- **3D Hex Grid**: Orbital camera with MMB rotation, RMB/WASD pan, scroll zoom
- **Camera Constraints**: Minimum 20° pitch angle (never fully side-on)
- **Agent Statuses**: Idle, Running, Sleeping, Blocked, Error - with visual effects
- **Activity Effects**:
  - Square smoke particles rising from active agents
  - Activity rings with status-based animations (pulse, rotation, glitch)
- **Stats HUD**: Top-right collapsible panel showing agent counts and system status
- **Persistence**: JSON state with 45-second autosave, backup on save
- **Security**:
  - Encrypted secret storage (AES-GCM with PBKDF2 key derivation)
  - Audit logging with automatic PII redaction
  - Privacy mode for redacting sensitive paths

### Agent Model

- **Roles**: Builder, Tester, Reviewer, Researcher, Ops/Integrator
- **Status-based visuals**: Different ring animations and particle effects per status
- **Workspace binding**: Repo path, branch, worktree per agent
- **Policy profiles**: Dry-run mode, allowed directories, token budgets

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
| Right Click + Drag | Pan camera |
| Middle Click + Drag | Rotate camera (orbit) |
| Scroll Wheel | Zoom in/out |
| WASD | Pan camera |
| 1-9 Keys | Quick-select agent by number |
| ESC | Close menu / Exit |

## Architecture

```
src/main/java/sh/vibecraft/hexcontrol/
├── HexControlApp.java              # Entry point
├── core/
│   └── Engine.java                 # Main loop, system integration
├── render/
│   ├── Camera.java                 # Orbital camera with constraints
│   ├── Renderer.java               # OpenGL shader-based rendering
│   ├── HexGrid.java                # Hex grid with effects integration
│   └── effects/
│       ├── ParticleSystem.java     # Instanced square particles
│       └── ActivityRing.java       # Status-based ring effects
├── input/
│   └── InputHandler.java           # MMB rotate, RMB/WASD pan
├── ui/
│   ├── MenuSystem.java             # Agent interaction panels
│   └── StatsHUD.java               # Top-right stats dashboard
├── agent/
│   ├── AIAgent.java                # Full agent model
│   ├── AgentType.java              # Claude, ChatGPT, Gemini, etc.
│   ├── AgentRole.java              # Builder, Tester, Reviewer, etc.
│   └── AgentStatus.java            # Idle, Running, Sleeping, etc.
├── persistence/
│   ├── AppState.java               # Serializable state model
│   └── StateManager.java           # Save/load with autosave
└── security/
    ├── SecretStore.java            # Encrypted API key storage
    └── AuditLog.java               # Security event logging
```

## Data Storage

State is persisted to `~/.hexcontrol/`:

- `appstate.json` - Grid layout, agents, camera, UI settings
- `appstate.backup.json` - Previous state backup
- `secrets.enc` - Encrypted API keys (AES-GCM)
- `logs/audit.jsonl` - Append-only audit log

## Security

Per spec, security is a first-class feature:

- **Secrets**: Never stored in plaintext. Uses AES-256-GCM with PBKDF2 (310k iterations)
- **Audit**: Tool executions, file changes, provider calls logged with timestamps
- **Redaction**: Automatic removal of API keys, paths, usernames from logs
- **Privacy Mode**: Optional redaction of paths and identifiers in UI/exports

## Roadmap

### V1 (Next)

- [ ] Text rendering (NanoVG integration)
- [ ] Mini-map with click-to-focus
- [ ] Agent search by name/role/status
- [ ] Task queue and scheduler
- [ ] Git diff preview in agent panel
- [ ] Needs-input flow for blocked agents
- [ ] Biome patterns by role (diagonal, dots, crosshatch, etc.)
- [ ] Pinned objectives panel

### Future

- [ ] Chat window integration
- [ ] API connections to AI providers
- [ ] PR automation via gh CLI
- [ ] Global "pause all" / "resume all"
- [ ] Time-lapse replay of session events

## License

Private tool - not for distribution.
