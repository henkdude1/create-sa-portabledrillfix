package com.henkdude1.createsadrillfix;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * End-to-end test of the AOE drops fix, replicating exactly what Create: Stuff 'N
 * Additions does: the player "mines" one block (BreakEvent) and the enchantment then
 * destroys a neighbor via {@code Level.destroyBlock} with no tool. With the fix, the
 * neighbor must drop the silk-touched item (stone), not the tool-less drop (cobblestone).
 */
@EventBusSubscriber(modid = CreateSaDrillFix.MOD_ID)
public final class AoeDropsGameTests {

    // The game test runner instantiates this class reflectively via a public no-arg
    // constructor, even for static test methods — do not add a private constructor.

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        event.register(AoeDropsGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", templateNamespace = CreateSaDrillFix.MOD_ID, timeoutTicks = 100)
    public static void aoeDropsUseToolEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // The real trigger enchantments come from Create: Stuff 'N Additions, which is
        // not present in the test environment, so use a vanilla enchantment instead.
        Config.TRIGGER_ENCHANTMENTS.set(List.of("minecraft:efficiency"));

        // Barrier floor so the dropped items stay where we can assert on them.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.BARRIER);
            }
        }
        BlockPos centerRel = new BlockPos(1, 1, 1);
        BlockPos neighborRel = new BlockPos(2, 1, 1);
        helper.setBlock(centerRel, Blocks.STONE);
        helper.setBlock(neighborRel, Blocks.STONE);

        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> trigger = enchantments.getHolderOrThrow(Enchantments.EFFICIENCY);
        Holder<Enchantment> silkTouch = enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH);
        ItemStack drill = new ItemStack(Items.DIAMOND_PICKAXE);
        drill.enchant(trigger, 1);
        drill.enchant(silkTouch, 1);

        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "drillfix-test"), ClientInformation.createDefault());
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);

        // 1. The player starts breaking the center block; this event fires before the
        // AOE enchantment code runs and lets the mod record its mining context.
        BlockPos centerAbs = helper.absolutePos(centerRel);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, centerAbs, level.getBlockState(centerAbs), player));

        // 2. The AOE enchantment destroys a neighbor with no tool, exactly like the
        // MCreator-generated code in Create: Stuff 'N Additions does.
        level.destroyBlock(helper.absolutePos(neighborRel), true);

        // 3. With the fix, the neighbor is silk-touched: stone instead of cobblestone.
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(Items.STONE, neighborRel, 2.0);
            helper.assertItemEntityNotPresent(Items.COBBLESTONE, neighborRel, 2.0);
        });
    }
}
