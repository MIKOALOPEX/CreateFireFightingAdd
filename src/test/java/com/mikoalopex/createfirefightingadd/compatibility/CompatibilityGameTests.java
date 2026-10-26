package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateFireFightingAdd.MODID)
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class CompatibilityGameTests {
    @net.neoforged.bus.api.SubscribeEvent
    public static void template(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        var level = event.getServer().overworld();
        level.getStructureManager().getOrCreate(CreateFireFightingAdd.path("compatibility_empty"))
            .fillFromWorld(level, new BlockPos(0, 200, 0), new net.minecraft.core.Vec3i(3, 3, 3), false, null);
    }

    @GameTest(template = "compatibility_empty")
    public static void capabilityProfile(GameTestHelper helper) {
        String profile = System.getProperty("createfirefightingadd.compatibilityProfile", "base");
        creativeFiltering(helper);
        for (String recipe : new String[] {"rope_hook", "smart_rope_connector", "rope_connector_master", "stress_coupling"}) {
            var id = CreateFireFightingAdd.path(recipe);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent() == ComponentPolicies.enabled(id),
                "Recipe does not match component policy: " + recipe);
        }
        if (!profile.equals("base")) {
            helper.assertTrue(CompatibilityManager.available(Capability.SABLE_SPATIAL), CompatibilityManager.status(Capability.SABLE_SPATIAL).toString());
            for (Capability capability : Capability.values()) {
                boolean expected = profile.equals("full") || capability == Capability.SABLE_SPATIAL
                    || profile.equals("ropes") && capability != Capability.COUPLING && capability != Capability.FLUID_COUPLING
                    || profile.startsWith("old") && capability == Capability.ROPE_CONNECT;
                helper.assertTrue(CompatibilityManager.available(capability) == expected,
                    profile + " / " + capability + ": " + CompatibilityManager.status(capability));
            }
            if (profile.equals("full")) backendFailure(helper);
            helper.succeed();
            return;
        }
        helper.assertTrue(CompatibilityManager.version("sable") == null, "Smoke test requires a Create-only directory");
        for (Capability capability : Capability.values())
            helper.assertTrue(!CompatibilityManager.available(capability), "Unexpected capability: " + capability);
        for (String recipe : new String[] {"rope_hook", "smart_rope_connector", "rope_connector_master", "stress_coupling"})
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CreateFireFightingAdd.path(recipe)).isEmpty(),
                "Disabled recipe was loaded: " + recipe);
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, CreateFireFightingAdd.ROPE_HOOK.get());
        var be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof InertPhysicsBlockEntity, "Hook must retain an inert block entity");
        helper.assertTrue(CreateFireFightingAdd.ROPE_HOOK.get().getStateDefinition().getProperties().size() == 3,
            "Inert hook must preserve facing, axis and powered properties");
        helper.assertTrue(CreateFireFightingAdd.SMART_ROPE_CONNECTOR.get().getStateDefinition().getProperties().size() == 2,
            "Inert connector must preserve exactly facing and axis properties");
        CompoundTag data = new CompoundTag();
        data.putString("CompatibilityRetentionTest", "saved");
        CompoundTag foreign = new CompoundTag();
        foreign.putLong("UnknownFutureField", 12345);
        data.put("RopeData", foreign);
        be.loadWithComponents(data, helper.getLevel().registryAccess());
        CompoundTag saved = be.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getString("CompatibilityRetentionTest").equals("saved"), "Unknown NBT was lost");
        helper.assertTrue(saved.getCompound("RopeData").getLong("UnknownFutureField") == 12345, "Nested NBT was lost");
        helper.setBlock(pos, CreateFireFightingAdd.ROPE_MASTER.get());
        var master = (com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master.RopeMasterBlockEntity)
            helper.getBlockEntity(pos);
        CompoundTag missingStack = new CompoundTag();
        missingStack.putString("id", "missing_optional_mod:rope");
        missingStack.putInt("count", 7);
        ListTag inventory = new ListTag();
        inventory.add(missingStack);
        CompoundTag masterData = new CompoundTag();
        masterData.put("Inventory", inventory);
        master.loadWithComponents(masterData, helper.getLevel().registryAccess());
        helper.assertTrue(master.saveWithoutMetadata(helper.getLevel().registryAccess()).getList("Inventory", 10).equals(inventory),
            "Unresolved manager inventory was lost");
        ListTag knownInventory = new ListTag();
        knownInventory.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRING, 7)
            .save(helper.getLevel().registryAccess()));
        masterData.put("Inventory", knownInventory);
        master.loadWithComponents(masterData, helper.getLevel().registryAccess());
        helper.assertTrue(master.getItemHandler().extractItem(0, 7, false).getCount() == 7, "Disabled inventory cannot be recovered");
        helper.assertTrue(master.saveWithoutMetadata(helper.getLevel().registryAccess()).getList("Inventory", 10).isEmpty(),
            "Extracted inventory was restored from stale data");
        helper.succeed();
    }

    private static void creativeFiltering(GameTestHelper helper) {
        var tab = net.minecraft.world.item.CreativeModeTabs.getDefaultTab();
        var parameters = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
            net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess());
        var parent = new net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet<net.minecraft.world.item.ItemStack>(
            net.minecraft.world.item.ItemStackLinkedSet.TYPE_AND_TAG);
        var search = new net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet<net.minecraft.world.item.ItemStack>(
            net.minecraft.world.item.ItemStackLinkedSet.TYPE_AND_TAG);
        var event = new net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent(tab,
            net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS, parameters, parent, search);
        var both = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
        var parentOnly = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT);
        var searchOnly = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND);
        var retained = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS);
        event.accept(both, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        event.accept(parentOnly, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        event.accept(searchOnly, net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        event.accept(retained, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        CompatibilityCreativeContents.filter(event, stack -> true);
        helper.assertTrue(parent.size() == 3 && search.size() == 3, "Unchanged tabs must remain readable");
        CompatibilityCreativeContents.filter(event, stack -> stack.is(net.minecraft.world.item.Items.GLASS));
        helper.assertTrue(parent.size() == 1 && parent.contains(retained) && search.size() == 1 && search.contains(retained),
            "Tab filtering must remove parent-only, search-only and shared entries");
        CompatibilityCreativeContents.filter(event, stack -> false);
        CompatibilityCreativeContents.filter(event, stack -> false);
        helper.assertTrue(parent.isEmpty() && search.isEmpty(), "Empty tabs must filter safely");
    }

    public static final class FailingFluidEndpoint extends com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingBlockEntity {
        public FailingFluidEndpoint(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
            throw new IllegalStateException("Expected adapter failure in compatibility test");
        }
    }

    private static void backendFailure(GameTestHelper helper) {
        try {
            var factory = com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings.class.getDeclaredField("factory");
            factory.setAccessible(true);
            factory.set(null, FailingFluidEndpoint.class.getConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class));
            var state = com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings.BLOCK.get().defaultBlockState();
            var fallback = com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings.BLOCK_ENTITY.get().create(BlockPos.ZERO, state);
            helper.assertTrue(fallback != null, "Failed fluid adapter must retain a block entity");
            helper.assertTrue(!CompatibilityManager.available(Capability.FLUID_COUPLING), "Fluid failure must reach capability cache");
            helper.assertTrue(!ComponentPolicies.enabled(CreateFireFightingAdd.path("fluid_coupling")), "Failed fluid endpoint must reject placement");
            helper.assertTrue(CompatibilityManager.available(Capability.COUPLING), "Fluid failure must not disable stress coupling");
            helper.assertTrue((CompatibilityManager.mask() & 1 << Capability.FLUID_COUPLING.ordinal()) == 0, "Sync mask retained failed adapter");
            CompatibilityManager.disable(Capability.COUPLING, "Expected shared backend failure in compatibility test");
            helper.assertTrue(!com.mikoalopex.createfirefightingadd.content.kinetics.coupling.StressCouplingCompatibility.enabled(),
                "Legacy coupling entry points must observe capability failure");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
