package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.AdditionalBossesHook;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.skills.PerksUtils;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-specific addition: a live line above the hotbar for super abilities, so players can see them charge.
 *
 * <ul>
 *     <li>While a tool is readied, it shows which ability is ready and how long the tool stays raised.</li>
 *     <li>While an ability is active, it counts down the time it has left.</li>
 *     <li>While holding a tool whose ability is on cooldown, it shows the cooldown recharging.</li>
 * </ul>
 *
 * <p>It stays out of the way of other messages: after any other mcMMO action bar message it waits a moment
 * (see {@link McMMOPlayer#holdChargeBar(long)}), and it never paints over Additional Bosses messages.</p>
 */
public final class AbilityChargeBarTask implements Runnable {

    /** Ticks between updates (twice a second). */
    public static final long PERIOD_TICKS = 10L;

    /** How long a readied tool stays raised; matches the delay of the {@link ToolLowerTask}. */
    public static final long READY_WINDOW_MILLIS = 4L * Misc.TIME_CONVERSION_FACTOR;

    private static final int SEGMENTS = 10;
    private static final String SEGMENT = "■";
    private static final String EMPTY_COLOR = "&8";

    private static volatile @Nullable Map<ToolType, List<SuperAbilityType>> abilitiesByTool;

    private final @NotNull McMMOPlayer mmoPlayer;
    private final boolean showRecharge;

    public AbilityChargeBarTask(@NotNull McMMOPlayer mmoPlayer) {
        this.mmoPlayer = mmoPlayer;
        this.showRecharge = mcMMO.p.getGeneralConfig().getAbilityChargeBarShowRecharge();
    }

    @Override
    public void run() {
        final Player player = mmoPlayer.getPlayer();
        if (!player.isOnline() || !mmoPlayer.useChatNotifications()) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < mmoPlayer.getChargeBarHeldUntil() || AdditionalBossesHook.actionBarBusy(player)) {
            return;
        }

        final Component line = buildLine(player, now);
        if (line != null) {
            mcMMO.getAudiences().player(player).sendActionBar(line);
        }
    }

    private @Nullable Component buildLine(@NotNull Player player, long now) {
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            if (ability != SuperAbilityType.BLAST_MINING && mmoPlayer.getAbilityMode(ability)) {
                return activeLine(ability, now);
            }
        }

        final ItemStack inHand = player.getInventory().getItemInMainHand();
        for (Map.Entry<ToolType, List<SuperAbilityType>> entry : abilitiesByTool().entrySet()) {
            final ToolType tool = entry.getKey();
            if (!tool.inHand(inHand)) {
                continue;
            }

            if (mmoPlayer.getToolPreparationMode(tool)) {
                final Component ready = readyLine(player, tool, entry.getValue(), now);
                if (ready != null) {
                    return ready;
                }
            }

            return showRecharge ? rechargeLine(player, entry.getValue(), now) : null;
        }

        return null;
    }

    /** An active ability: its time left, draining. */
    private @NotNull Component activeLine(@NotNull SuperAbilityType ability, long now) {
        final long endsAt = mmoPlayer.getProfile().getAbilityDATS(ability) * Misc.TIME_CONVERSION_FACTOR;
        final long left = Math.max(0L, endsAt - now);
        final long length = Math.max(left, mmoPlayer.getAbilityLengthMillis(ability));

        return LocaleLoader.getTextComponent("Skills.ChargeBar.Active",
                ability.getLocalizedName(),
                bar(length <= 0 ? 0 : (double) left / length, "&6"),
                formatTime(left));
    }

    /** A readied tool: which abilities are ready, what triggers them, and how long the tool stays up. */
    private @Nullable Component readyLine(@NotNull Player player, @NotNull ToolType tool,
            @NotNull List<SuperAbilityType> abilities, long now) {
        final List<String> names = new ArrayList<>(abilities.size());
        final List<String> hints = new ArrayList<>(abilities.size());
        for (SuperAbilityType ability : abilities) {
            if (cooldown(player, ability, now).remaining() <= 0 && canUse(player, ability)) {
                names.add(ability.getLocalizedName());
                hints.add(LocaleLoader.getString("Skills.ChargeBar.Hint." + ability.name()));
            }
        }

        if (names.isEmpty()) {
            return null;
        }

        final long raisedFor = now - mmoPlayer.getToolPreparedAt(tool);
        final double left = 1.0 - (double) raisedFor / READY_WINDOW_MILLIS;
        return LocaleLoader.getTextComponent("Skills.ChargeBar.Ready",
                String.join(" / ", names),
                String.join(" / ", hints),
                bar(left, "&a"));
    }

    /** Holding a tool whose ability is on cooldown: the cooldown filling back up. */
    private @Nullable Component rechargeLine(@NotNull Player player,
            @NotNull List<SuperAbilityType> abilities, long now) {
        final List<Component> parts = new ArrayList<>(abilities.size());
        boolean anyRecharging = false;
        for (SuperAbilityType ability : abilities) {
            final Cooldown cooldown = cooldown(player, ability, now);
            if (cooldown.remaining() > 0) {
                anyRecharging = true;
                parts.add(LocaleLoader.getTextComponent("Skills.ChargeBar.Recharging",
                        ability.getLocalizedName(),
                        bar(1.0 - (double) cooldown.remaining() / cooldown.total(), "&e"),
                        formatTime(cooldown.remaining())));
            } else if (canUse(player, ability)) {
                parts.add(LocaleLoader.getTextComponent("Skills.ChargeBar.Charged",
                        ability.getLocalizedName()));
            }
        }

        if (!anyRecharging) {
            return null;
        }

        final Component separator = LocaleLoader.getTextComponent("Skills.ChargeBar.Separator");
        return Component.join(JoinConfiguration.separator(
                Component.text(" ").append(separator).append(Component.text(" "))), parts);
    }

    private static boolean canUse(@NotNull Player player, @NotNull SuperAbilityType ability) {
        return ability.getPermissions(player)
                && RankUtils.hasUnlockedSubskill(player, ability.getSubSkillTypeDefinition());
    }

    /**
     * How long an ability's cooldown has left. Cooldown perks only ever shorten a cooldown, so once the
     * base cooldown has passed the (permission-based) perk lookup is skipped.
     */
    private @NotNull Cooldown cooldown(@NotNull Player player, @NotNull SuperAbilityType ability,
            long now) {
        if (mmoPlayer.getAbilityMode(ability)) {
            return Cooldown.NONE;
        }

        final long deactivatedAt = mmoPlayer.getProfile().getAbilityDATS(ability)
                * Misc.TIME_CONVERSION_FACTOR;
        final int baseSeconds = ability.getCooldown();
        if (deactivatedAt + (long) baseSeconds * Misc.TIME_CONVERSION_FACTOR <= now) {
            return Cooldown.NONE;
        }

        final long total = (long) PerksUtils.handleCooldownPerks(player, baseSeconds)
                * Misc.TIME_CONVERSION_FACTOR;
        final long remaining = deactivatedAt + total - now;
        return remaining <= 0 || total <= 0 ? Cooldown.NONE : new Cooldown(remaining, total);
    }

    private record Cooldown(long remaining, long total) {
        static final Cooldown NONE = new Cooldown(0L, 1L);
    }

    /** A bar of {@link #SEGMENTS} blocks, {@code fraction} of them in {@code color} (an {@code &} code). */
    static @NotNull String bar(double fraction, @NotNull String color) {
        final double clamped = Math.max(0.0, Math.min(1.0, fraction));
        final int filled = (int) Math.round(clamped * SEGMENTS);
        return color + SEGMENT.repeat(filled) + EMPTY_COLOR + SEGMENT.repeat(SEGMENTS - filled);
    }

    /** "45s" under a minute, "3:05" above, always rounding up so it never shows 0 while still waiting. */
    static @NotNull String formatTime(long millis) {
        final long seconds = Math.max(0L, (millis + Misc.TIME_CONVERSION_FACTOR - 1)
                / Misc.TIME_CONVERSION_FACTOR);
        if (seconds < 60) {
            return seconds + "s";
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Which super abilities each tool triggers (an axe has two), built once. */
    private static @NotNull Map<ToolType, List<SuperAbilityType>> abilitiesByTool() {
        Map<ToolType, List<SuperAbilityType>> map = abilitiesByTool;
        if (map == null) {
            final Map<ToolType, List<SuperAbilityType>> built = new EnumMap<>(ToolType.class);
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                final SuperAbilityType ability = mcMMO.p.getSkillTools().getSuperAbility(skill);
                final ToolType tool = mcMMO.p.getSkillTools().getPrimarySkillToolType(skill);
                // Only abilities that are actually implemented have a sub-skill definition.
                if (ability == null || tool == null || ability == SuperAbilityType.BLAST_MINING
                        || ability.getSubSkillTypeDefinition() == null) {
                    continue;
                }
                built.computeIfAbsent(tool, ignored -> new ArrayList<>()).add(ability);
            }
            built.replaceAll((tool, abilities) -> List.copyOf(abilities));
            map = Collections.unmodifiableMap(built);
            abilitiesByTool = map;
        }
        return map;
    }
}
