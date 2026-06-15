package dev.invisiblespiders.haven.core;

import com.zaxxer.hikari.HikariConfig;
import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.service.*;
import dev.invisiblespiders.haven.api.service.HavenSuiteRegistry;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.core.async.HavenAsyncExecutors;
import dev.invisiblespiders.haven.core.afk.AfkCommand;
import dev.invisiblespiders.haven.core.afk.AfkManager;
import dev.invisiblespiders.haven.core.afk.AfkSettings;
import dev.invisiblespiders.haven.core.afk.NoOpAfkService;
import dev.invisiblespiders.haven.core.command.HavenCommand;
import dev.invisiblespiders.haven.core.command.RewardAdminCommand;
import dev.invisiblespiders.haven.core.command.RewardsCommand;
import dev.invisiblespiders.haven.core.command.UpgradeAdminCommand;
import dev.invisiblespiders.haven.core.command.UpgradesCommand;
import dev.invisiblespiders.haven.core.suite.CoreSuiteEntry;
import dev.invisiblespiders.haven.core.suite.HavenSuiteRegistryImpl;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.config.EconomySettings;
import dev.invisiblespiders.haven.core.config.HookSettings;
import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import dev.invisiblespiders.haven.core.config.StorageSettings;
import dev.invisiblespiders.haven.core.economy.ExcellentEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.FallbackEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.ItemEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.MoneyEconomyAdapter;
import dev.invisiblespiders.haven.core.gui.GuiListener;
import dev.invisiblespiders.haven.core.hook.ExcellentEconomyHook;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import dev.invisiblespiders.haven.core.dialog.RewardDialog;
import dev.invisiblespiders.haven.core.dialog.UpgradeDialog;
import dev.invisiblespiders.haven.core.placeholder.HavenExpansion;
import dev.invisiblespiders.haven.core.repository.CodexRepository;
import dev.invisiblespiders.haven.core.repository.PlayerRepository;
import dev.invisiblespiders.haven.core.repository.VirtualInventoryRepository;
import dev.invisiblespiders.haven.core.reward.RewardRuntimeBootstrap;
import dev.invisiblespiders.haven.core.upgrade.UpgradeRepository;
import dev.invisiblespiders.haven.core.upgrade.UpgradeServiceImpl;
import dev.invisiblespiders.haven.core.service.*;
import dev.invisiblespiders.haven.core.tab.TabManager;
import dev.invisiblespiders.haven.core.tab.TabSettings;
import dev.invisiblespiders.haven.core.util.GroupResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
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
    private HavenSuiteRegistryImpl suiteRegistry;
    private AfkManager afkManager;
    private TabManager tabManager;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        configManager = new ConfigManager(this);
        configManager.load();

        // Database
        HikariConfig hikariConfig = configManager.buildHikariConfig();
        dataSource = new DataSourceImpl(getLogger());
        dataSource.init(hikariConfig);

        // Async executor (owned thread pool)
        asyncExecutor = HavenAsyncExecutors.create(2);

        // Core services
        EventBusImpl    eventBus    = new EventBusImpl(getLogger());
        CooldownServiceImpl cooldowns = new CooldownServiceImpl();
        hookRegistry = new HookRegistryImpl(eventBus, getLogger());
        ItemRegistryImpl itemRegistry = new ItemRegistryImpl(this);
        TierServiceImpl  tierService  = new TierServiceImpl(
            configManager.getMain().getString("tier.luckperms-meta-key", "haven_tier"), getLogger()
        );
        NotificationServiceImpl notifications = new NotificationServiceImpl(configManager);
        OpToggleService opToggleService = new OpToggleService(
            this, OpToggleSettings.from(configManager.getOpToggle())
        );
        opToggleService.registerPermissions();

        // Hooks
        VaultUnlockedHook vaultHook = new VaultUnlockedHook(this, getLogger());
        ExcellentEconomyHook eeHook  = new ExcellentEconomyHook();
        PlaceholderAPIHook papiHook  = new PlaceholderAPIHook();
        HookSettings hookSettings = HookSettings.from(configManager.getHooks());
        if (hookSettings.vaultUnlockedEnabled())    hookRegistry.register(vaultHook);
        if (hookSettings.excellentEconomyEnabled()) hookRegistry.register(eeHook);
        if (hookSettings.placeholderApiEnabled())   hookRegistry.register(papiHook);

        // Economy
        EconomySettings economySettings = EconomySettings.from(configManager.getEconomy());
        Material currencyMat = economySettings.itemMaterial();
        ItemEconomyAdapter itemEco = new ItemEconomyAdapter(
            this, currencyMat,
            economySettings.itemCurrencyTag(),
            economySettings.itemCurrencyEnabled(),
            economySettings.itemDisplayName(),
            economySettings.itemLore()
        );
        // ExcellentEconomy first (native), VaultUnlocked as fallback
        FallbackEconomyAdapter moneyEco = new FallbackEconomyAdapter(
            new ExcellentEconomyAdapter(eeHook, economySettings.excellentEconomyCurrencyId()),
            new MoneyEconomyAdapter(vaultHook)
        );
        EconomyServiceImpl economyService = new EconomyServiceImpl(
            moneyEco, itemEco, eventBus, economySettings.preferredAdapter()
        );

        // Player
        PlayerRepository playerRepo = new PlayerRepository(dataSource.getDataSource());
        PlayerServiceImpl playerService = new PlayerServiceImpl(
            playerRepo, eventBus, asyncExecutor, this, getLogger()
        );
        getServer().getPluginManager().registerEvents(playerService, this);

        // Storage
        VirtualInventoryRepository storageRepo = new VirtualInventoryRepository(dataSource.getDataSource());
        StorageSettings storageSettings = StorageSettings.from(configManager.getStorage());
        StorageServiceImpl storageService = new StorageServiceImpl(
            storageRepo, eventBus, asyncExecutor, this, getLogger(), storageSettings
        );

        // Codex
        CodexRepository codexRepo = new CodexRepository(dataSource.getDataSource());
        CodexServiceImpl codexService = new CodexServiceImpl(
            codexRepo, eventBus, asyncExecutor, configManager.getCodex(), getLogger()
        );

        // Upgrades
        UpgradeRepository upgradeRepository = new UpgradeRepository(dataSource.getDataSource());
        UpgradeServiceImpl upgradeService = new UpgradeServiceImpl(upgradeRepository);
        UpgradeDialog upgradeDialog = new UpgradeDialog(configManager, upgradeService);
        upgradeService.setDialog(upgradeDialog);

        // Rewards
        HavenRewardService rewardService = RewardRuntimeBootstrap.register(
            this, dataSource.getDataSource(), configManager
        );
        RewardDialog rewardDialog = new RewardDialog(configManager, rewardService);

        // GUI listener
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // Suite registry — must be after hookRegistry and opToggleService are created
        suiteRegistry = new HavenSuiteRegistryImpl();
        suiteRegistry.register(new CoreSuiteEntry(
            this, configManager, hookRegistry, asyncExecutor, opToggleService
        ));

        // Register all services with Bukkit ServicesManager
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
        sm.register(HavenSuiteRegistry.class,     suiteRegistry,  this, ServicePriority.Normal);
        sm.register(HavenUpgradeService.class,    upgradeService, this, ServicePriority.Normal);

        // ── LuckPerms + GroupResolver ─────────────────────────────────────────
        LuckPerms luckPerms = null;
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = LuckPermsProvider.get();
            } catch (IllegalStateException ignored) {
                getLogger().warning("LuckPerms not available — group formatting will use 'default' for all players.");
            }
        }
        GroupResolver groupResolver = new GroupResolver(luckPerms);

        // ── AFK ───────────────────────────────────────────────────────────────
        if (configManager.getMain().getBoolean("features.afk", true)) {
            AfkSettings afkSettings = AfkSettings.from(configManager.getAfk());
            afkManager = new AfkManager(afkSettings, this, playerService);
            getServer().getPluginManager().registerEvents(afkManager, this);
            afkManager.start();
            sm.register(HavenAfkService.class, afkManager, this, ServicePriority.Normal);
            getLogger().info("AFK detection enabled.");
        }

        // ── Tab ───────────────────────────────────────────────────────────────
        if (configManager.getMain().getBoolean("features.tab-list", true)) {
            TabSettings tabSettings = TabSettings.from(configManager.getTab());
            HavenAfkService afkForTab = afkManager != null ? afkManager : new NoOpAfkService();
            tabManager = new TabManager(tabSettings, afkForTab, groupResolver, papiHook, this);
            getServer().getPluginManager().registerEvents(tabManager, this);
            tabManager.start();
            getLogger().info("Tab layout enabled.");
        }

        // Late-inject cross-references after both modules are wired.
        if (afkManager != null && tabManager != null) {
            afkManager.setTabManager(tabManager);
        }

        // ── PAPI expansion ────────────────────────────────────────────────────
        if (papiHook.isAvailable() && afkManager != null) {
            new HavenExpansion(afkManager, groupResolver).register();
            getLogger().info("PAPI expansion registered.");
        }

        // ── /afk command ──────────────────────────────────────────────────────
        if (afkManager != null) {
            var afkCmd = getCommand("afk");
            if (afkCmd != null) {
                AfkCommand afkCommand = new AfkCommand(afkManager, playerService);
                afkCmd.setExecutor(afkCommand);
                afkCmd.setTabCompleter(afkCommand);
            }
        }

        // Commands
        UpgradeAdminCommand upgradeAdmin = new UpgradeAdminCommand(
            configManager, upgradeService, name -> getServer().getOfflinePlayer(name)
        );
        RewardAdminCommand rewardAdmin = new RewardAdminCommand(
            configManager, rewardService, name -> getServer().getOfflinePlayer(name)
        );
        HavenCommand cmd = new HavenCommand(
            this, configManager, hookRegistry, asyncExecutor, opToggleService, suiteRegistry,
            upgradeAdmin, rewardAdmin
        );
        var havenCmd = getCommand("haven");
        if (havenCmd != null) {
            havenCmd.setExecutor(cmd);
            havenCmd.setTabCompleter(cmd);
        }
        var upgradesCmd = getCommand("upgrades");
        if (upgradesCmd != null) {
            upgradesCmd.setExecutor(new UpgradesCommand(configManager, upgradeService));
        }
        var rewardsCmd = getCommand("rewards");
        if (rewardsCmd != null) {
            rewardsCmd.setExecutor(new RewardsCommand(configManager, rewardDialog));
        }

        getLogger().info("HavenCore enabled. " + hookRegistry.getAll().size() + " hook(s) registered.");
    }

    @Override
    public void onDisable() {
        if (tabManager != null) tabManager.stop();
        if (afkManager != null) afkManager.stop();

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
