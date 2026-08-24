package com.agurim.robocraft;

import com.agurim.robocraft.command.RoboCraftCommand;
import com.agurim.robocraft.data.PlayerStore;
import com.agurim.robocraft.listener.InteractListener;
import com.agurim.robocraft.listener.JoinListener;
import com.agurim.robocraft.listener.MenuListener;
import com.agurim.robocraft.listener.PartBlockListener;
import com.agurim.robocraft.listener.PlotProtection;
import com.agurim.robocraft.mission.MissionService;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartRegistry;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.plot.PlotManager;
import com.agurim.robocraft.plot.WorkshopKiosk;
import com.agurim.robocraft.robot.RobotEngine;
import com.agurim.robocraft.robot.RobotStore;
import com.agurim.robocraft.ui.ComponentBoard;
import com.agurim.robocraft.ui.StatusBar;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * RoboCraft - teaches robotics by building mechanisms that sense, decide and act.
 *
 * <p>Sibling of ChemCraft; see robotics/docs/DESIGN.md for the concept and the decision log, and
 * the root CLAUDE.md for why the two plugins share conventions but not a core module.
 */
public final class RoboCraftPlugin extends JavaPlugin {

    private PartRegistry parts;
    private PartStore placements;
    private RobotStore robots;
    private RobotEngine engine;
    private MissionService missions;
    private PlayerStore store;
    private PlotManager plots;
    private ComponentBoard board;
    private WorkshopKiosk kiosk;
    private StatusBar statusBar;

    private NamespacedKey partKey;
    private NamespacedKey tileKey;
    private NamespacedKey kioskKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.partKey  = new NamespacedKey(this, "part");
        this.tileKey  = new NamespacedKey(this, "tile");
        this.kioskKey = new NamespacedKey(this, "kiosk");

        this.parts      = new PartRegistry(this);
        this.store      = new PlayerStore(this);
        this.plots      = new PlotManager(this);
        this.placements = new PartStore(this);
        this.robots     = new RobotStore(this);
        this.missions   = new MissionService(this);
        this.engine     = new RobotEngine(this);
        this.board      = new ComponentBoard(this);
        this.kiosk      = new WorkshopKiosk(this);
        this.statusBar  = new StatusBar(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotProtection(this), this);
        getServer().getPluginManager().registerEvents(new PartBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        RoboCraftCommand command = new RoboCraftCommand(this);
        Objects.requireNonNull(getCommand("robocraft")).setExecutor(command);
        Objects.requireNonNull(getCommand("robocraft")).setTabCompleter(command);

        engine.start();
        missions.registry().validate(this);   // a stuck mission ladder is invisible in play

        getLogger().info("RoboCraft enabled: " + parts.size() + " parts, "
                + missions.registry().size() + " missions.");
    }

    @Override
    public void onDisable() {
        if (engine != null)     engine.stop();
        if (robots != null)     robots.save();
        if (placements != null) placements.save();
        if (store != null)      store.save();
    }

    /** Total battery capacity attached to a robot; 0 means it has no power source. */
    public int batteryCapacity(String robotKey) {
        int capacity = 0;
        for (Placed p : placements.partsOf(robotKey).values()) {
            Part part = parts.get(p.partId());
            if (part != null && part.isBattery()) capacity += part.capacity();
        }
        return capacity;
    }

    public PartRegistry parts()      { return parts; }
    public PartStore placements()    { return placements; }
    public RobotStore robots()       { return robots; }
    public RobotEngine engine()      { return engine; }
    public MissionService missions() { return missions; }
    public PlayerStore store()       { return store; }
    public PlotManager plots()       { return plots; }
    public ComponentBoard board()    { return board; }
    public WorkshopKiosk kiosk()     { return kiosk; }
    public StatusBar statusBar()     { return statusBar; }

    public NamespacedKey partKey()   { return partKey; }
    public NamespacedKey tileKey()   { return tileKey; }
    public NamespacedKey kioskKey()  { return kioskKey; }
}
