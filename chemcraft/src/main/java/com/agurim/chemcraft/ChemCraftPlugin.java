package com.agurim.chemcraft;

import com.agurim.chemcraft.command.ChemCraftCommand;
import com.agurim.chemcraft.data.PlayerStore;
import com.agurim.chemcraft.element.ElementRegistry;
import com.agurim.chemcraft.extraction.ExtractionService;
import com.agurim.chemcraft.listener.AtomBlockListener;
import com.agurim.chemcraft.listener.JoinListener;
import com.agurim.chemcraft.listener.MoleculeListener;
import com.agurim.chemcraft.listener.PlotProtection;
import com.agurim.chemcraft.listener.StationListener;
import com.agurim.chemcraft.material.MaterialEngine;
import com.agurim.chemcraft.molecule.BondStore;
import com.agurim.chemcraft.molecule.MoleculeEngine;
import com.agurim.chemcraft.molecule.MoleculeRegistry;
import com.agurim.chemcraft.listener.WallRegisterListener;
import com.agurim.chemcraft.plot.PlotManager;
import com.agurim.chemcraft.plot.PlotShield;
import com.agurim.chemcraft.plot.StationKiosk;
import com.agurim.chemcraft.reaction.ReactionService;
import com.agurim.chemcraft.region.RegionBuilder;
import com.agurim.chemcraft.region.RegionManager;
import com.agurim.chemcraft.table.PeriodicWall;
import com.agurim.chemcraft.ui.AssistBoard;
import com.agurim.chemcraft.assistant.AskService;
import com.agurim.chemcraft.ui.MissionBar;
import com.agurim.chemcraft.world.AtomStore;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ChemCraftPlugin extends JavaPlugin {

    private ElementRegistry registry;
    private PlayerStore store;
    private PlotManager plots;
    private StationKiosk kiosk;
    private PeriodicWall wall;
    private ExtractionService extraction;
    private AtomStore atoms;
    private MoleculeRegistry moleculeRegistry;
    private BondStore bonds;
    private MoleculeEngine moleculeEngine;
    private ReactionService reactions;
    private MaterialEngine materials;
    private RegionManager regions;
    private RegionBuilder regionBuilder;
    private AssistBoard assistBoard;
    private PlotShield shield;
    private MissionBar missionBar;
    private AskService ask;

    private NamespacedKey tileKey;
    private NamespacedKey atomKey;
    private NamespacedKey recipeKey;
    private NamespacedKey moleculeKey;
    private NamespacedKey reactionKey;
    private NamespacedKey stationKey;
    private NamespacedKey regionKey;
    private NamespacedKey sourceKey;
    private NamespacedKey sourceNameKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.tileKey       = new NamespacedKey(this, "tile");
        this.atomKey       = new NamespacedKey(this, "atom");
        this.recipeKey     = new NamespacedKey(this, "recipe");
        this.moleculeKey   = new NamespacedKey(this, "molecule");
        this.reactionKey   = new NamespacedKey(this, "reaction");
        this.stationKey    = new NamespacedKey(this, "station");
        this.regionKey     = new NamespacedKey(this, "region");
        this.sourceKey     = new NamespacedKey(this, "source");
        this.sourceNameKey = new NamespacedKey(this, "source_name");

        this.registry         = new ElementRegistry(this);
        this.store            = new PlayerStore(this);
        this.plots            = new PlotManager(this);
        this.kiosk            = new StationKiosk(this);
        this.wall             = new PeriodicWall(this);
        this.extraction       = new ExtractionService(this);
        this.atoms            = new AtomStore(this);
        this.moleculeRegistry = new MoleculeRegistry(this);
        this.bonds            = new BondStore(this);
        this.moleculeEngine   = new MoleculeEngine(this);
        this.reactions        = new ReactionService(this);
        this.materials        = new MaterialEngine(this);
        this.regions          = new RegionManager(this);
        this.regionBuilder    = new RegionBuilder(this);
        this.assistBoard      = new AssistBoard(this);
        this.shield           = new PlotShield(this);
        this.missionBar       = new MissionBar(this);
        this.ask              = new AskService(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotProtection(this), this);
        getServer().getPluginManager().registerEvents(new StationListener(this), this);
        getServer().getPluginManager().registerEvents(new AtomBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MoleculeListener(this), this);
        getServer().getPluginManager().registerEvents(new WallRegisterListener(this), this);
        Objects.requireNonNull(getCommand("chemcraft")).setExecutor(new ChemCraftCommand(this));

        assistBoard.init();
        regionBuilder.refillBeds(); // heal node beds left as placeholders by a restart mid-regen

        getLogger().info("ChemCraft enabled: " + registry.all().size() + " elements, "
                + moleculeRegistry.size() + " molecules, " + reactions.all().size() + " reactions, "
                + regions.all().size() + " regions.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        if (atoms != null) atoms.save();
        if (bonds != null) bonds.save();
    }

    public ElementRegistry registry()         { return registry; }
    public PlayerStore store()                { return store; }
    public PlotManager plots()                { return plots; }
    public StationKiosk kiosk()               { return kiosk; }
    public PeriodicWall wall()                { return wall; }
    public ExtractionService extraction()     { return extraction; }
    public AtomStore atoms()                  { return atoms; }
    public MoleculeRegistry moleculeRegistry(){ return moleculeRegistry; }
    public BondStore bonds()                  { return bonds; }
    public MoleculeEngine moleculeEngine()    { return moleculeEngine; }
    /**
     * kiosk.stations from the SERVER config file only; absent key = empty = all methods
     * (legacy). isSet ignores the jar-embedded default, which would otherwise leak
     * [reactor] into upgraded servers whose config predates the key.
     */
    public java.util.List<String> kioskStations() {
        return getConfig().isSet("kiosk.stations")
                ? getConfig().getStringList("kiosk.stations")
                : java.util.List.of();
    }

    public ReactionService reactions()        { return reactions; }
    public MaterialEngine materials()         { return materials; }
    public RegionManager regions()            { return regions; }
    public RegionBuilder regionBuilder()      { return regionBuilder; }
    public AssistBoard assistBoard()          { return assistBoard; }
    public PlotShield shield()                { return shield; }
    public MissionBar missionBar()            { return missionBar; }
    public AskService ask()                   { return ask; }
    public NamespacedKey tileKey()            { return tileKey; }
    public NamespacedKey atomKey()            { return atomKey; }
    public NamespacedKey recipeKey()          { return recipeKey; }
    public NamespacedKey moleculeKey()        { return moleculeKey; }
    public NamespacedKey reactionKey()        { return reactionKey; }
    public NamespacedKey stationKey()         { return stationKey; }
    public NamespacedKey regionKey()          { return regionKey; }
    public NamespacedKey sourceKey()          { return sourceKey; }
    public NamespacedKey sourceNameKey()      { return sourceNameKey; }
}
