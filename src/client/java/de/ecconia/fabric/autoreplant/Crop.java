package de.ecconia.fabric.autoreplant;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Every kind of plant the mod can harvest and replant. Each one has its own on/off switch in the settings.
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
	//Any other crop block, for example from other mods.
	OTHER;

	public String key()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	public Text getName()
	{
		return Text.translatable("crop." + AutoReplant.MOD_ID + "." + key());
	}
}
