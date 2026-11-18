package de.ecconia.fabric.autoreplant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Settings, stored in config/ecconia-auto-replant.json. Edited in game through Mod Menu (needs Cloth Config) or by hand.
 */
public class AutoReplantConfig
{
	private static final Logger LOGGER = LoggerFactory.getLogger(AutoReplant.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(AutoReplant.MOD_ID + ".json");

	public static final int VANILLA_DELAY_TICKS = 4;
	public static final int MAX_DELAY_TICKS = 10;

	//Master switch, also flipped by the toggle key.
	public boolean enabled = true;
	//Ticks to wait after a successful harvest before held right-click fires again. 0 = one crop per tick, 4 = vanilla.
	public int replantDelayTicks = 0;
	//After a harvest, while right-click is still held, don't use the held item on other blocks (no stripped logs).
	public boolean protectBlocksWhileHolding = true;
	//After taking seeds from another hotbar slot, go back to the item held before (keeps a Fortune hoe or an axe in hand).
	public boolean switchBackToHeldItem = true;
	//Leave the crop alone when there is nothing to replant it with.
	public boolean onlyHarvestWithSeeds = false;
	//Action bar hints, for example when no seeds are in the hotbar.
	public boolean showMessages = true;
	public Map<Crop, Boolean> crops = defaultCrops();

	public boolean isCropEnabled(Crop crop)
	{
		return crops.getOrDefault(crop, true);
	}

	private static Map<Crop, Boolean> defaultCrops()
	{
		Map<Crop, Boolean> crops = new EnumMap<>(Crop.class);
		for(Crop crop : Crop.values())
		{
			crops.put(crop, true);
		}
		return crops;
	}

	public static AutoReplantConfig load()
	{
		AutoReplantConfig config = null;
		if(Files.exists(FILE))
		{
			try(Reader reader = Files.newBufferedReader(FILE))
			{
				config = GSON.fromJson(reader, AutoReplantConfig.class);
			}
			catch(Exception e)
			{
				LOGGER.error("Could not read {}, using default settings.", FILE, e);
			}
		}
		if(config == null)
		{
			config = new AutoReplantConfig();
		}
		config.clean();
		//Writes the file on first start, and adds any settings that are new since it was last saved.
		config.save();
		return config;
	}

	//Repairs values that were hand-edited out of range or left out of the file.
	private void clean()
	{
		replantDelayTicks = Math.clamp(replantDelayTicks, 0, MAX_DELAY_TICKS);
		Map<Crop, Boolean> loaded = crops;
		crops = defaultCrops();
		if(loaded != null)
		{
			for(Crop crop : Crop.values())
			{
				Boolean value = loaded.get(crop);
				if(value != null)
				{
					crops.put(crop, value);
				}
			}
		}
	}

	public void save()
	{
		try
		{
			Files.createDirectories(FILE.getParent());
			try(Writer writer = Files.newBufferedWriter(FILE))
			{
				GSON.toJson(this, writer);
			}
		}
		catch(IOException e)
		{
			LOGGER.error("Could not save {}", FILE, e);
		}
	}
}
