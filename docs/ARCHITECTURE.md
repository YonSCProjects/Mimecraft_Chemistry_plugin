# ChemCraft - Architecture

Class-level reference for safely modifying the code. See `CLAUDE.md` for the high-level map.

## Bootstrap
`ChemCraftPlugin extends JavaPlugin`
- `onEnable`: `saveDefaultConfig()`, create 5 `NamespacedKey`s, then construct (in order):
  `ElementRegistry, PlayerStore, PlotManager, PeriodicWall, ExtractionService, AtomStore,
  MoleculeRegistry, BondStore, MoleculeEngine, ReactionService, MaterialEngine`; register 5
  listeners; set the command executor.
- `onDisable`: saves `PlayerStore`, `AtomStore`, `BondStore`.
- Exposes a getter for every service + key (e.g. `plugin.atoms()`, `plugin.moleculeEngine()`,
  `plugin.reactionKey()`). All collaborators reach each other via these getters on the plugin.

## element/
- `Element` (record): symbol, name, number, group, period, family, valence, region, raw,
  extraction, fact. Loaded from `elements.yml`.
- `ElementRegistry`: `get`, `has`, `all`. Writes the default `elements.yml` if missing.
- `Families`: `material(family)` -> the stained-glass `Material` for that family (also used as the
  atom-item material). Ghost tiles use `GRAY_STAINED_GLASS`.
- `AtomItems`: `create(plugin, element, n)` builds the atom item (family glass + display name +
  lore + PDC `atom`=symbol + `custom_model_data = atoms.base-model-data + atomicNumber`).
  `symbolOf`, `countIn`, `removeFrom` read/scan inventories by the `atom` PDC.

## world/AtomStore
`location -> symbol` for **player-placed** atom blocks. Key = `world;x;y;z`. `put/get/remove`,
saved to `atoms.yml` on each change. This is how a plain glass block knows which element it is.

## molecule/
- `BondStore`: `order(a,b)` (default 1), `setOrder`, `removeAround(loc)`. Edge key = the two sorted
  location keys joined by `|`. Persisted `bonds.yml`.
- `Molecule` (record) + `MoleculeRegistry`: loads `molecules.yml`; `match(composition)` compares a
  **canonical formula string** (`canonical()` = symbols sorted, each with its count); `byId`.
- `MoleculeEngine` (the core):
  - `component(start)` - BFS over face-adjacent atoms in `AtomStore` (cap 2000).
  - `usedValence(loc)` - sum of `BondStore.order` over adjacent atoms.
  - `evaluate(loc, player)` - classify the cluster: any atom `used > valence` -> **over-bonded**
    (warn); any `used < valence` -> **incomplete** (silent); else **complete** -> `celebrate` once
    (a `Set<locKey> celebrated` prevents re-firing until the cluster changes).
  - `celebrate` - match composition -> message + fact + reward + a **molecule sample item**; else
    a generic "stable molecule" message. Particles + level-up sound.
  - `cycleBond(atomLoc, face, player)` - cycle the bond to the neighbour on `face`, re-evaluate.
  - `onAtomRemoved(loc, player)` - clear that location's bonds + celebrated flag, re-evaluate
    surviving neighbours.
- `MoleculeItems`: molecule samples as `GLASS_BOTTLE` items (PDC `molecule`=id, `custom_model_data
  = molecules.base-model-data + hash(id)`); `idOf/count/remove`.

## extraction/
- `Recipe` (record): id, station, icon, label, inputs, outputs. Input keys are vanilla `Material`
  names or `atom:<SYM>`; output keys are element symbols.
- `ExtractionService`: loads `extraction.yml`; `recipesFor(station)`, `byId`, `canAfford`,
  `extract` (consume inputs, give atom items, mark discovered, light wall tiles, fact, sound).

## reaction/
- `Reaction` (record): id, label, inputs, outputs (molecule ids or `atom:<SYM>`), effect, fact.
- `ReactionService`: loads `reactions.yml`; `all`, `byId`, `canAfford`, `react` (consume, produce,
  effect, print the balanced `equation`, conservation message). `playEffect`: explosion/fire/none
  map to **particles + sound only**.

## material/
- `MaterialDef` (record): id, pattern (`uniform`/`checker`), e1, e2, result `Material` name, name.
- `MaterialEngine.check(loc, player)` - test the 8 candidate 2x2x2 cubes containing `loc`; if all 8
  cells are atoms and match a `uniform`(all e1) or `checker`(e1/e2 by corner parity) pattern,
  `form`: clear the 8 atoms (store + bonds + labels + set AIR) and give the result block + a fact.
  Returns true if a material formed.

## table/PeriodicWall
Per-plot table built from `tileLocation(plotIndex, element)` = `plotCorner + wall offsets +
(group-1, MAX_PERIOD-period)*spacing`. `build` lays all tiles as ghosts (grey glass + dim
`TextDisplay` tagged `tile`); `lightUp` swaps to family colour + bright label; `isWallTile` lets
protection guard the wall.

## plot/PlotManager
Grid in one world. `plotCorner(i)`/`plotCenter(i)` from index via `per-row`, `size`, `gap`,
`origin`, `ground-y`. `plotIndexAt(loc)` reverses it (or -1 in a gap). `teleportToPlot`.

## data/PlayerStore
`players.yml`: `<uuid>.plot`, `<uuid>.wall-built`, `<uuid>.discovered` (list), `_meta.next-plot`.
`getOrAssignPlotIndex` hands out the next free plot on first join.

## ui/
`StationMenu(station)` and `ReactionMenu` - chest GUIs (`InventoryHolder`s). Each icon carries the
recipe/reaction id in PDC (`recipe`/`reaction`); affordable icons show in colour, unaffordable as a
grey pane. `StationListener.onClick` dispatches by holder type.

## listener/
- `JoinListener` - assign plot, build the wall once, re-light discovered tiles, teleport on first join.
- `PlotProtection` - cancel break/place outside the player's own plot (ops bypass); cancel breaking
  wall tiles. Runs at NORMAL.
- `StationListener` - right-click a configured station/reactor block -> open the right menu; handle
  menu clicks -> extract/react.
- `AtomBlockListener` - **HIGH, ignoreCancelled** so protection decides first. On place: record +
  label, then `MaterialEngine.check` else `MoleculeEngine.evaluate`. On break: drop the atom back,
  remove label, `onAtomRemoved`.
- `MoleculeListener` - sneak + right-click an atom -> `cycleBond` on the clicked face.

## Persistence formats (all YAML)
- `players.yml` - see PlayerStore above.
- `atoms.yml` - flat `world;x;y;z: SYMBOL`.
- `bonds.yml` - flat `worldKeyA|worldKeyB: order`.
