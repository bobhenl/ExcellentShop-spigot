package su.nightexpress.excellentshop.playershop.bank;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.nightcore.config.FileConfig;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Splits shop banks and allowed currencies by world group, for servers that keep a separate
 * economy per group of worlds (e.g. Multiverse-Inventories "economy" share).
 * With no groups configured everything behaves as upstream.
 */
public class WorldGroups {

    private static final String PATH_GROUPS     = "WorldGroups.Groups";
    private static final String PATH_CURRENCIES = "WorldGroups.Currencies";

    private final Map<String, String>      groupByWorld      = new HashMap<>();
    private final Map<String, Set<String>> currenciesByGroup = new HashMap<>();
    private final Map<UUID, String>        groupByBankId     = new ConcurrentHashMap<>();

    public void load(@NonNull FileConfig config) {
        this.groupByWorld.clear();
        this.currenciesByGroup.clear();

        if (!config.contains(PATH_GROUPS)) {
            config.createSection(PATH_GROUPS);
            config.setComments(PATH_GROUPS, List.of(
                "Groups of worlds that share one economy. Each group gets its own shop bank per player,",
                "and bank money can only be deposited or withdrawn while standing in a world of that group.",
                "Format: <group>: [world1, world2]. Leave empty to disable."));
        }
        if (!config.contains(PATH_CURRENCIES)) {
            config.createSection(PATH_CURRENCIES);
            config.setComments(PATH_CURRENCIES, List.of(
                "Currencies allowed for shops in each group. Format: <group>: [currency1, currency2].",
                "Groups not listed here allow every currency."));
        }

        ConfigurationSection groups = config.getConfigurationSection(PATH_GROUPS);
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                for (String world : groups.getStringList(group)) {
                    this.groupByWorld.put(world.toLowerCase(Locale.ROOT), group.toLowerCase(Locale.ROOT));
                }
            }
        }

        ConfigurationSection currencies = config.getConfigurationSection(PATH_CURRENCIES);
        if (currencies != null) {
            for (String group : currencies.getKeys(false)) {
                Set<String> ids = new HashSet<>();
                currencies.getStringList(group).forEach(id -> ids.add(id.toLowerCase(Locale.ROOT)));
                this.currenciesByGroup.put(group.toLowerCase(Locale.ROOT), ids);
            }
        }
    }

    public boolean isEnabled() {
        return !this.groupByWorld.isEmpty();
    }

    /** Group of the world, or the world name itself when it is not in any group. */
    @NonNull
    public String getGroup(@NonNull String worldName) {
        String world = worldName.toLowerCase(Locale.ROOT);
        return this.groupByWorld.getOrDefault(world, world);
    }

    public boolean isCurrencyAllowed(@NonNull String worldName, @NonNull String currencyId) {
        Set<String> allowed = this.currenciesByGroup.get(this.getGroup(worldName));
        return allowed == null || allowed.contains(currencyId.toLowerCase(Locale.ROOT));
    }

    /** Bank id for an owner in the given world. Same as the owner id when groups are disabled. */
    @NonNull
    public UUID getBankId(@NonNull UUID ownerId, @NonNull String worldName) {
        if (!this.isEnabled()) return ownerId;

        String group = this.getGroup(worldName);
        UUID bankId = UUID.nameUUIDFromBytes(("shop-bank:" + ownerId + ":" + group).getBytes(StandardCharsets.UTF_8));
        this.groupByBankId.put(bankId, group);
        return bankId;
    }

    /** Group the bank belongs to, or null for a plain per-player bank. */
    @Nullable
    public String getBankGroup(@NonNull UUID bankId) {
        return this.groupByBankId.get(bankId);
    }
}
