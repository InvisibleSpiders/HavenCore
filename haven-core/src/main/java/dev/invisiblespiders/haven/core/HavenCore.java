package dev.invisiblespiders.haven.core;

import com.zaxxer.hikari.HikariConfig;
import dev.invisiblespiders.haven.api.service.*;
import dev.invisiblespiders.haven.core.async.HavenAsyncExecutors;
import dev.invisiblespiders.haven.core.command.HavenCommand;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.economy.ItemEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.MoneyEconomyAdapter;
import dev.invisiblespiders.haven.core.gui.GuiListener;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import dev.invisiblespiders.haven.core.repository.CodexRepository;
import dev.invisiblespiders.haven.core.repository.PlayerRepository;
import dev.invisiblespiders.haven.core.repository.VirtualInventoryRepository;
import dev.invisiblespiders.haven.core.service.*;
import org.bukkit.Material;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;

public class HavenCore extends JavaPlugin {

    private static HavenCore instance;

    private ConfigManager configManager;
    private DataSourceImpl dataSource;
    private HookRegistryImpl hookRegistry;
    private ExecutorService asyncExecutor;

    @Override
    public void onEnable() {
        instance = this;

        // ── Config ────────────────────────────────────────────────────────────
        configManager = new ConfigManager(this);
        configManager.load();

        // ── Database ──────────────────────────────────────────────────────────
        HikariConfig hikariConfig = configManager.buildHikariConfig();
        dataSource = new DataSourceImpl(getLogger());
        dataSource.init(hikariConfig);

        // ── Async executor (owned thread pool) ───────────────────────────────
        asyncExecutor = HavenAsyncExecutors.create(2);

        // ── Core services ────────────────────────────────────────────────────
        EventBusImpl    eventBus    = new EventBusImpl();
        CooldownServiceImpl cooldowns = new CooldownServiceImpl();
        hookRegistry = new HookRegistryImpl(eventBus, getLogger());
        ItemRegistryImpl itemRegistry = new ItemRegistryImpl(this);
        TierServiceImpl  tierService  = new TierServiceImpl(
            configManager.getMain().getString("tier.luckperms-meta-key", "haven_tier"), getLogger()
        );
        NotificationServiceImpl notifications = new NotificationServiceImpl(configManager);

        // ── Hooks ─────────────────────────────────────────────────────────────
        VaultUnlockedHook vaultHook = new VaultUnlockedHook();
        PlaceholderAPIHook papiHook  = new PlaceholderAPIHook();
        if (configManager.getHooks().getBoolean("hooks.vaultunlocked.enabled", true)) hookRegistry.register(vaultHook);
        if (configManager.getHooks().getBoolean("hooks.placeholderapi.enabled", true)) hookRegistry.register(papiHook);

        // ── Economy ───────────────────────────────────────────────────────────
        Material currencyMat = Material.matchMaterial(
            configManager.getEconomy().getString("item-currency.material", "EMERALD")
        );
        if (currencyMat == null) currencyMat = Material.EMERALD;
        ItemEconomyAdapter itemEco = new ItemEconomyAdapter(
            this, currencyMat,
            configManager.getEconomy().getString("item-currency.pdc-tag", "haven:currency"),
            configManager.getEconomy().getBoolean("item-currency.enabled", false)
        );
        MoneyEconomyAdapter moneyEco = new MoneyEconomyAdapter(vaultHook);
        EconomyServiceImpl economyService = new EconomyServiceImpl(moneyEco, itemEco, eventBus);

        // ── Player ────────────────────────────────────────────────────────────
        PlayerRepository playerRepo = new PlayerRepository(dataSource.getDataSource());
        PlayerServiceImpl playerService = new PlayerServiceImpl(
            playerRepo, eventBus, asyncExecutor, this, getLogger()
        );
        getServer().getPluginManager().registerEvents(playerService, this);

        // ── Storage ───────────────────────────────────────────────────────────
        VirtualInventoryRepository storageRepo = new VirtualInventoryRepository(dataSource.getDataSource());
        StorageServiceImpl storageService = new StorageServiceImpl(
            storageRepo, eventBus, asyncExecutor, this, getLogger()
        );

        // ── Codex ─────────────────────────────────────────────────────────────
        CodexRepository codexRepo = new CodexRepository(dataSource.getDataSource());
        CodexServiceImpl codexService = new CodexServiceImpl(
            codexRepo, eventBus, asyncExecutor, configManager.getCodex(), getLogger()
        );

        // ── GUI listener ──────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // ── Register all services with Bukkit ServicesManager ─────────────────
        var sm = getServer().getServicesManager();
        sm.register(HavenEventBus.class,         eventBus,       this, ServicePriority.Normal);
        sm.register(HavenCooldownService.class,   cooldowns,      this, ServicePriority.Normal);
        sm.register(HavenHookRegistry.class,      hookRegistry,   this, ServicePriority.Normal);
        sm.register(HavenItemRegistry.class,      itemRegistry,   this, ServicePriority.Normal);
        sm.register(HavenTierService.class,       tierService,    this, ServicePriority.Normal);
        sm.register(HavenNotificationService.class, notifications, this, ServicePriority.Normal);
        sm.register(HavenEconomyService.class,    economyService, this, ServicePriority.Normal);
        sm.register(HavenPlayerService.class,     playerService,  this, ServicePriority.Normal);
        sm.register(HavenStorageService.class,    storageService, this, ServicePriority.Normal);
        sm.register(HavenCodexService.class,      codexService,   this, ServicePriority.Normal);
        sm.register(HavenDataSource.class,        dataSource,     this, ServicePriority.Normal);

        // ── Commands ──────────────────────────────────────────────────────────
        HavenCommand cmd = new HavenCommand(this, configManager, hookRegistry);
        var havenCmd = getCommand("haven");
        if (havenCmd != null) {
            havenCmd.setExecutor(cmd);
            havenCmd.setTabCompleter(cmd);
        }

        getLogger().info("HavenCore enabled. " + hookRegistry.getAll().size() + " hook(s) registered.");
    }

    @Override
    public void onDisable() {
        // Disable hooks first (before unregistering services)
        if (hookRegistry != null) hookRegistry.disableAll();

        // Unregister all services
        getServer().getServicesManager().unregisterAll(this);

        // Stop async database work before closing the pool
        HavenAsyncExecutors.shutdown(asyncExecutor, getLogger());
        asyncExecutor = null;

        // Close DB pool
        if (dataSource != null) dataSource.close();

        instance = null;
        getLogger().info("HavenCore disabled.");
    }

    public static HavenCore getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
}
