package de.ecconia.fabric.autoreplant.config;

import de.ecconia.fabric.autoreplant.AutoReplant;
import de.ecconia.fabric.autoreplant.AutoReplantConfig;
import de.ecconia.fabric.autoreplant.Crop;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
				.setTitle(Text.translatable(KEY + "title"))
				.setSavingRunnable(config::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Text.translatable(KEY + "category.general"));
		general.addEntry(entries.startBooleanToggle(Text.translatable(KEY + "enabled"), config.enabled)
				.setDefaultValue(defaults.enabled)
				.setTooltip(Text.translatable(KEY + "enabled.tooltip"))
				.setSaveConsumer(value -> config.enabled = value)
				.build());
		general.addEntry(entries.startIntSlider(Text.translatable(KEY + "delay"), config.replantDelayTicks, 0, AutoReplantConfig.MAX_DELAY_TICKS)
				.setDefaultValue(defaults.replantDelayTicks)
				.setTextGetter(ConfigScreen::delayText)
				.setTooltip(Text.translatable(KEY + "delay.tooltip"))
				.setSaveConsumer(value -> config.replantDelayTicks = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Text.translatable(KEY + "switch_back"), config.switchBackToHeldItem)
				.setDefaultValue(defaults.switchBackToHeldItem)
				.setTooltip(Text.translatable(KEY + "switch_back.tooltip"))
				.setSaveConsumer(value -> config.switchBackToHeldItem = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Text.translatable(KEY + "only_with_seeds"), config.onlyHarvestWithSeeds)
				.setDefaultValue(defaults.onlyHarvestWithSeeds)
				.setTooltip(Text.translatable(KEY + "only_with_seeds.tooltip"))
				.setSaveConsumer(value -> config.onlyHarvestWithSeeds = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Text.translatable(KEY + "show_messages"), config.showMessages)
				.setDefaultValue(defaults.showMessages)
				.setTooltip(Text.translatable(KEY + "show_messages.tooltip"))
				.setSaveConsumer(value -> config.showMessages = value)
				.build());
		general.addEntry(entries.startTextDescription(Text.translatable(KEY + "keybind_hint")).build());

		ConfigCategory crops = builder.getOrCreateCategory(Text.translatable(KEY + "category.crops"));
		for(Crop crop : Crop.values())
		{
			crops.addEntry(entries.startBooleanToggle(crop.getName(), config.isCropEnabled(crop))
					.setDefaultValue(true)
					.setTooltip(Text.translatable(KEY + "crop." + crop.key() + ".tooltip"))
					.setSaveConsumer(value -> config.crops.put(crop, value))
					.build());
		}

		return builder.build();
	}

	private static Text delayText(int ticks)
	{
		if(ticks == 0)
		{
			return Text.translatable(KEY + "delay.fastest");
		}
		if(ticks == AutoReplantConfig.VANILLA_DELAY_TICKS)
		{
			return Text.translatable(KEY + "delay.vanilla", ticks);
		}
		return Text.translatable(KEY + "delay.ticks", ticks);
	}
}
