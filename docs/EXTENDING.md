# ChemCraft - Adding Content

Almost everything is data. Edit the YAML in `src/main/resources/` (then rebuild), **or** edit the
live copy in `plugins/ChemCraft/` on the server. A bundled file is only copied to the server if it
is absent there, so to push new defaults to a running server, edit/delete the server copy.

## Add an element (`elements.yml`)
Key = the chemical symbol. Required fields:
```yaml
Xe: { name: Xenon, number: 54, group: 18, period: 5, family: noble, valence: 0,
      region: air, raw: air, extraction: air_separation,
      fact: "A rare noble gas used in bright car headlights." }
```
- `group`/`period` place the tile on the wall (group 1-18, period 1-7).
- `family` picks the tile/atom colour. Known families: `nonmetal, noble, alkali, alkaline_earth,
  metalloid, halogen, post_transition, transition` (anything else falls back to nonmetal/lime).
- `valence` = bonds wanted (0 for noble gases). Used by the molecule engine.
- To make it **obtainable**, also add an extraction recipe (below) - otherwise it only appears as a
  ghost tile.

## Add an extraction recipe (`extraction.yml`)
```yaml
- { id: pan_platinum, station: panning, icon: Pt, label: "Pan for platinum",
    inputs: { RAW_GOLD: 1 }, outputs: { Pt: 1 } }
```
- `station` must be one of the keys in `config.yml > stations` (`panning, air_separation, smelter,
  electrolysis, reactor`-but reactor uses reactions, not extraction).
- `inputs`: vanilla `Material` names and/or `atom:<SYM>` (e.g. `"atom:C": 1` for a carbon atom as
  fuel). `{}` = no input.
- `outputs`: element symbols -> count. Multiple outputs are fine (e.g. splitting water -> H + O).

## Add a molecule (`molecules.yml`)
```yaml
ozone:                      # id
  name: "Ozone"
  display: "O3"
  atoms: { O: 3 }
  fact: "..."
  reward: GLASS_BOTTLE      # optional vanilla item granted on completion
```
Recognised when a built cluster's composition matches `atoms` **and** every atom's valence is
satisfied. (Heads up: the integer-valence model can't satisfy resonance molecules like real ozone.)

## Add a reaction (`reactions.yml`)
```yaml
- { id: rust, label: "Rust iron", inputs: { "atom:Fe": 4, oxygen_gas: 3 },
    outputs: { iron_oxide: 2 }, effect: none, fact: "..." }
```
- `inputs`/`outputs`: molecule ids (must exist in `molecules.yml`) or `atom:<SYM>`.
- `effect`: `explosion`, `fire`, or `none` (particles + sound only - never a real explosion).
- **Reactions must be atom-balanced.** Verify with the helper:
  ```python
  # python3 - reads molecules.yml + reactions.yml and reports any unbalanced reaction
  ```
  (See the balance script used in development - sum element counts on each side and compare.)

## Add a material (`materials.yml`)
A 2x2x2 atom cube that crystallises into a block.
```yaml
brass: { pattern: checker, elements: [Cu, Zn], result: GOLD_BLOCK, name: "Brass block",
         fact: "Copper + zinc - the alloy in trumpets and door handles." }
quartz: { pattern: uniform, element: Si, result: QUARTZ_BLOCK, name: "Quartz", fact: "..." }
```
- `pattern: uniform` (all 8 corners `element`) or `checker` (two `elements` alternating by corner).
- `result` is any vanilla block `Material`. `name` is the display name on the dropped block.

## Move or change a station block (`config.yml`)
```yaml
stations:
  smelter: BLAST_FURNACE     # change to any block Material; right-clicking it opens that station
```

## Resource pack (visual upgrade, no code)
Atoms set `custom_model_data = atoms.base-model-data (7000) + atomicNumber`; molecule samples set
`molecules.base-model-data (8000) + hash(id) mod 1000`. Build a pack that maps those values on the
base items (stained glass for atoms, glass bottle for molecules) to your own textures. Set it as a
server resource pack (works with offline-mode clients; make it required so all students see it).
Note: the exact `custom_model_data` model syntax shifted across 1.21.x - pin it to your version.
