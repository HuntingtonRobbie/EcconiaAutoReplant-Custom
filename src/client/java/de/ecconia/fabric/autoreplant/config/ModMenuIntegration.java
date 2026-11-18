package de.ecconia.fabric.autoreplant.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the settings button in Mod Menu. Only used when Mod Menu is installed.
 */
public class ModMenuIntegration implements ModMenuApi
{
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory()
	{
		//The screen is built with Cloth Config. Without it there is no button, the settings file can still be edited by hand.
		if(!FabricLoader.getInstance().isModLoaded("cloth-config"))
		{
			return parent -> null;
		}
		return ConfigScreen::create;
	}
}
