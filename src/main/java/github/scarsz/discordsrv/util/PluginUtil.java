/*-
 * LICENSE
 * DiscordSRV
 * -------------
 * Copyright (C) 2016 - 2021 Austin "Scarsz" Shapiro
 * -------------
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * END
 */

package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.DiscordSRV;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class PluginUtil {
    /**
     * Check whether or not the given plugin is installed and enabled
     * @param pluginName The plugin name to check
     * @param startsWith Whether or not to to {@link String#startsWith(String)} checking
     * @return Whether or not the plugin is installed and enabled
     */
    public static boolean checkIfPluginEnabled(String pluginName, boolean startsWith) {
        if (startsWith && checkIfPluginEnabled(pluginName, false)) {
            return true;
        }
        for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
            boolean match = startsWith
                    ? plugin.getDescription().getName().toLowerCase().startsWith(pluginName.toLowerCase())
                    : plugin.getDescription().getName().equalsIgnoreCase(pluginName);
            if (match) {
                return true;
            }
        }
        return false;
    }

    public static boolean pluginHookIsEnabled(String pluginName) {
        return pluginHookIsEnabled(pluginName, true);
    }

    public static boolean pluginHookIsEnabled(String pluginName, boolean startsWith) {
        boolean enabled = checkIfPluginEnabled(pluginName, startsWith);
        for (String pluginHookName : DiscordSRV.config().getStringList("DisabledPluginHooks")) {
            if (pluginName.toLowerCase().startsWith(pluginHookName.toLowerCase())) {
                enabled = false;
                break;
            }
        }
        return enabled;
    }

    public static Plugin getPlugin(String pluginName) {
        for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins())
            if (plugin.getDescription().getName().toLowerCase().startsWith(pluginName.toLowerCase())) return plugin;
        return null;
    }

}
