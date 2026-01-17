# HexController — Agent Orchestration on a 3D Hex Grid (Java Desktop)
# CONTEXT.md (hand this file to an agentic builder)

## 0) Purpose
HexController is a private, desktop Java application that lets a user instantiate, monitor, and interact with multiple agentic AI agents via a gamified, Civ-style hex grid rendered in a top-down 3D view. Each hex tile can represent an agent "zone" or an empty slot. Agents can run tasks in parallel, typically against a shared single GitHub repository, with independent goals/branches/worktrees.

Core value: parallel orchestration + situational awareness (what's running, where, and why) with a playful but information-dense UI.

Visual / vibe target: futuristic, minimalist, slightly glitchy, high-contrast, always-dark UI (no light theme).

---

## 1) Product Principles
1. Local-first & private by default
   - No source code is sent to third-party servers unless the user explicitly configures a provider and consents per action.
   - Telemetry OFF by default; if ever added, opt-in and minimal.

2. Security is a first-class feature
   - Strong API key hygiene, encryption at rest, least privilege.
   - "Privacy Mode / Streaming Mode" concept: hide sensitive paths, usernames, repo identifiers, and redact exports.

3. Fast, legible operator UI
   - At a glance: which agents are idle/running/stuck, what they're doing, and where they're blocked.

4. Delight without complexity
   - Visual flair comes from simple primitives (squares/particles, rings, glitch lines), not heavy 3D models.

---

## 2) Core User Experience

### 2.1 Launch Flow
- App launches → brief loading screen (placeholder OK now; animated later).
- Loads persisted state from previous session:
  - Grid layout (size/rings)
  - Tile-to-agent assignments
  - Agent metadata (names, colors, roles, last task, last status)
  - UI layout (panels open/closed, camera angle, keybinds)
  - Provider settings (without exposing secrets)
  - Context sheet / pinned objectives state

### 2.2 Main Scene: 3D Hex Grid
**Theme**
- Background: pure/near black.
- Grid lines: crisp white (thin; subtle bloom optional).
- Empty tiles: black with white outline.
- Occupied tiles: outline + subtle fill tinted to agent color.
- Futuristic minimalism: clean type, restrained color, small glitch accents.

**Camera**
- Middle mouse drag rotates the grid in 3D.
- Grid remains face-up:
  - Minimum pitch angle: 20° above horizon (never fully side-on).
- Zoom: mouse wheel.
- Pan: right mouse drag and/or WASD (configurable).
- Optional "snap back" dampening if rotation would exceed constraints.

**Tile interaction**
- Left click a tile:
  - Empty: open Create/Assign Agent panel.
  - Occupied: open Agent Detail / Chat / Task panel.
- Hover:
  - Soft highlight (white outline glow) + tooltip (agent name/status/task).
- Selection:
  - Thin selection ring or reticle (white) with subtle glitch flicker.

### 2.3 Activity Effects (Simple Shape + Cheap GPU Effects)
No heavy models. Use instanced quads/sprites/lines.

Effects to implement (cheap and scalable):
- Square "smoke" particles rising from occupied tiles.
- Activity rings around tiles:
  - Idle: none or faint slow pulse
  - Running: steady pulse + ring rotation
  - High activity: tighter pulse + occasional glitch spikes
  - Blocked: ring stutters / intermittent red or amber accent (keep minimal)
  - Error: ring becomes segmented + slow flashing (limited color; red accents allowed)
- Optional "scanline" sweep across grid when global actions occur (e.g., start all agents).
- Optional subtle "glitch shimmer" on active tiles (low amplitude; not distracting).

### 2.4 Biomes / Tile Styling (Role-Based Visual Encoding)
Introduce "visual biomes" (patterns, not models) to convey an agent's role at a glance:
- Builder: diagonal micro-lines
- Tester: dot-grid pattern
- Reviewer: thin crosshatch
- Researcher: subtle waveform line
- Ops/Integrator: circuit-like pattern

Implementation approach:
- Use procedural patterns or small repeating textures; keep them subtle.
- Patterns overlay the tinted tile fill at low opacity.

### 2.5 HUD: Top-Right Stats Panel (Always Visible, Collapsible)
A compact operator dashboard:
- Total agents
- Running / Idle / Sleeping / Blocked / Error
- Queue depth (if tasks queued)
- Provider status (connected/disconnected)
- Git status summary (dirty worktrees, pending PRs, failing checks)
- Token/cost metrics (if available; otherwise placeholders)

Design notes:
- Minimal, readable, high contrast.
- Collapsible to an icon.
- Supports "pinning" key numbers.

### 2.6 Mini-Map (Recommended)
A small 2D minimap of the grid:
- Shows tile occupancy as colored dots/hexes.
- Shows camera direction/viewport.
- Clicking a minimap location recenters the camera.
- Toggleable; can be pinned.

### 2.7 Agent Search (Recommended)
Fast jump-to-agent:
- Search by agent name, role, status, tag, task keyword.
- Results list shows status + current objective.
- Selecting result focuses camera and opens agent panel.

### 2.8 Pinned Objectives + Global Context Sheet (Recommended)
Pinned Objectives:
- A top bar or side panel listing global goals ("epics") and who is working on what.
- Each objective has:
  - owner agent(s)
  - priority
  - status
  - notes
  - links (issues/PRs/files)

Global Context Sheet:
- A structured, always-available page summarizing:
  - project goal
  - repo location
  - current epics
  - constraints/policies
  - "definition of done"
  - current blockers
- Designed so the user can keep the system aligned and quickly onboard new agents.

---

## 3) Agent Model & Orchestration

### 3.1 Core Concepts
Agent:
- id, name, color, role, tags
- tile position
- status (Idle/Running/Sleeping/Blocked/Error)
- current task + objective
- activity telemetry (events, logs)
- workspace binding (repo path + branch/worktree)
- policy profile (allowed tools/dirs, dry-run requirement, budgets)

Task:
- id, title
- prompt/instructions
- inputs (files, issue link, context)
- expected artifacts (PR, patch, notes, tests)
- state (Queued/Running/Needs Input/Done/Failed)
- dependencies (optional)

Zone:
- A tile (or region) where an agent "lives."
- Optional multi-tile zones later; start with single tile per agent.

### 3.2 Parallel Work on One GitHub Repo
Prevent collisions by default:
- One git worktree per agent, each on its own branch.
- UI support:
  - Create worktree/branch
  - View diffs
  - Commit staging
  - Open PR (optional in early versions; can export patch)

### 3.3 Agent Detail Panel (Per Tile)
When clicking an occupied tile:
- Chat / conversation view
- Task controls:
  - Start task
  - Pause/sleep
  - Stop/cancel
  - Resume
- "Needs input" flow:
  - agent asks user a focused question
  - user responds
  - agent continues
- Context snapshot:
  - active files
  - recent diffs
  - TODO list
- Events timeline:
  - file changes detected
  - build/test runs
  - provider calls
  - errors/warnings
- Quick actions:
  - "Run tests"
  - "Format code"
  - "Open diff"
- "Generate PR description"

---

## 4) Testing
Run tests with:
- `./gradlew test`

If the Gradle wrapper fails with `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`, ensure the wrapper JAR is present at `gradle/wrapper/gradle-wrapper.jar` or regenerate it via a local Gradle install (`gradle wrapper`). This repository currently fails to run tests because the wrapper JAR is missing.

---

## 5) Known Issues (Keep Updated)
- Gradle wrapper JAR missing, so `./gradlew test` cannot run without regenerating or adding the wrapper JAR.

### 3.4 Dry-Run Mode (Recommended Safety Default)
Dry-run mode is a first-class workflow:
- Agents can propose diffs/patches and plans without writing to disk.
- User approves "apply changes" explicitly.
- Per-agent and global toggles.
- Policy can force dry-run for specific directories (e.g., deployment scripts).

---

## 4) System Architecture (Java Desktop)

### 4.1 High-Level Modules
1. Renderer / Scene
   - Hex grid generation, camera controls, picking/raycast
   - Particle + ring + glitch effects (instanced rendering)
   - UI overlay integration

2. UI Layer
   - Panels: Stats HUD, Mini-map, Agent Search, Agent Detail, Global Context Sheet, Global Settings, Git view
   - Input/keybind system (rebindable)

3. Orchestrator Core
   - Agent lifecycle manager
   - Task scheduler (parallel execution, throttling, budgets)
   - Event bus (agent events → UI)

4. Provider Layer (LLM + Tools)
   - Provider abstraction (OpenAI/Anthropic/local/etc.)
   - Tool execution sandbox (filesystem, shell, git operations)
   - Rate limiting, retries, circuit breakers
   - Token/cost accounting (if provider supports)

5. GitHub/Repo Integration
   - Repo discovery
   - Worktree management per agent
   - Diff/patch extraction
   - PR automation (optional; can be manual initially)

6. Persistence
   - App state store (grid config, agents, UI layout, objectives)
   - Secure secrets store
   - Audit/event logs

### 4.2 Threading / Responsiveness
- UI thread must never block on agent work.
- Agent execution runs in background threads/processes.
- Use a message/event queue to marshal updates to UI safely.
- Renderer maintains consistent FPS even with multiple agents active.

---

## 5) Persistence Requirements
Persist on:
- Clean exit
- Periodic autosave (30–60 seconds)
- Important transitions (agent start/stop, task created/completed, objective updates)

Persist (non-secret):
- Grid size/rings
- Camera pose (yaw/pitch/zoom)
- Tile assignments
- Agent metadata (id/name/color/role/tags/status summaries)
- Task history summaries (not full prompts if privacy mode enabled)
- UI layout and keybinds
- Global context sheet + pinned objectives

Suggested formats:
- Non-secret app state: JSON (versioned schema) or SQLite
- Event log: append-only JSONL + optional SQLite indexing
- Secrets: OS keychain/credential vault preferred

---

## 6) Security Requirements (Non-Negotiable)

### 6.1 Secrets
- Never store secrets in plaintext.
- Prefer OS-native secure storage:
  - macOS Keychain, Windows Credential Manager, Linux Secret Service/libsecret.
- If OS keychain integration isn't feasible initially:
  - Encrypt secrets at rest with passphrase-derived key using a modern KDF (Argon2id/scrypt) + random salt.
  - Use authenticated encryption (AES-GCM or ChaCha20-Poly1305).
  - Never log secrets (including in exception traces).

### 6.2 Threat Model (Minimum)
Assume:
- Local machine could have other processes.
- Logs could be shared for debugging.

Controls:
- Privacy Mode / Streaming Mode:
  - hides usernames, absolute paths, repo identifiers
  - redacts exports (logs, screenshots, reports)
- Redaction layer for UI and log export.
- Explicit consent prompts before:
  - sending code to external providers
  - connecting to non-configured endpoints
  - running system-modifying shell commands outside repo sandbox

### 6.3 Least Privilege Defaults
- Tooling constrained to configured workspace directory.
- Command allowlist (git/build/test/format); deny dangerous commands by default.
- Network egress:
  - only configured LLM endpoints (+ GitHub if enabled)
  - settings page lists all endpoints.

### 6.4 Auditability
- Maintain an audit log of:
  - agent tool execution events
  - file changes (paths; optional diffs)
  - provider calls (provider name + timestamp; no sensitive payloads)
- Provide "Export sanitized report" function.

---

## 7) Functional Requirements (MVP → V1)

### 7.1 MVP (ship first)
- Render hex grid in 3D with MMB rotation and min pitch 20°
- Click tiles:
  - create agent
  - open agent panel
- Basic statuses (Idle/Running/Blocked/Error/Sleeping)
- Simple square particle animation for "Running"
- Basic activity ring for selection + running pulse
- Persistence (layout + agents + camera)
- Secure secret storage (at minimum: encrypted local store)
- Single GitHub repo configured; per-agent worktree branches
- Dry-run mode (at least global toggle + "propose diff" path)

### 7.2 V1 (next)
- Top-right Stats HUD with counts + basic health + budgets
- Task queue + scheduler
- Needs-input flow
- Event timeline per agent
- Git diff preview in agent panel
- Privacy mode (redaction + toggles)
- Keybind editor
- Mini-map
- Agent search
- Pinned objectives + global context sheet
- Biome patterns by role

---

## 8) Recommended Future Features (Keep These in Mind)
Operator:
- Alerts/toasts for needs-input, test failures, PR ready
- Minimap click-to-focus
- Quick "focus mode" for one agent
- Global "pause all agents" and "resume all"

Workflow:
- Task templates (feature/refactor/tests/docs)
- Dependency graph between tasks/agents
- Swarm mode: create N agents for an epic and auto-assign subtasks
- Policy rules: "always run tests before PR," "never modify these dirs," etc.

Visual:
- Scanline sweeps for global actions
- Glitch shimmer on active tiles
- Time-lapse replay of session events (watch grid light up)

Safety:
- Per-agent budget caps (tokens/cost/time)
- Provider throttles per agent
- "No-write" mode (read-only analysis)

Non-goals (early):
- Multiplayer / shared sessions
- Complex physics
- Heavy 3D models/characters
- Auto-merge to main without explicit approval

---

## 9) Open Questions (Decide Early, Don't Block MVP)
- Java rendering stack choice (e.g., jMonkeyEngine vs LWJGL vs libGDX).
- Agent execution model:
  - embedded LLM calls inside app vs supervising external CLI agents/processes
  - if external: define IPC protocol + process supervision
- GitHub automation depth:
  - read-only vs full PR creation and review automation

---

## 10) Acceptance Criteria (Definition of Done for MVP)
- On launch, app restores last grid + agent placements reliably.
- User can rotate/zoom/pan; grid never goes below 20° pitch.
- Tile picking is accurate; agent panel opens correctly.
- Agent tile shows status and visible activity effects when running.
- Secrets are never stored in plaintext; logs are redacted/sanitizable.
- Multiple agents can operate in parallel on the same repo using isolated worktrees/branches.
- UI remains responsive during agent execution (no UI thread blocking).

---
# End of CONTEXT.md
