package de.ecconia.fabric.autoreplant.config;

import de.ecconia.fabric.autoreplant.AutoReplant;
import de.ecconia.fabric.autoreplant.AutoReplantConfig;
import de.ecconia.fabric.autoreplant.Crop;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen (Cloth Config). Kept in its own class so the mod still loads without Cloth Config installed.
 */
public final class ConfigScreen
{
	private static final String KEY = "config." + AutoReplant.MOD_ID + ".";

	private ConfigScreen()
	{
	}

	public static Screen create(Screen parent)
	{
		AutoReplantConfig config = AutoReplant.config;
		AutoReplantConfig defaults = new AutoReplantConfig();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(KEY + "title"))
				.setSavingRunnable(config::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable(KEY + "category.general"));
		general.addEntry(entries.startBooleanToggle(Component.translatable(KEY + "enabled"), config.enabled)
				.setDefaultValue(defaults.enabled)
				.setTooltip(Component.translatable(KEY + "enabled.tooltip"))
				.setSaveConsumer(value -> config.enabled = value)
				.build());
		general.addEntry(entries.startIntSlider(Component.translatable(KEY + "delay"), config.replantDelayTicks, 0, AutoReplantConfig.MAX_DELAY_TICKS)
				.setDefaultValue(defaults.replantDelayTicks)
				.setTextGetter(ConfigScreen::delayText)
				.setTooltip(Component.translatable(KEY + "delay.tooltip"))
				.setSaveConsumer(value -> config.replantDelayTicks = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Component.translatable(KEY + "protect"), config.protectBlocksWhileHolding)
				.setDefaultValue(defaults.protectBlocksWhileHolding)
				.setTooltip(Component.translatable(KEY + "protect.tooltip"))
				.setSaveConsumer(value -> config.protectBlocksWhileHolding = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Component.translatable(KEY + "switch_back"), config.switchBackToHeldItem)
				.setDefaultValue(defaults.switchBackToHeldItem)
				.setTooltip(Component.translatable(KEY + "switch_back.tooltip"))
				.setSaveConsumer(value -> config.switchBackToHeldItem = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Component.translatable(KEY + "only_with_seeds"), config.onlyHarvestWithSeeds)
				.setDefaultValue(defaults.onlyHarvestWithSeeds)
				.setTooltip(Component.translatable(KEY + "only_with_seeds.tooltip"))
				.setSaveConsumer(value -> config.onlyHarvestWithSeeds = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Component.translatable(KEY + "show_messages"), config.showMessages)
				.setDefaultValue(defaults.showMessages)
				.setTooltip(Component.translatable(KEY + "show_messages.tooltip"))
				.setSaveConsumer(value -> config.showMessages = value)
				.build());
		general.addEntry(entries.startTextDescription(Component.translatable(KEY + "keybind_hint")).build());

		ConfigCategory crops = builder.getOrCreateCategory(Component.translatable(KEY + "category.crops"));
		for(Crop crop : Crop.values())
		{
			crops.addEntry(entries.startBooleanToggle(crop.getName(), config.isCropEnabled(crop))
					.setDefaultValue(true)
					.setTooltip(Component.translatable(KEY + "crop." + crop.key() + ".tooltip"))
					.setSaveConsumer(value -> config.crops.put(crop, value))
					.build());
		}

		return builder.build();
	}

	private static Component delayText(int ticks)
	{
		if(ticks == 0)
		{
			return Component.translatable(KEY + "delay.fastest");
		}
		if(ticks == AutoReplantConfig.VANILLA_DELAY_TICKS)
		{
			return Component.translatable(KEY + "delay.vanilla", ticks);
		}
		return Component.translatable(KEY + "delay.ticks", ticks);
	}
}
