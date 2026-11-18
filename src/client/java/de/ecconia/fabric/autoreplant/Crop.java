package de.ecconia.fabric.autoreplant;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Every kind of plant the mod can harvest. Each one has its own on/off switch in the settings.
 */
public enum Crop
{
	WHEAT,
	CARROTS,
	POTATOES,
	BEETROOTS,
	NETHER_WART,
	COCOA,
	TORCHFLOWER,
	PITCHER_PLANT,
	//Harvest only, nothing to replant: sugar cane keeps its bottom block, pumpkins and melons keep their stem.
	SUGAR_CANE,
	PUMPKIN,
	MELON,
	//Any other crop block, for example from other mods.
	OTHER;

	public String key()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	public Component getName()
	{
		return Component.translatable("crop." + AutoReplant.MOD_ID + "." + key());
	}

	//Which tool breaks it in one hit. Only some crops need one, the others break instantly with anything.
	public Component toolHint()
	{
		return Component.translatableWithFallback("crop." + AutoReplant.MOD_ID + "." + key() + ".tool_hint", "Use a faster tool.");
	}
}
