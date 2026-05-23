# Daily Rewards (Fabric)

A Daily Rewards mod built for Minecraft 26.1.x (Year.Drop). 
Reward your players for logging in daily with a configurable system.

## Features

- **Dual Reward Modes:** Choose between Sequential Streaks (rewards scale up the longer the player logs in consecutively) or a Weighted Mystery Pool (RNG-based daily rewards).
- **Economy Integration:** Native support for the Common Economy API to deposit cash directly into player accounts.
- **Item & Command Execution:** Deliver physical in-game items or execute server console commands as part of the daily reward.
- **Data Safety:** Isolated per-UUID JSON data storage with atomic file swapping to prevent data corruption. Uses Java 25 Virtual Threads for asynchronous background saving.
- **Timezone Configuration:** Configurable `timezone` setting ensures your daily midnight reset occurs at exactly the right time for your community.

## Commands & Permissions

| Command | Description | Permission Node |
|---------|-------------|-----------------|
| `/daily` | Claim your daily reward or view your current streak status. | *None (Level 0)* |
| `/dailyreload` | Reloads the `config.toml` file without requiring a server restart. | `COMMANDS_ADMIN` |

## Configuration

The mod generates a `config.toml` file located in the `config/dailyrewards` directory. 

### Core Settings
- `economyProvider`: The backend economy API provider to hook into (Default: `savs_common_economy`).
- `currencyId`: The specific currency type to dispense (Default: `dollar`).
- `timezone`: Determines the timezone used for the midnight reset. Leave blank `""` to use the host machine's local time, or specify a zone (e.g., `"America/New_York"` or `"UTC"`).
- `mode`: `STREAK` (sequential progression) or `RANDOM` (weighted lottery pool).

### Rewards
Rewards are customizable. You can configure multiple days (for Streaks) or multiple possible drops (for Random mode).

**Example Reward Entry:**
```toml
[[rewards]]
  # Display name shown in chat
  displayName = "Day 1 Starter Pack"
  # Economy deposit
  economyPayout = 100.0
  # For Random mode: The weight or "chance" of this item rolling
  weight = 100
  # Native items to drop (Format: "minecraft:item_id count")
  items = ["minecraft:diamond 1", "minecraft:iron_ingot 5"]
  # Commands to execute from the console (%player% is replaced with the player's name)
  commands = ["give %player% golden_apple 1"]
```

## Building from Source

This project requires JDK 25+ and is built on the Fabric Loader (Minecraft 26.1.x / Mappings 26.1.2).

1. Clone the repository.
2. Run `./gradlew build`
3. The compiled `.jar` will be generated in `build/libs/`.
