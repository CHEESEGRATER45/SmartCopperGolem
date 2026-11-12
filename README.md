# SmartCopperGolem (Paper 26.2)

Copper golems keep their vanilla walking, pathfinding and animations. The only thing that changes is the
"does this chest already hold my kind of item?" decision: with `match-mode: CATEGORY`, a golem carrying
birch logs will deliver to a chest that holds oak planks, because both are in the same category.

## Build and install
Needs JDK 25 and Gradle 9.1+ (no wrapper is included).

    gradle build

The jar is `build/libs/SmartCopperGolem-1.0.0.jar`. Put it in `plugins/` and restart.
`/scg stats` shows the mode, how many categories/items loaded, and how many golems are using it.
`/scg reload` reloads `config.yml` and `categories.yml`.

## Categories
`plugins/SmartCopperGolem/categories.yml` is generated from the "Items 26.2" tab of the item sheet:
40 categories covering 1,226 items. Edit it freely. Entries can be item ids (`oak_log`) or globs (`*_banner`).
An item belongs to one category; the first one listed wins (the sheet lists `barrel` and `firework_star`
twice, so they stay in the first category). Items in no category only match themselves, like vanilla.

To regenerate it after the sheet changes: download the tab as CSV, then

    python3 tools/sheet_to_categories.py "<downloaded>.csv" categories.yml

Copy the result into `plugins/SmartCopperGolem/` and run `/scg reload`. Rows that are guesses (for example
"Purpur Bricks", which doesn't exist in 26.2) are listed when the script runs.

## How it works
`CategoryTransportBehavior` extends the vanilla `TransportItemsBetweenContainers` and overrides the two
protected methods that contain the match test. It reaches the few private members it needs by reflection,
all resolved when the plugin starts. If this server's class doesn't match, the plugin disables itself and
golems stay vanilla. Nothing is saved to golems; removing the plugin restores vanilla behaviour.

As in vanilla, a golem walks to the nearest chest and checks it on arrival, so it may visit a non-matching
chest first. Vanilla also lets golems drop items into completely empty chests; set
`allow-empty-containers: false` to stop that.

## License
MIT, see `LICENSE`. Written from scratch from observed vanilla behaviour. No code from any mod is used.
