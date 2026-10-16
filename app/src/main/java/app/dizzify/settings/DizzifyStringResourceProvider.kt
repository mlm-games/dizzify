package app.dizzify.settings

import android.content.Context
import app.dizzify.R
import io.github.mlmgames.settings.core.resources.AndroidStringResourceProvider
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.core.resources.StringResourceProvider

/**
 * Maps [DizzifySettingsKeys] to Android resources.
 *
 * The English literal in each annotation stays as the library's fallback; the
 * keys resolve to these resources through SettingMeta.resolvedTitle and friends.
 */
private val settingsResources: Map<String, Int> = mapOf(
    SettingsTextKeys.LANGUAGE to R.string.language,
    SettingsTextKeys.LANGUAGE_SYSTEM to R.string.system_default,
    DizzifySettingsKeys.SETTING_THEME to R.string.theme,
    DizzifySettingsKeys.SETTING_THEME_OPTIONS to R.array.setting_theme_options,
    DizzifySettingsKeys.SETTING_SHOW_APP_ICONS to R.string.show_banners_icons,
    DizzifySettingsKeys.SETTING_SHOW_APP_ICONS_DESC to R.string.display_app_banners_icons,
    DizzifySettingsKeys.SETTING_SORT_ORDER to R.string.sort_order,
    DizzifySettingsKeys.SETTING_SORT_ORDER_OPTIONS to R.array.setting_sort_order_options,
    DizzifySettingsKeys.SETTING_SEARCH_TYPE to R.string.search_type,
    DizzifySettingsKeys.SETTING_SEARCH_TYPE_OPTIONS to R.array.setting_search_type_options,
    DizzifySettingsKeys.SETTING_SEARCH_ALIASES to R.string.search_aliases,
    DizzifySettingsKeys.SETTING_SEARCH_ALIASES_OPTIONS to R.array.setting_search_aliases_options,
    DizzifySettingsKeys.SETTING_SEARCH_INCLUDE_PACKAGE_NAMES to R.string.include_package_names_in_search,
    DizzifySettingsKeys.SETTING_SHOW_HIDDEN_APPS_ON_SEARCH to R.string.show_hidden_apps_in_search,
    DizzifySettingsKeys.SETTING_NON_TV_APPS to R.string.non_tv_apps,
    DizzifySettingsKeys.SETTING_NON_TV_APPS_DESC to R.string.non_tv_apps_desc,
    DizzifySettingsKeys.SETTING_NON_TV_APPS_OPTIONS to R.array.setting_non_tv_apps_options,
    DizzifySettingsKeys.SETTING_PREFER_TV_LAUNCH to R.string.prefer_tv_leanback_launch,
    DizzifySettingsKeys.SETTING_PREFER_TV_LAUNCH_DESC to R.string.prefer_tv_leanback_launch_desc,
    DizzifySettingsKeys.SETTING_SHOW_TV_INPUTS to R.string.show_tv_inputs_row,
    DizzifySettingsKeys.SETTING_SHOW_TV_INPUTS_DESC to R.string.show_tv_inputs_desc,
    DizzifySettingsKeys.SETTING_SHOW_WATCH_NEXT to R.string.show_continue_watching,
    DizzifySettingsKeys.SETTING_SHOW_WATCH_NEXT_DESC to R.string.show_watch_next_desc,
    DizzifySettingsKeys.SETTING_SHOW_SYSTEM_APPS to R.string.show_system_apps,
    DizzifySettingsKeys.SETTING_SHOW_SYSTEM_APPS_DESC to R.string.include_system_apps_in_list,
    DizzifySettingsKeys.SETTING_SHOW_PINNED_SHORTCUTS to R.string.show_pinned_shortcuts,
    DizzifySettingsKeys.SETTING_SHOW_PINNED_SHORTCUTS_DESC to R.string.show_pinned_shortcuts_desc,
)

fun dizzifyStringResourceProvider(context: Context): StringResourceProvider =
    AndroidStringResourceProvider(context) { key -> settingsResources[key] ?: 0 }
