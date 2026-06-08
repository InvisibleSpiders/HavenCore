# HavenCore

HavenCore is the shared foundation plugin for the Haven plugin suite. It provides common services for hooks, economy, player data, storage, codex data, notifications, cooldowns, and database access.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/haven` | `haven.use` | Shows the same health output as `/haven status`. |
| `/haven help` | `haven.use` | Lists HavenCore commands with their required permissions. |
| `/haven status` | `haven.use` | Shows the HavenCore version plus hook, economy, database, async executor, storage, and codex service health. VaultUnlocked status distinguishes plugin detection from economy provider availability. |
| `/haven version` | `haven.use` | Shows the HavenCore, Paper, and Java versions currently running. |
| `/haven reload` | `haven.admin.reload` | Reloads HavenCore configuration files. A restart is still required for hooks, economy, database, and service wiring changes. |
| `/haven toggleop` | `havencore.toggleop.<code>` | Toggles OP for the executing player only when `op-toggle.yml` is enabled, the player's UUID is explicitly listed, and the player has the generated permission from their configured code. |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `haven.use` | `op` | Allows access to `/haven`, `/haven help`, `/haven status`, and `/haven version`. |
| `haven.admin` | `op` | Parent admin permission for HavenCore administrative permissions. Includes `haven.admin.reload` and `haven.admin.codex`. |
| `haven.admin.reload` | `op` | Allows `/haven reload`. |
| `haven.admin.codex` | `op` | Reserved for viewing player codex data in HavenCore/Haven suite admin tooling. |
| `havencore.toggleop.<code>` | `false` | Runtime permission generated from `op-toggle.yml` entries, such as `havencore.toggleop.a5b27`. Not inherited by `haven.admin`. |

## OP Toggle

OP toggle is disabled by default in `op-toggle.yml`. Each allowed player must be listed by UUID with a five-character alphanumeric code:

```yaml
enabled: false
players:
  InvisibleSpiders:
    uuid: "00000000-0000-0000-0000-000000000000"
    code: "A5B27"
```

The generated permission is lowercase: `havencore.toggleop.a5b27`. The command only works for the matching UUID and does not expose configured codes through tab completion. Successful toggles are logged to the server console.

## Optional Hooks

HavenCore soft-depends on these plugins when present so integrations load in the right order:

| Plugin | Purpose |
| --- | --- |
| `Vault` / `VaultUnlocked` | Economy bridge through the VaultUnlocked API. |
| `PlaceholderAPI` | Placeholder integration hook. |
| `LuckPerms` | Tier metadata lookup support. |
