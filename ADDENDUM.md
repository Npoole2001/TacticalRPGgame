# HexController — Iteration Addendum (Multi-Agent Collaboration, Guardrails, Roles)
# ADDENDUM.md (append to CONTEXT.md or keep as a separate file for the builder agent)

## 1) Multi-Agent Collaboration Guardrails (Prevent Collisions)

### 1.1 Primary Goal
Multiple agents must work on the same codebase concurrently without:
- overwriting each other's changes,
- diverging architectural intent,
- producing large, destabilizing refactors in a single run.

The app should enforce "small, reviewable steps" and make architecture visible to both the user and the agents.

### 1.2 Work Isolation (Required)
Adopt hard isolation boundaries:
- Each agent operates in its own git worktree + branch by default.
- Agents are prohibited from directly committing to / pushing main.
- Merge is explicit and user-controlled (or PM-controlled if that workflow is enabled).

### 1.3 Change Budgeting (Required)
Enforce bounded change sets per "agent run":
- Limit the size of changes an agent can propose/apply in one session:
  - max files changed (e.g., 5–20 configurable)
  - max lines changed (e.g., 200–800 configurable)
  - max runtime per task (e.g., 5–20 minutes configurable)
- If a change exceeds a budget:
  - agent must stop and produce a plan broken into smaller tasks ("slices")
  - agent must request approval to proceed.

Rationale: prevent "half the app in one session" failures and reduce integration risk.

### 1.4 Staged Integration Pipeline (Recommended Default)
Pipeline states per task:
1) Plan → 2) Propose diff (dry-run) → 3) Apply diff (optional) → 4) Run checks/tests → 5) Review → 6) Merge/PR

Default behavior:
- Dry-run / propose-first is ON by default for new agents.
- "Apply" requires user approval (or delegated approval rules).

### 1.5 Conflict Detection & Resolution (Required)
Add repo-level coordination:
- Track per-agent touched files + modules during an active task.
- If a second agent tries to modify a locked/touched area:
  - warn and block by default,
  - offer alternatives:
    - rebase onto latest integration branch
    - pick a different module
    - wait/queue
    - request explicit override

Optional enhancement:
- "Module Locks" managed by PM agent or the user.

---

## 2) Architecture / System Map (The "Constant Chart")

### 2.1 Objective
Provide a continuously updated "Architecture Map" so agents share the same mental model:
- modules and boundaries,
- key classes/interfaces,
- dependencies and data flow,
- owner agent(s) per area.

This reduces agents making incompatible changes.

### 2.2 What the Map Contains (MVP Scope)
- Module graph (nodes = packages/modules; edges = dependencies/import relationships)
- Key entry points:
  - renderer loop
  - orchestrator core
  - persistence layer
  - provider layer
  - git integration
- "Ownership tags":
  - which agent is responsible for a module during a time window
- Build/test commands and expected runtime

### 2.3 How the Map is Produced (Implementation Guidance)
- Static scan of code:
  - parse package structure + imports
  - optional: lightweight AST parsing for class relationships
- Output to:
  - a JSON snapshot (machine-readable for agents)
  - a visual view in-app:
    - node-link graph ("web/chart") or layered diagram
    - filtered views by subsystem
- Update cadence:
  - regenerate after merges / significant diffs
  - or every N minutes while agents are running

### 2.4 Agent Consumption Rules (Required)
- Agents must read the latest Architecture Map before starting a coding task.
- Agents must declare "intended touch surface" (modules/files) before editing.
- Agents must update the map (or request map update) after proposing changes.

---

## 3) Role-Based Agents (Team Simulation)

### 3.1 Feature Decision
Yes, a "Product Manager (PM) agent" is a good feature if implemented with guardrails:
- PM agent should coordinate work, not directly perform large code changes.
- PM agent focuses on decomposition, acceptance criteria, sequencing, and conflict avoidance.

If this becomes overly complex, fallback:
- user manually plays PM via pinned objectives + task templates.
But design it so PM can be enabled/disabled.

### 3.2 Standard Roles (Initial Set)
Allow assigning a role per agent, which changes prompts, tools, permissions, and UI affordances.

Recommended roles:
- Product Manager (PM)
  - decomposes features into tasks
  - assigns tasks to coder agents
  - enforces budgets and sequencing
  - maintains Architecture Map + Global Context Sheet
- Tech Lead / Architect
  - defines module boundaries and patterns
  - reviews risky changes
  - updates architecture decisions log
- Backend Coder
  - orchestrator core, provider layer, persistence, git integration
- Frontend/UI Coder
  - panels, HUD, minimap, agent search, interactions
- Graphics/FX Coder
  - hex rendering, picking, particles, rings, glitch effects, biome patterns
- QA / Test Engineer
  - writes tests, builds CI scripts, validates scenarios, regression checks
- Docs / Release Engineer (optional)
  - maintains README, user guide, changelog, packaging notes

### 3.3 Role Controls in the App (Required)
Each agent has configurable:
- Role (dropdown)
- Tool permissions (e.g., can run shell? can write files? can open PR?)
- Directories allowed/denied
- Default mode: dry-run vs can-apply
- Budget caps: max lines/files/time

UI behavior changes with role:
- PM agent tile UI emphasizes objectives, task lists, assignments, dependencies.
- Coder tiles emphasize diffs, file activity, tests.
- FX tile emphasizes visuals/performance metrics.

### 3.4 Delegation Workflow (PM Agent)
User asks PM for a feature → PM outputs:
- spec + acceptance criteria
- architecture impact assessment
- task breakdown (small slices)
- assignments to coder agents (with module ownership)
- integration plan (merge order, test gates)

PM does not merge automatically by default.
PM can "request merge" when checks pass and user approval is granted (configurable).

---

## 4) Session Persistence & "Always Restore" Guarantee

### 4.1 Persistence Must Be Durable (Required)
When the app closes and reopens:
- grid + camera + UI layout restored
- agent roster restored (names/roles/colors/tags)
- pinned objectives + global context sheet restored
- architecture map snapshot restored (and revalidated)
- task history restored (at least summaries + timestamps)
- event log restored
- per-agent worktree locations re-linked (or user prompted to relink if missing)

### 4.2 Autosave Strategy (Required)
- Autosave at fixed intervals (30–60s) + on key transitions:
  - agent start/stop
  - task created/completed
  - objective edits
  - settings changes
- On crash recovery:
  - detect last autosave and restore
  - mark "Recovered Session" banner with timestamp

### 4.3 State Versioning (Required)
- Persisted state is versioned (schema version).
- Migration logic exists for forward upgrades.
- "Safe mode" load if migration fails (load minimal UI, allow export of old data).

---

## 5) Secure API Key Storage (Re-Emphasized)

### 5.1 Requirements
- Secrets must never be stored in plaintext.
- Prefer OS secure storage:
  - macOS Keychain / Windows Credential Manager / Linux Secret Service
- If OS store not available:
  - encrypted local secret vault:
    - KDF: Argon2id or scrypt (configurable parameters)
    - encryption: AES-GCM or ChaCha20-Poly1305
  - secrets wiped from memory when not needed (best effort)

### 5.2 UX Requirements
- Provider settings page:
  - add/remove keys
  - test connection
  - show last-used timestamp (no key reveal)
- Optional "unlock vault on startup" passphrase flow if not using OS keychain.
- Privacy mode redacts provider identity if desired.

---

## 6) Acceptance Criteria (Addendum)
- Agents cannot modify the same locked/touched files simultaneously without explicit override.
- Architecture Map is visible in-app and updated after meaningful changes.
- Agents read the map before tasks and declare intended touch surface.
- PM role can decompose and assign tasks; coders execute in bounded slices.
- Closing and reopening restores the full session state reliably.
- API keys are stored securely and are never exposed in logs or exports.

# End of ADDENDUM.md
