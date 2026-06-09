package com.henkdude1.createsadrillfix;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRIGGER_ENCHANTMENTS = BUILDER
            .comment(
                    "Enchantment ids that break extra blocks around the mined block (AOE / vein mining).",
                    "When a player mines a block with a tool that has one of these enchantments, every block",
                    "broken nearby by the enchantment will drop loot as if it was mined with that tool,",
                    "so Silk Touch, Fortune and XP apply to the whole area.",
                    "If the ids ever change in Create: Stuff 'N Additions, look them up in the mod jar under",
                    "data/<namespace>/enchantment/ and adjust this list.")
            .defineListAllowEmpty("triggerEnchantments",
                    List.of("create_sa:digging", "create_sa:chunk_eater"),
                    () -> "create_sa:digging",
                    Config::validateEnchantmentId);

    public static final ModConfigSpec.IntValue MAX_BLOCK_DISTANCE = BUILDER
            .comment(
                    "Maximum distance (in blocks) between the block the player mined and an AOE-broken block",
                    "for the fix to apply. The Digging enchantment only needs ~2, the Chunk Eater (vein miner)",
                    "enchantment can break blocks further away.")
            .defineInRange("maxBlockDistance", 12, 1, 128);

    public static final ModConfigSpec.IntValue CONTEXT_LIFETIME_TICKS = BUILDER
            .comment(
                    "How many game ticks after the player mined a block the fix keeps applying to nearby",
                    "tool-less block drops. The AOE blocks normally break in the same tick, so keep this small",
                    "to avoid affecting unrelated block drops (e.g. from machines breaking blocks nearby).")
            .defineInRange("contextLifetimeTicks", 3, 1, 200);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateEnchantmentId(Object obj) {
        return obj instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private Config() {}
}
