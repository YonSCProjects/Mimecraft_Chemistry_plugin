# ChemCraft (v0.4.0)

A PaperMC plugin that teaches chemistry by turning the world into a game.
Targets **PaperMC 1.21.x / Java 21**. Working title.

## The full arc - now complete
1. **Fill the table** - explore, extract elements, light up your periodic-table wall.
2. **Build molecules** - place atoms, satisfy their bonds, get them recognised.
3. **React & make materials** - react molecules into products, and crystallise lattices into blocks.

The whole loop runs end to end: **gather raw materials -> refine into atoms -> fill the periodic
table -> bond atoms into molecules -> react molecules / stack them into materials.**

## What's new in v0.4
### Reactions (the Reactor - a `FURNACE`)
Right-click the reactor to open a menu of reactions. Each needs **molecule samples** (you get
one each time you complete a molecule) and/or atoms. React, and you get the products, an effect
(a safe particle "bang" - no terrain damage), the **balanced equation**, and a reminder that
**mass is conserved**. Ships with five, all balanced, editable in `reactions.yml`:
- `2 H2 + O2 -> 2 H2O` (burn hydrogen)
- `C + O2 -> CO2` (burn carbon)
- `CH4 + 2 O2 -> CO2 + 2 H2O` (burn methane)
- `2 Na + Cl2 -> 2 NaCl` (make salt)
- `N2 + 3 H2 -> 2 NH3` (the Haber process)

### Materials (lattices -> real blocks)
Build a **2x2x2 cube of atoms** in your plot and it crystallises:
- a cube of **carbon -> a diamond block**,
- cubes of **iron / copper / gold -> their metal blocks**,
- a **sodium/chlorine checkerboard -> a salt crystal**.

The 8 atoms are consumed and you get the block plus a fact. Edit patterns in `materials.yml`.

## Controls recap
- **Place an atom:** hold an atom item, right-click a face (don't sneak).
- **Bond order:** **sneak** + right-click an atom on the face toward its neighbour (single->double->triple).
- **Break an atom:** break the block - it returns to you.
- **Stations & reactor:** right-click the station block (`COPPER_BLOCK` pan, `BLUE_ICE` air,
  `BLAST_FURNACE` smelt, `SEA_LANTERN` electrolysis, `FURNACE` reactor).
- Build **one molecule or lattice per cluster**.

## Try the new stuff fast
```
/chemcraft givemol oxygen_gas 1
/chemcraft givemol hydrogen_gas 2     # then react them at the FURNACE -> water (bang!)
/chemcraft give C 8                   # place a 2x2x2 carbon cube -> diamond block
```
Completing a molecule the normal way (bonding atoms) also hands you its sample for reactions.

## Everything before still applies
Per-student plots in one shared world; a personal periodic-table wall; per-player progress;
plot protection; four extraction stations on the reactivity ladder; the valence/bond molecule
engine; atoms and molecules as resource-pack-ready items.

## Build & run
JDK 21 + Gradle (or open in IntelliJ): `gradle build` -> jar in `build/libs/`. Drop it into
`plugins/` on a **1.21.x** server; it writes `config.yml`, `elements.yml`, `extraction.yml`,
`molecules.yml`, `reactions.yml`, and `materials.yml` into `plugins/ChemCraft/`. If your server
isn't 1.21.8, change the `paper-api` version in `build.gradle`.

## Honest caveats
- It's a scaffold - **build and test on a 1.21.8 Paper server** before classroom use. I can't
  compile against the Paper API in here, but the data checks pass (reactions are balanced, no
  dangling references, all internal calls resolve).
- The simple integer-valence model means an ionic lattice (salt) may flash "over-bonded" warnings
  while you build it up - the 2x2x2 completes and converts. A known, harmless quirk.
- Reactions use particle effects, never real explosions, so plots stay intact.
- `setCustomModelData(int)` is deprecated in 1.21.4 but still works.

## Where to go from here (all optional)
- A **resource pack** mapping the `custom_model_data` values to real atom/molecule icons - pure
  visual upgrade, no code.
- More elements / molecules / reactions / materials - all just YAML.
- A **classroom layer**: quests, a build-off competition, or scoreboards for who's filled the
  most of their table.
