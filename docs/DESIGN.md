# ChemCraft - Design & Decision Log

This is the "why" document. The code says *what* the plugin does; this says *what we were trying
to do and which forks we took*, so the intent survives into future work.

## 1. Goal & audience
Teach the basics of chemistry - atoms, the periodic table, bonding, reactions - to **middle
schoolers (~11-14)** through play, with lots of surprising facts. Old enough for real concepts
(valence, the octet rule, reactivity), young enough that it must be visual and fun, not equations.

Learning goals, absorbed through gameplay rather than lectured:
- what elements are and **where in the world they come from**,
- the **reactivity series**, felt as "why is gold lying around but aluminium needs a power plant?",
- **valence / the octet rule**, discovered via "why can't I make H3O?",
- **ionic vs covalent** bonding (give vs share electrons),
- **reactions & conservation of mass** (atoms rearrange, none are lost).

## 2. The three-act shape
Framed as "Complete the Periodic Table, then build the universe."
1. **Fill the table** - explore regions, extract elements, light up the wall.
2. **Build molecules** - place atoms, satisfy their bonds.
3. **React & make materials** - react molecules into products; stack lattices into blocks.
This mirrors how chemistry is taught: elements first, then bonding.

## 3. The world
Regions are themed to where elements actually occur, so gathering *is* the lesson: **sea/salt
flats** (H, O, Na, Cl, Mg), **mines** (metals, Al, Li), **air/sky** (N, O, Ar, Ne, He), **forest**
(C from burning wood), **volcano** (native S), **quarry/desert** (Si, Ca, P, F, B), **river**
(panning for Au, Ag). Regions are built by hand (e.g. WorldEdit); the plugin supplies the logic.

## 4. Extraction = the reactivity ladder
How hard an element is to free **is** how reactive it is, which doubles as the difficulty curve.
Four stations, easy->hard: **panning** (native: Au, S, Ag), **air-separation** (the gases),
**smelter** (ore + a **carbon atom** as fuel -> metals; carbon itself comes from burning wood),
**electrolysis** (needs **redstone "power"**; splits water/salt, frees the reactive metals).

## 5. The periodic-table wall
A physical wall (no 9-column GUI limit). Every element starts as a dim grey **ghost tile**; once
discovered it turns its **family colour** and lights up. So the wall is reference *and* progress
meter at once. **Per student** - each plot has its own wall.

## 6. Building molecules
- Atoms placed adjacent bond (single by default). The connected cluster = one molecule.
- Each atom "wants" `valence` bonds; **complete when every atom's bonds == its valence**.
- **Bond order** via sneak + right-click a face (single/double/triple). Needed for O2, N2, CO2.
- Completion forces the right bond orders automatically (e.g. two oxygens can only complete as a
  double bond), so the puzzle teaches itself.

## 7. Reactions & materials
- **Reactions** at a reactor consume molecule samples (granted on completion) and/or atoms, and
  produce products, with the **balanced equation** shown and conservation stated. Effects are
  **particles only - never real explosions** (no plot griefing).
- **Materials**: a **2x2x2 cube** of atoms crystallises into a real block - uniform cubes
  (carbon->diamond, metals->their blocks) and a Na/Cl checkerboard->salt.

---

# Decision log
Format: **Decision - why - trade-off / alternatives.**

- **Server-side Paper plugin, not a Forge/Fabric mod.** - Students use vanilla clients, including
  cracked/TLauncher (server runs offline-mode). - Means we **cannot add real custom block IDs**;
  every "custom block" is a repurposed vanilla block (+ resource pack later).

- **Tier-1 custom blocks (vanilla block + Display label + PDC + `custom_model_data`).** - Fastest
  path to working gameplay; runs on every client with zero setup. - Limited to ~16 glass colours
  and floating text until a resource pack is added (the `custom_model_data` hook is already there).

- **Per-student plots in one shared world** (Yon's call), with **shared gathering regions** but a
  **personal wall + build space** and **per-UUID progress**. - One world is simplest to run on a
  class server and is social; per-student walls keep progress individual. - Plot protection needed
  so kids can't grief each other.

- **Extract-and-refine realism** (Yon's call) over "click a station, get an element." - The
  method-by-station gradient teaches the reactivity series; carbon-as-smelter-fuel links the
  forest to the mines. - More steps; some raw materials use vanilla stand-ins (e.g. RED_SAND for
  bauxite) since custom raw items would need a resource pack.

- **Adjacency = a single bond; sneak-click cycles order.** - Intuitive ("snap atoms together");
  completion mathematically forces correct double/triple bonds. - Two separate molecules built
  touching are read as one cluster - build one per cluster.

- **Integer-valence bonding model.** - Clean, matches the octet rule, easy for the age group.
  - **Known limitation:** ionic lattices "over-bond" in this model (salt's Na touches many Cl),
  and resonance (ozone, average 1.5 bond order) can't be represented. We lean into it: salt is
  handled by the *material* path, ozone is simply excluded. Building toward a salt 2x2x2 will flash
  over-bond warnings until the cube completes - accepted.

- **Molecule recognition by composition (formula), not structure.** - Trivial and sufficient for
  the target molecule set. - Isomers aren't distinguished (not needed at this level).

- **Completing a molecule grants a sample item (blocks stay).** - Gives reactions an input without
  forcing the built model to vanish. - Mild "matter duplication"; we put the conservation lesson in
  the reaction step instead, where atoms-in == atoms-out is exact and checked.

- **Materials as a 2x2x2 cube, detected on placement (uniform or checker).** - Smallest satisfying
  cube; bounded, cheap detection (<=8 candidate cubes per placement). - Bigger/arbitrary lattices
  would need real pattern-matching; deferred.

- **Reactions are YAML recipes, balanced, with particle effects.** - Editable by a teacher; a build
  check verifies atom balance. - No real explosions, so plots survive.

- **Everything gameplay-related is YAML-driven.** - A teacher can retune the whole curriculum
  without recompiling. - Slightly more loader code.

- **ASCII source + Adventure `Component`s, UTF-8 build.** - Avoids the encoding pitfalls that have
  bitten this toolchain before. - No `§` colour shortcuts in code.

- **~25 elements (first 20 + Fe, Cu, Zn, Ag, Au).** - Enough to teach families and bonding and to
  populate a readable wall, without overwhelming. - Heavier/rarer elements omitted by design.
