# Chest Radar

Remembers every chest, barrel and placed shulker box you open, so you can find any item again, custom server items included (names, lore, enchants).

- **G** opens the radar, **R** searches the item in your hand.
- Click an item and its container gets outlined through walls, with a compass until you get there.
- Preview of a container you already opened, what changed since your last visit, pins, craft planner and collection routes.

Client-side only, nothing to install on the server. Fabric, Minecraft 26.2 and 26.3.

## Build

Clone [Aeternum](https://github.com/EroiiKZz/Aeternum) next to this repo, in a folder named `aeternum`:

```
git clone https://github.com/EroiiKZz/Aeternum aeternum
```

Then `./gradlew build` (and `./gradlew build263` for the 26.3 jar).
