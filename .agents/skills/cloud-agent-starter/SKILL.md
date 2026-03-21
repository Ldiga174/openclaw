---
name: cloud-agent-starter
description: Practical setup, run, and test instructions for Cloud agents working on the OpenClaw codebase. Use when onboarding to the repo or whenever you need a quick reference for installing deps, starting the gateway, running tests, or validating changes across different codebase areas.
---

# Cloud Agent Starter

Quick-reference for Cloud agents working in the OpenClaw monorepo.
Read this skill first whenever you start a task; it covers everything you need
to install, run, test, and validate changes without real API keys.

## 1. Environment bootstrap

```bash
# Node 22+ is required (pre-installed via nvm on Cloud VMs)
node -v  # confirm >= 22.16

# Bun (optional but preferred for script execution)
export PATH="$HOME/.bun/bin:$PATH"

# Install all workspace deps
pnpm install
# Expected: warnings about ignored build scripts (@discordjs/opus, etc.) — safe to ignore.
# Do NOT run `pnpm approve-builds`.
```

State directory: `~/.openclaw/` (SQLite, sessions, credentials — auto-created on
first gateway run).

## 2. Key commands cheat sheet

| Task                       | Command                                                                                         |
| -------------------------- | ----------------------------------------------------------------------------------------------- |
| Install deps               | `pnpm install`                                                                                  |
| Lint + format + typecheck  | `pnpm check`                                                                                    |
| Format fix                 | `pnpm format:fix`                                                                               |
| Build (TypeScript)         | `pnpm build`                                                                                    |
| Build (control UI)         | `pnpm ui:build`                                                                                 |
| Unit tests (Cloud VM)      | `OPENCLAW_TEST_PROFILE=low OPENCLAW_TEST_SERIAL_GATEWAY=1 pnpm test`                            |
| Scoped test                | `pnpm test -- path/to/file.test.ts`                                                             |
| Scoped test with filter    | `pnpm test -- path/to/file.test.ts -t "test name pattern"`                                      |
| Gateway (dev, no channels) | `OPENCLAW_SKIP_CHANNELS=1 pnpm openclaw gateway run --dev --bind loopback --port 18789 --force` |
| Health check               | `curl http://127.0.0.1:18789/health`                                                            |
| CLI in dev                 | `pnpm openclaw <command>`                                                                       |
| Commit (scoped)            | `scripts/committer "message" file1 file2`                                                       |

## 3. Running the dev gateway

No API keys or channel tokens are needed when channels are skipped.

```bash
OPENCLAW_SKIP_CHANNELS=1 pnpm openclaw gateway run --dev --bind loopback --port 18789 --force
```

The gateway generates a default config on first run. Verify with:

```bash
curl http://127.0.0.1:18789/health
# → {"ok":true,"status":"live"}
```

Additional skip flags (useful for isolated testing):

| Flag                                     | Skips                  |
| ---------------------------------------- | ---------------------- |
| `OPENCLAW_SKIP_CHANNELS=1`               | All messaging channels |
| `OPENCLAW_SKIP_GMAIL_WATCHER=1`          | Gmail hook             |
| `OPENCLAW_SKIP_CRON=1`                   | Scheduled jobs         |
| `OPENCLAW_SKIP_CANVAS_HOST=1`            | Canvas host sidecar    |
| `OPENCLAW_SKIP_BROWSER_CONTROL_SERVER=1` | Browser control server |

Combine as needed — for a minimal gateway, set all five.

## 4. Testing by codebase area

### 4a. Core / unit tests

Most source files under `src/` and `extensions/` have colocated `*.test.ts` files.

```bash
# Full unit suite (low-memory profile for Cloud VMs)
OPENCLAW_TEST_PROFILE=low OPENCLAW_TEST_SERIAL_GATEWAY=1 pnpm test

# Single file
pnpm test -- src/config/validation.test.ts

# Pattern filter
pnpm test -- src/agents/ -t "session cleanup"
```

Always use the `pnpm test --` wrapper; do not invoke `vitest` directly (the
wrapper routes tests through `scripts/test-parallel.mjs` with correct pool and
profile settings).

### 4b. Gateway tests

Gateway tests live under `src/gateway/**/*.test.ts` and run in forks mode.

```bash
# Full gateway suite
pnpm test:gateway

# Single gateway test
pnpm test -- src/gateway/server.auth.compat-baseline.test.ts
```

Gateway test helpers (`src/gateway/test-helpers.server.ts`) automatically set
`OPENCLAW_SKIP_CHANNELS`, `OPENCLAW_SKIP_GMAIL_WATCHER`, `OPENCLAW_SKIP_CRON`,
etc. so gateway tests do not need real credentials.

### 4c. Extension / plugin tests

Extension tests are under `extensions/**/*.test.ts`.

```bash
# All extension tests
pnpm test:extensions

# Single extension
pnpm test -- extensions/anthropic/src/provider.test.ts
```

### 4d. Channel tests

```bash
pnpm test:channels
```

### 4e. E2E tests

```bash
pnpm test:e2e
```

E2E tests are in `test/**/*.e2e.test.ts` and `src/**/*.e2e.test.ts`. They start
a real gateway process, so they are heavier. Control parallelism with
`OPENCLAW_E2E_WORKERS` (max 16).

### 4f. Control UI tests

```bash
pnpm test:ui
```

The control UI is a Lit/Vite app in `ui/`.

### 4g. Build verification

Run when your changes touch build output, module boundaries, or published
surfaces:

```bash
pnpm build
# Check for [INEFFECTIVE_DYNAMIC_IMPORT] warnings in the output.
```

## 5. Config and "feature flags"

OpenClaw does not use a feature-flag service. Behavior is controlled via:

1. **Config file** (`~/.openclaw/openclaw.json`, JSON5):

   ```bash
   # Read a value
   pnpm openclaw config get gateway.mode

   # Set a value
   pnpm openclaw config set gateway.mode local
   ```

2. **Environment variables** (`OPENCLAW_*`): see `.env.example` at the repo
   root for the full list. Key ones for dev:
   - `OPENCLAW_STATE_DIR` — override state directory
   - `OPENCLAW_CONFIG_PATH` — override config path
   - `OPENCLAW_GATEWAY_TOKEN` — auth token (required when binding beyond loopback)
   - `OPENCLAW_DIAGNOSTICS` — comma-separated diagnostic flags (`*` for all)

3. **Diagnostic flags** (`src/infra/diagnostic-flags.ts`): fine-grained debug
   toggles, set via `OPENCLAW_DIAGNOSTICS` env var or `diagnostics.flags` in
   config.

4. **Dangerous config flags** (`src/security/dangerous-config-flags.ts`):
   security-sensitive overrides — only touch these when explicitly required by a
   test scenario.

For unit tests, config and env are typically stubbed via Vitest's
`unstubEnvs`/`unstubGlobals` (both enabled in the base Vitest config).

## 6. Lint and format

```bash
# Full check (what CI runs)
pnpm check

# Fix formatting
pnpm format:fix

# Fix lint
pnpm lint:fix
```

`pnpm check` runs Oxfmt (format), tsgo (typecheck), Oxlint, and many custom
boundary-enforcement scripts. All must pass before push.

## 7. Commits and PRs

Use the scoped commit helper to avoid accidentally staging unrelated changes
(important in multi-agent environments):

```bash
scripts/committer "config: fix validation edge case" src/config/validation.ts src/config/validation.test.ts
```

Do not use bare `git add .` / `git commit` — the committer script unstages
everything first, then stages only the listed files.

## 8. Project layout quick reference

```
src/
├── agents/          # Agent runtime, sessions, skills
├── channels/        # Channel plugin registry, routing
├── cli/             # CLI wiring, commands
├── commands/        # Top-level CLI commands (configure, onboard, …)
├── config/          # Config I/O, validation, paths
├── discord/         # Built-in Discord channel
├── gateway/         # Gateway server, protocol, HTTP API
├── hooks/           # Lifecycle hooks
├── infra/           # Env, diagnostics, restart, updates
├── media/           # Media pipeline
├── memory/          # Memory/RAG subsystem
├── plugin-sdk/      # Public SDK surface for extensions
├── plugins/         # Plugin loader and registry
├── routing/         # Message routing
├── secrets/         # Secret storage and config
├── signal/          # Built-in Signal channel
├── slack/           # Built-in Slack channel
├── telegram/        # Built-in Telegram channel
├── terminal/        # Table formatting, palette
├── web/             # WhatsApp Web channel
└── …
extensions/          # Workspace packages (providers, channels, tools)
ui/                  # Control UI (Lit + Vite)
docs/                # Mintlify documentation
apps/                # Native apps (macOS, iOS, Android)
scripts/             # Build, test, release tooling
```

## 9. Common pitfalls

- **`pnpm gateway:dev` exits immediately** — use the full command from section 3
  instead.
- **Missing `node_modules`** — run `pnpm install` first; then retry the
  failing command.
- **Memory pressure during tests** — always use `OPENCLAW_TEST_PROFILE=low` on
  Cloud VMs.
- **`vitest not found`** — `pnpm install` was skipped or failed silently.
- **Build warnings about dynamic imports** — check for
  `[INEFFECTIVE_DYNAMIC_IMPORT]` after `pnpm build`; these indicate broken
  lazy-loading boundaries.
- **Extension import errors** — extensions must import from
  `openclaw/plugin-sdk/<subpath>`, never from `src/` or another extension
  directly.

## 10. Keeping this skill up to date

When you discover a new testing trick, environment workaround, or runbook step
that would save future Cloud agents time:

1. Open this file (`.agents/skills/cloud-agent-starter/SKILL.md`).
2. Add the new knowledge to the most relevant section, or create a new
   subsection if none fits.
3. Keep entries concise — one paragraph or a short code block per item.
4. Commit with: `scripts/committer "skill: update cloud-agent-starter" .agents/skills/cloud-agent-starter/SKILL.md`

Examples of good additions:

- A new `OPENCLAW_*` env var that simplifies a common test scenario.
- A workaround for a flaky test or environment issue specific to Cloud VMs.
- A new `pnpm test:*` script and when to use it.
- Tips for debugging a specific subsystem (gateway protocol, channel pairing,
  agent sessions, etc.).
