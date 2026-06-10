# HavenCore

HavenCore is the shared foundation plugin for the Haven plugin suite. It provides common services for hooks, economy, player data, storage, codex data, notifications, cooldowns, and database access.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/haven` | `haven.use` | Shows the same health output as `/haven status`. |
| `/haven help` | `haven.use` | Lists HavenCore commands with their required permissions. |
| `/haven status` | `haven.use` | Shows the HavenCore version plus hook, economy, database, async executor, storage, codex service, and OP-toggle health. VaultUnlocked status distinguishes plugin detection from economy provider availability. |
| `/haven doctor` | `haven.admin.doctor` | Runs core diagnostics for loaded config files, database connectivity, async executor state, hook availability, economy, storage, codex, and OP-toggle wiring. |
| `/haven version` | `haven.use` | Shows the HavenCore, Paper, and Java versions currently running. |
| `/haven reload` | `haven.admin.reload` | Reloads HavenCore configuration files. A restart is still required for hooks, economy, database, and service wiring changes. |
| `/haven toggleop` | `havencore.toggleop.<code>` | Toggles OP for the executing player only when `op-toggle.yml` is enabled, the player's UUID is explicitly listed, and the player has the generated permission from their configured code. |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `haven.use` | `op` | Allows access to `/haven`, `/haven help`, `/haven status`, and `/haven version`. |
| `haven.admin` | `op` | Parent admin permission for HavenCore administrative permissions. Includes `haven.admin.reload`, `haven.admin.doctor`, and `haven.admin.codex`. |
| `haven.admin.reload` | `op` | Allows `/haven reload`. |
| `haven.admin.doctor` | `op` | Allows `/haven doctor` diagnostics. |
| `haven.admin.codex` | `op` | Reserved for viewing player codex data in HavenCore/Haven suite admin tooling. |
| `havencore.toggleop.<code>` | `false` | Runtime permission generated from `op-toggle.yml` entries, such as `havencore.toggleop.a5b27`. Not inherited by `haven.admin`. |

## OP Toggle

OP toggle is disabled by default in `op-toggle.yml`. A single allowed player can be configured at the root with a UUID and five-character alphanumeric code:

```yaml
enabled: true
player: "00000000-0000-0000-0000-000000000000"
code: "2410a"
```

Multiple allowed players can be configured under `players`:

```yaml
enabled: true
players:
  InvisibleSpiders:
    uuid: "00000000-0000-0000-0000-000000000000"
    code: "A5B27"
```

The generated permission is lowercase: `havencore.toggleop.2410a` or `havencore.toggleop.a5b27`. Duplicate UUIDs or duplicate generated permission codes are ignored after the first valid entry and logged as configuration warnings. The command only works for the matching UUID and does not expose configured codes through tab completion. `/haven toggleop` reloads `op-toggle.yml` before checking access, so edits to this file do not require a full plugin reload. Successful toggles are logged to the server console. Denied toggle attempts keep the in-game message generic but log the failed gate to console.

Example LuckPerms assignment:

```text
/lp user InvisibleSpiders permission set havencore.toggleop.a5b27 true
```

When OP toggle is enabled, `/haven status` shows whether the feature is enabled and how many valid UUID/code entries were loaded. Startup and reload diagnostics warn about invalid UUIDs, invalid codes, duplicate codes, or enabling the feature without any valid players.

## Database API

Plugins can load `HavenDataSource` from Bukkit services for shared connections and migrations. `HavenDataSource.health()` returns a lightweight `DataSourceHealth` snapshot with initialized/open state plus Hikari pool counts for admin diagnostics.

## Configuration Diagnostics

HavenCore checks its config files during startup and reload. Diagnostics warn about invalid values and unknown keys in `database.yml`, `economy.yml`, `storage.yml`, `hooks.yml`, and `op-toggle.yml` so typos can be fixed before they cause confusing behavior.

## Optional Hooks

HavenCore soft-depends on these plugins when present so integrations load in the right order:

| Plugin | Purpose |
| --- | --- |
| `Vault` / `VaultUnlocked` | Economy bridge through the VaultUnlocked API. |
| `PlaceholderAPI` | Placeholder integration hook. |
| `LuckPerms` | Tier metadata lookup support. |

Hook status output uses these labels:

| Status | Meaning |
| --- | --- |
| `AVAILABLE` | The hook is loaded and usable. |
| `DISABLED` | The hook is intentionally disabled. |
| `MISSING_PLUGIN` | The target plugin is not installed or not detected. |
| `MISCONFIGURED` | The target plugin is present but a required provider or setup step is missing. |
| `API_ERROR` | The hook could not initialize because the target API failed unexpectedly. |
