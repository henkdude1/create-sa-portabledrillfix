package com.henkdude1.createsadrillfix;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Create S&A Portable Drill Fix.
 *
 * <p>Create: Stuff 'N Additions breaks the extra blocks of its Digging / Chunk Eater
 * enchantments via {@code Level.destroyBlock}, which drops loot as if no tool was used.
 * As a result Silk Touch, Fortune and XP are only applied to the block the player
 * actually mined, not to the rest of the area of effect.
 *
 * <p>This mod listens for those tool-less block drops happening right next to a player
 * who just mined a block with a Digging/Chunk Eater enchanted tool, and recomputes the
 * loot (and experience) using the player's actual tool, so all of its enchantments
 * apply to the whole area.
 */
@Mod(CreateSaDrillFix.MOD_ID)
public final class CreateSaDrillFix {
    public static final String MOD_ID = "create_sa_drillfix";

    public CreateSaDrillFix(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
