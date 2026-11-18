package de.ecconia.fabric.autoreplant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.PitcherCropBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class AutoReplant implements ClientModInitializer
{
	public static final String MOD_ID = "ecconia-auto-replant";
	//PitcherCropBlock keeps its max age in an unnamed constant (field_43240).
	private static final int PITCHER_MAX_AGE = 4;

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
		KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key." + MOD_ID + ".toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while(toggleKey.wasPressed())
			{
				config.enabled = !config.enabled;
				config.save();
				if(client.player != null)
				{
					client.player.sendMessage(Text.translatable("message." + MOD_ID + (config.enabled ? ".enabled" : ".disabled")), true);
				}
			}
			if(!client.options.useKey.isPressed())
			{
				harvestedThisHold = false;
			}
		});

		MinecraftClient client = MinecraftClient.getInstance();
		UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
			//The event also fires on the integrated server in singleplayer; only handle our own client player.
			//Vanilla asks the main hand first, so act once per click there.
			if(replanting || !config.enabled || client.interactionManager == null || client.player == null || player != client.player || hand != Hand.MAIN_HAND)
			{
				return ActionResult.PASS;
			}
			Harvest harvest = findHarvest(world, hit.getBlockPos());
			if(harvest == null || !config.isCropEnabled(harvest.crop()))
			{
				//While right-click is still held after a harvest, the crosshair sweeps over other blocks:
				//don't let the held item act on them (an axe would strip logs, a held block would be placed).
				return config.protectBlocksWhileHolding && harvestedThisHold ? ActionResult.FAIL : ActionResult.PASS;
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
	private static Harvest findHarvest(World world, BlockPos pos)
	{
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if(block instanceof CropBlock cropBlock)
		{
			//Wheat, carrots, potatoes, beetroots and crops from mods.
			//(Torchflower crops never count as mature here, a grown one becomes the torchflower block below.)
			if(cropBlock.isMature(state))
			{
				return growsOnBlockBelow(cropType(block), pos, state, cropBlock.getSeedsItem().asItem());
			}
		}
		else if(block instanceof NetherWartBlock)
		{
			if(state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE)
			{
				return growsOnBlockBelow(Crop.NETHER_WART, pos, state, Items.NETHER_WART);
			}
		}
		else if(state.isOf(Blocks.TORCHFLOWER))
		{
			//Only torchflowers on farmland: decorative ones on grass are left alone.
			if(world.getBlockState(pos.down()).isOf(Blocks.FARMLAND))
			{
				return growsOnBlockBelow(Crop.TORCHFLOWER, pos, state, Items.TORCHFLOWER_SEEDS);
			}
		}
		else if(block instanceof PitcherCropBlock)
		{
			//Two blocks tall when grown. Whichever half was clicked, break and replant from the lower one.
			BlockPos lowerPos = state.get(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
			BlockState lowerState = world.getBlockState(lowerPos);
			if(lowerState.getBlock() instanceof PitcherCropBlock
					&& lowerState.get(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
					&& lowerState.get(PitcherCropBlock.AGE) >= PITCHER_MAX_AGE)
			{
				return growsOnBlockBelow(Crop.PITCHER_PLANT, lowerPos, lowerState, Items.PITCHER_POD);
			}
		}
		else if(block instanceof CocoaBlock)
		{
			if(state.get(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE)
			{
				//Cocoa hangs on the side of a jungle log and FACING points at that log.
				//Clicking the same log face places the new pod facing the same way.
				Direction towardsLog = state.get(CocoaBlock.FACING);
				BlockPos logPos = pos.offset(towardsLog);
				Direction logFace = towardsLog.getOpposite();
				BlockHitResult plantHit = new BlockHitResult(Vec3d.ofCenter(logPos).offset(logFace, 0.5), logFace, logPos, false);
				return new Harvest(Crop.COCOA, pos, state, Items.COCOA_BEANS, plantHit);
			}
		}
		else if(state.isOf(Blocks.SUGAR_CANE))
		{
			//Keep the bottom block so it grows back: break the one above it, everything higher pops off with it.
			BlockPos basePos = pos;
			while(world.getBlockState(basePos.down()).isOf(Blocks.SUGAR_CANE))
			{
				basePos = basePos.down();
			}
			BlockPos cutPos = basePos.up();
			BlockState cutState = world.getBlockState(cutPos);
			if(cutState.isOf(Blocks.SUGAR_CANE))
			{
				return new Harvest(Crop.SUGAR_CANE, cutPos, cutState, null, null);
			}
		}
		else if(state.isOf(Blocks.PUMPKIN))
		{
			if(isGrownFromStem(world, pos, Blocks.ATTACHED_PUMPKIN_STEM))
			{
				return new Harvest(Crop.PUMPKIN, pos, state, null, null);
			}
		}
		else if(state.isOf(Blocks.MELON))
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
	private static boolean isGrownFromStem(World world, BlockPos fruitPos, Block attachedStem)
	{
		for(Direction direction : Direction.Type.HORIZONTAL)
		{
			BlockState neighbour = world.getBlockState(fruitPos.offset(direction));
			if(neighbour.isOf(attachedStem) && neighbour.get(AttachedStemBlock.FACING) == direction.getOpposite())
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
		BlockPos fieldBlockPos = pos.down();
		BlockHitResult plantHit = new BlockHitResult(
				new Vec3d(fieldBlockPos.getX() + 0.5f, fieldBlockPos.getY() + 0.9375f, fieldBlockPos.getZ() + 0.5f),
				Direction.UP,
				fieldBlockPos,
				false);
		return new Harvest(crop, pos, state, seed, plantHit);
	}

	private static ActionResult doTheHarvestThing(MinecraftClient client, ClientPlayerEntity player, World world, BlockHitResult hit, Harvest harvest)
	{
		ClientPlayerInteractionManager interactions = client.interactionManager;
		PlayerInventory playerInventory = player.getInventory();
		Item seed = harvest.seed();
		boolean replant = seed != null;

		//Find the seed before breaking anything: in hand, in the off-hand, or elsewhere in the hotbar.
		boolean seedInOffHand = replant && !player.getMainHandStack().isOf(seed) && player.getOffHandStack().isOf(seed);
		int seedSlot = replant && !seedInOffHand ? findInHotbar(playerInventory, seed) : -1;
		boolean haveSeed = seedInOffHand || seedSlot != -1;
		//When skipping a crop, still return FAIL: vanilla would otherwise use the held item on it
		//(for example place a held block, or cocoa beans on a nearby jungle log).
		if(replant && !haveSeed && config.onlyHarvestWithSeeds)
		{
			message(player, "no_seeds", seed.getName());
			return ActionResult.FAIL;
		}

		//Some crops do not break in one hit with every tool: cocoa needs an iron+ axe, pumpkins and melons
		//need mining speed 30 (Efficiency V axe, or Efficiency IV plus Haste). This uses the same speed check
		//as vanilla, so enchantments, Haste, Mining Fatigue, water and standing on the ground all count.
		//Attacking anyway would only start mining, which also blocks right-click until it is cancelled.
		if(!player.isCreative() && harvest.breakState().calcBlockBreakingDelta(player, world, harvest.breakPos()) < 1.0f)
		{
			message(player, "needs_tool", harvest.crop().getName(), harvest.crop().toolHint());
			return ActionResult.FAIL;
		}

		if(!interactions.attackBlock(harvest.breakPos(), hit.getSide()))
		{
			message(player, "break_failed", harvest.crop().getName());
			//At this point, normal interaction may happen:
			return ActionResult.PASS;
		}
		//From here on, the script must fail.
		if(world.getBlockState(harvest.breakPos()) == harvest.breakState())
		{
			//Break was refused on the client (for example a sword in creative).
			message(player, "break_failed", harvest.crop().getName());
			return ActionResult.FAIL;
		}
		harvestedThisHold = true;

		if(replant)
		{
			if(!haveSeed)
			{
				message(player, "no_seeds", seed.getName());
				return ActionResult.FAIL;
			}

			int previousSlot = playerInventory.getSelectedSlot();
			Hand plantHand = seedInOffHand ? Hand.OFF_HAND : Hand.MAIN_HAND;
			if(!seedInOffHand)
			{
				playerInventory.setSelectedSlot(seedSlot);
			}
			boolean planted;
			replanting = true;
			try
			{
				planted = interactions.interactBlock(player, plantHand, harvest.plantHit()).isAccepted();
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
				return ActionResult.FAIL;
			}
		}

		//Harvest (and replant) fully succeeded: vanilla has just set the held-right-click delay (itemUseCooldown) to 4 ticks.
		//Replace it with the configured delay (0 = next crop on the next tick, the same rate as holding left click).
		//Only done on success, so failures keep vanilla pacing.
		client.itemUseCooldown = config.replantDelayTicks;
		return ActionResult.FAIL;
	}

	//Prefers the selected slot, so no slot switch is needed when the seeds are already in hand.
	private static int findInHotbar(PlayerInventory playerInventory, Item seed)
	{
		if(playerInventory.getSelectedStack().isOf(seed))
		{
			return playerInventory.getSelectedSlot();
		}
		for(int i = 0; i < PlayerInventory.getHotbarSize(); i++)
		{
			if(playerInventory.getStack(i).isOf(seed))
			{
				return i;
			}
		}
		return -1;
	}

	private static void message(PlayerEntity player, String key, Object... args)
	{
		if(config.showMessages)
		{
			player.sendMessage(Text.translatable("message." + MOD_ID + "." + key, args), true);
		}
	}
}
