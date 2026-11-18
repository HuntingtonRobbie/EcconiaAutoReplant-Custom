package de.ecconia.fabric.autoreplant;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class AutoReplant implements ClientModInitializer
{
	public static final String MOD_ID = "ecconia-auto-replant";

	public static AutoReplantConfig config;
	//Something was harvested since right-click was last pressed down.
	private static boolean harvestedThisHold;
	//True while our own replant click runs, so the callback lets it through.
	private static boolean replanting;

	@Override
	public void onInitializeClient()
	{
		config = AutoReplantConfig.load();

		//Unbound by default, set it in Options > Controls > Key Binds.
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + MOD_ID + ".toggle",
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while(toggleKey.consumeClick())
			{
				config.enabled = !config.enabled;
				config.save();
				if(client.player != null)
				{
					client.player.sendOverlayMessage(Component.translatable("message." + MOD_ID + (config.enabled ? ".enabled" : ".disabled")));
				}
			}
			if(!client.options.keyUse.isDown())
			{
				harvestedThisHold = false;
			}
		});

		Minecraft client = Minecraft.getInstance();
		UseBlockCallback.EVENT.register((Player player, Level world, InteractionHand hand, BlockHitResult hit) -> {
			//The event also fires on the integrated server in singleplayer; only handle our own client player.
			//Vanilla asks the main hand first, so act once per click there.
			if(replanting || !config.enabled || client.gameMode == null || client.player == null || player != client.player || hand != InteractionHand.MAIN_HAND)
			{
				return InteractionResult.PASS;
			}
			Harvest harvest = findHarvest(world, hit.getBlockPos());
			if(harvest == null || !config.isCropEnabled(harvest.crop()))
			{
				//While right-click is still held after a harvest, the crosshair sweeps over other blocks:
				//don't let the held item act on them (an axe would strip logs, a held block would be placed).
				return config.protectBlocksWhileHolding && harvestedThisHold ? InteractionResult.FAIL : InteractionResult.PASS;
			}
			return doTheHarvestThing(client, client.player, world, hit, harvest);
		});
	}

	/**
	 * @param breakPos the block to break (for pitcher plants always the lower half)
	 * @param seed what to replant with, null for plants that are only harvested (sugar cane, pumpkins, melons)
	 * @param plantHit the click that puts the seed back, null when there is no seed
	 */
	private record Harvest(Crop crop, BlockPos breakPos, BlockState breakState, @Nullable Item seed, @Nullable BlockHitResult plantHit)
	{
	}

	/**
	 * @return what to break (and replant), or null if the block is not ready to harvest
	 */
	@Nullable
	private static Harvest findHarvest(Level world, BlockPos pos)
	{
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if(block instanceof CropBlock cropBlock)
		{
			//Wheat, carrots, potatoes, beetroots and crops from mods.
			//(Torchflower crops never count as mature here, a grown one becomes the torchflower block below.)
			if(cropBlock.isMaxAge(state))
			{
				return growsOnBlockBelow(cropType(block), pos, state, cropBlock.getBaseSeedId().asItem());
			}
		}
		else if(block instanceof NetherWartBlock)
		{
			if(state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE)
			{
				return growsOnBlockBelow(Crop.NETHER_WART, pos, state, Items.NETHER_WART);
			}
		}
		else if(state.is(Blocks.TORCHFLOWER))
		{
			//Only torchflowers on farmland: decorative ones on grass are left alone.
			if(world.getBlockState(pos.below()).is(Blocks.FARMLAND))
			{
				return growsOnBlockBelow(Crop.TORCHFLOWER, pos, state, Items.TORCHFLOWER_SEEDS);
			}
		}
		else if(block instanceof PitcherCropBlock)
		{
			//Two blocks tall when grown. Whichever half was clicked, break and replant from the lower one.
			BlockPos lowerPos = state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
			BlockState lowerState = world.getBlockState(lowerPos);
			if(lowerState.getBlock() instanceof PitcherCropBlock
					&& lowerState.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
					&& lowerState.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE)
			{
				return growsOnBlockBelow(Crop.PITCHER_PLANT, lowerPos, lowerState, Items.PITCHER_POD);
			}
		}
		else if(block instanceof CocoaBlock)
		{
			if(state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE)
			{
				//Cocoa hangs on the side of a jungle log and FACING points at that log.
				//Clicking the same log face places the new pod facing the same way.
				Direction towardsLog = state.getValue(CocoaBlock.FACING);
				BlockPos logPos = pos.relative(towardsLog);
				Direction logFace = towardsLog.getOpposite();
				BlockHitResult plantHit = new BlockHitResult(Vec3.atCenterOf(logPos).relative(logFace, 0.5), logFace, logPos, false);
				return new Harvest(Crop.COCOA, pos, state, Items.COCOA_BEANS, plantHit);
			}
		}
		else if(state.is(Blocks.SUGAR_CANE))
		{
			//Keep the bottom block so it grows back: break the one above it, everything higher pops off with it.
			BlockPos basePos = pos;
			while(world.getBlockState(basePos.below()).is(Blocks.SUGAR_CANE))
			{
				basePos = basePos.below();
			}
			BlockPos cutPos = basePos.above();
			BlockState cutState = world.getBlockState(cutPos);
			if(cutState.is(Blocks.SUGAR_CANE))
			{
				return new Harvest(Crop.SUGAR_CANE, cutPos, cutState, null, null);
			}
		}
		else if(state.is(Blocks.PUMPKIN))
		{
			if(isGrownFromStem(world, pos, Blocks.ATTACHED_PUMPKIN_STEM))
			{
				return new Harvest(Crop.PUMPKIN, pos, state, null, null);
			}
		}
		else if(state.is(Blocks.MELON))
		{
			if(isGrownFromStem(world, pos, Blocks.ATTACHED_MELON_STEM))
			{
				return new Harvest(Crop.MELON, pos, state, null, null);
			}
		}
		return null;
	}

	//Only fruit that grew on a stem: a stem attaches to the fruit it grows, and FACING points at it.
	//Pumpkins and melons placed by hand (decoration, builds) never have one, so they are left alone.
	private static boolean isGrownFromStem(Level world, BlockPos fruitPos, Block attachedStem)
	{
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			BlockState neighbour = world.getBlockState(fruitPos.relative(direction));
			if(neighbour.is(attachedStem) && neighbour.getValue(AttachedStemBlock.FACING) == direction.getOpposite())
			{
				return true;
			}
		}
		return false;
	}

	private static Crop cropType(Block block)
	{
		if(block == Blocks.WHEAT)
		{
			return Crop.WHEAT;
		}
		if(block == Blocks.CARROTS)
		{
			return Crop.CARROTS;
		}
		if(block == Blocks.POTATOES)
		{
			return Crop.POTATOES;
		}
		if(block == Blocks.BEETROOTS)
		{
			return Crop.BEETROOTS;
		}
		return Crop.OTHER;
	}

	//Replanted by clicking the top of the farmland (or soul sand) it grows on.
	private static Harvest growsOnBlockBelow(Crop crop, BlockPos pos, BlockState state, Item seed)
	{
		BlockPos fieldBlockPos = pos.below();
		BlockHitResult plantHit = new BlockHitResult(
				new Vec3(fieldBlockPos.getX() + 0.5f, fieldBlockPos.getY() + 0.9375f, fieldBlockPos.getZ() + 0.5f),
				Direction.UP,
				fieldBlockPos,
				false);
		return new Harvest(crop, pos, state, seed, plantHit);
	}

	private static InteractionResult doTheHarvestThing(Minecraft client, LocalPlayer player, Level world, BlockHitResult hit, Harvest harvest)
	{
		MultiPlayerGameMode interactions = client.gameMode;
		Inventory playerInventory = player.getInventory();
		Item seed = harvest.seed();
		boolean replant = seed != null;

		//Find the seed before breaking anything: in hand, in the off-hand, or elsewhere in the hotbar.
		boolean seedInOffHand = replant && !player.getMainHandItem().is(seed) && player.getOffhandItem().is(seed);
		int seedSlot = replant && !seedInOffHand ? findInHotbar(playerInventory, seed) : -1;
		boolean haveSeed = seedInOffHand || seedSlot != -1;
		//When skipping a crop, still return FAIL: vanilla would otherwise use the held item on it
		//(for example place a held block, or cocoa beans on a nearby jungle log).
		if(replant && !haveSeed && config.onlyHarvestWithSeeds)
		{
			message(player, "no_seeds", seed.getName(seed.getDefaultInstance()));
			return InteractionResult.FAIL;
		}

		//Some crops do not break in one hit with every tool: cocoa needs an iron+ axe, pumpkins and melons
		//need mining speed 30 (Efficiency V axe, or Efficiency IV plus Haste). This uses the same speed check
		//as vanilla, so enchantments, Haste, Mining Fatigue, water and standing on the ground all count.
		//Attacking anyway would only start mining, which also blocks right-click until it is cancelled.
		if(!player.isCreative() && harvest.breakState().getDestroyProgress(player, world, harvest.breakPos()) < 1.0f)
		{
			message(player, "needs_tool", harvest.crop().getName(), harvest.crop().toolHint());
			return InteractionResult.FAIL;
		}

		if(!interactions.startDestroyBlock(harvest.breakPos(), hit.getDirection()))
		{
			message(player, "break_failed", harvest.crop().getName());
			//At this point, normal interaction may happen:
			return InteractionResult.PASS;
		}
		//From here on, the script must fail.
		if(world.getBlockState(harvest.breakPos()) == harvest.breakState())
		{
			//Break was refused on the client (for example a sword in creative).
			message(player, "break_failed", harvest.crop().getName());
			return InteractionResult.FAIL;
		}
		harvestedThisHold = true;

		if(replant)
		{
			if(!haveSeed)
			{
				message(player, "no_seeds", seed.getName(seed.getDefaultInstance()));
				return InteractionResult.FAIL;
			}

			int previousSlot = playerInventory.getSelectedSlot();
			InteractionHand plantHand = seedInOffHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if(!seedInOffHand)
			{
				playerInventory.setSelectedSlot(seedSlot);
			}
			boolean planted;
			replanting = true;
			try
			{
				planted = interactions.useItemOn(player, plantHand, harvest.plantHit()).consumesAction();
			}
			finally
			{
				replanting = false;
			}
			if(config.switchBackToHeldItem && !seedInOffHand)
			{
				playerInventory.setSelectedSlot(previousSlot);
			}
			if(!planted)
			{
				message(player, "plant_failed", harvest.crop().getName());
				return InteractionResult.FAIL;
			}
		}

		//Harvest (and replant) fully succeeded: vanilla has just set the held-right-click delay (rightClickDelay) to 4 ticks.
		//Replace it with the configured delay (0 = next crop on the next tick, the same rate as holding left click).
		//Only done on success, so failures keep vanilla pacing.
		client.rightClickDelay = config.replantDelayTicks;
		return InteractionResult.FAIL;
	}

	//Prefers the selected slot, so no slot switch is needed when the seeds are already in hand.
	private static int findInHotbar(Inventory playerInventory, Item seed)
	{
		if(playerInventory.getSelectedItem().is(seed))
		{
			return playerInventory.getSelectedSlot();
		}
		for(int i = 0; i < Inventory.getSelectionSize(); i++)
		{
			if(playerInventory.getItem(i).is(seed))
			{
				return i;
			}
		}
		return -1;
	}

	private static void message(Player player, String key, Object... args)
	{
		if(config.showMessages)
		{
			player.sendOverlayMessage(Component.translatable("message." + MOD_ID + "." + key, args));
		}
	}
}
