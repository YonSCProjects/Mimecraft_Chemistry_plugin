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
import com.agurim.chemcraft.plot.PlotManager;
import com.agurim.chemcraft.reaction.ReactionService;
import com.agurim.chemcraft.table.PeriodicWall;
import com.agurim.chemcraft.world.AtomStore;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ChemCraftPlugin extends JavaPlugin {

    private ElementRegistry registry;
    private PlayerStore store;
    private PlotManager plots;
    private PeriodicWall wall;
    private ExtractionService extraction;
    private AtomStore atoms;
    private MoleculeRegistry moleculeRegistry;
    private BondStore bonds;
    private MoleculeEngine moleculeEngine;
    private ReactionService reactions;
    private MaterialEngine materials;

    private NamespacedKey tileKey;
    private NamespacedKey atomKey;
    private NamespacedKey recipeKey;
    private NamespacedKey moleculeKey;
    private NamespacedKey reactionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.tileKey     = new NamespacedKey(this, "tile");
        this.atomKey     = new NamespacedKey(this, "atom");
        this.recipeKey   = new NamespacedKey(this, "recipe");
        this.moleculeKey = new NamespacedKey(this, "molecule");
        this.reactionKey = new NamespacedKey(this, "reaction");

        this.registry         = new ElementRegistry(this);
        this.store            = new PlayerStore(this);
        this.plots            = new PlotManager(this);
        this.wall             = new PeriodicWall(this);
        this.extraction       = new ExtractionService(this);
        this.atoms            = new AtomStore(this);
        this.moleculeRegistry = new MoleculeRegistry(this);
        this.bonds            = new BondStore(this);
        this.moleculeEngine   = new MoleculeEngine(this);
        this.reactions        = new ReactionService(this);
        this.materials        = new MaterialEngine(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotProtection(this), this);
        getServer().getPluginManager().registerEvents(new StationListener(this), this);
        getServer().getPluginManager().registerEvents(new AtomBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MoleculeListener(this), this);
        Objects.requireNonNull(getCommand("chemcraft")).setExecutor(new ChemCraftCommand(this));

        getLogger().info("ChemCraft enabled: " + registry.all().size() + " elements, "
                + moleculeRegistry.size() + " molecules, " + reactions.all().size() + " reactions.");
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
    public PeriodicWall wall()                { return wall; }
    public ExtractionService extraction()     { return extraction; }
    public AtomStore atoms()                  { return atoms; }
    public MoleculeRegistry moleculeRegistry(){ return moleculeRegistry; }
    public BondStore bonds()                  { return bonds; }
    public MoleculeEngine moleculeEngine()    { return moleculeEngine; }
    public ReactionService reactions()        { return reactions; }
    public MaterialEngine materials()         { return materials; }
    public NamespacedKey tileKey()            { return tileKey; }
    public NamespacedKey atomKey()            { return atomKey; }
    public NamespacedKey recipeKey()          { return recipeKey; }
    public NamespacedKey moleculeKey()        { return moleculeKey; }
    public NamespacedKey reactionKey()        { return reactionKey; }
}
