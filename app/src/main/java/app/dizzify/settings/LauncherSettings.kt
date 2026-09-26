package app.dizzify.settings

import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable

@CategoryDefinition(order = 0)
object General

@CategoryDefinition(order = 1)
object Apps

@CategoryDefinition(order = 2)
object Search

@CategoryDefinition(order = 3)
object Tv

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class SortOrder { AZ, ZA, Recent }

@Serializable
enum class SearchType { Contains, Fuzzy, StartsWith, Exact }

@Serializable
enum class SearchAliasesMode { Off, Transliteration, KeyboardSwap, Both }

@Serializable
enum class TextWeight { Thin, Light, Normal, Medium, Bold, Black }

@Serializable
enum class ItemSpacing { None, Small, Medium, Large }

@Serializable
enum class LabelAlignment { Left, Center, Right }

@Serializable
enum class SearchBarPosition { Top, Bottom }

@Serializable
enum class DefaultScreen { Home, Apps }

@Serializable
enum class NonTvApps { Auto, TvOnly, All }

@SchemaVersion(version = 5)
@Serializable
data class LauncherSettings(
    @Setting(
        title = "Theme",
        category = General::class,
        type = Dropdown::class,
        key = "theme",
        options = ["System", "Light", "Dark"]
    )
    val theme: ThemeMode = ThemeMode.System,

    @Setting(
        title = "Show Banners & Icons",
        description = "Display app banners and icons in launcher",
        category = General::class,
        type = Toggle::class,
        key = "show_app_icons"
    )
    val showAppIcons: Boolean = true,

//    @Setting(
//        title = "Icon Pack",
//        description = "Applies to app icons",
//        category = General::class,
//        type = Dropdown::class,
//        key = "icon_pack",
//        options = ["Default"]
//    )
    val iconPack: String = "default",

    @Setting(
        title = "Sort Order",
        category = Apps::class,
        type = Dropdown::class,
        key = "sort_order",
        options = ["A-Z", "Z-A", "Recent"]
    )
    val sortOrder: SortOrder = SortOrder.AZ,

    @Setting(
        title = "Search Type",
        category = Search::class,
        type = Dropdown::class,
        key = "search_type",
        options = ["Contains", "Fuzzy", "Starts With", "Exact"]
    )
    val searchType: SearchType = SearchType.Contains,

    @Setting(
        title = "Search Aliases",
        category = Search::class,
        type = Dropdown::class,
        key = "search_aliases_mode",
        options = ["Off", "Transliteration", "Keyboard Swap", "Both"]
    )
    val searchAliasesMode: SearchAliasesMode = SearchAliasesMode.Off,

    @Setting(
        title = "Include Package Names in Search",
        category = Search::class,
        type = Toggle::class,
        key = "search_include_package_names"
    )
    val searchIncludePackageNames: Boolean = false,

    @Setting(
        title = "Show Hidden Apps in Search",
        category = Search::class,
        type = Toggle::class,
        key = "show_hidden_apps_on_search"
    )
    val showHiddenAppsOnSearch: Boolean = false,

    @Setting(
        title = "Non-TV Apps",
        description = "TV only hides apps without a leanback entry, which some TV apps lack",
        category = Tv::class,
        type = Dropdown::class,
        key = "show_non_tv_apps",
        options = ["All apps", "Auto", "TV only"]
    )
    val showNonTvApps: NonTvApps = NonTvApps.All,

    @Setting(
        title = "Prefer TV (Leanback) Launch",
        description = "Open TV UI when app supports it (e.g. VLC, Dolphin)",
        category = Tv::class,
        type = Toggle::class,
        key = "prefer_tv_launch"
    )
    val preferTvLaunch: Boolean = true,

    @Setting(
        title = "Show TV Inputs Row",
        description = "HDMI and other TV inputs on the home screen",
        category = Tv::class,
        type = Toggle::class,
        key = "show_tv_inputs"
    )
    val showTvInputs: Boolean = true,

    @Setting(
        title = "Show Continue Watching",
        description = "Watch-Next programs published by your apps",
        category = Tv::class,
        type = Toggle::class,
        key = "show_watch_next"
    )
    val showWatchNext: Boolean = true,

    @Persisted(key = "wallpaper_path")
    val wallpaperPath: String = "",

    @Setting(
        title = "Show System Apps",
        description = "Include pre-installed system apps in the list",
        category = Apps::class,
        type = Toggle::class,
        key = "show_system_apps"
    )
    val showSystemApps: Boolean = true,

    @Setting(
        title = "Show Pinned Shortcuts",
        description = "Display pinned app shortcuts in the app list",
        category = Apps::class,
        type = Toggle::class,
        key = "show_pinned_shortcuts"
    )
    val showPinnedShortcuts: Boolean = false,

    // Settings lock (PIN stored only as salted SHA-256 hash, see SettingsLock).
    @Persisted(key = "lock_settings")
    val lockSettings: Boolean = false,

    @Persisted(key = "settings_lock_pin")
    val settingsLockPin: String = "",

    @Persisted(key = "show_app_names")
    val showAppNames: Boolean = false,

    @Persisted(key = "auto_open_filtered_app")
    val autoOpenFilteredApp: Boolean = false,

    @Persisted(key = "return_to_home_after_app")
    val returnToHomeAfterApp: Boolean = false,

    @Persisted(key = "default_screen")
    val defaultScreen: DefaultScreen = DefaultScreen.Home,

    @Persisted(key = "show_web_search_option")
    val showWebSearchOption: Boolean = true,

    @Persisted(key = "text_size_scale")
    val textSizeScale: Float = 1.0f,

    @Persisted(key = "animation_speed")
    val animationSpeed: Float = 1.0f,

    @Persisted(key = "font_weight")
    val fontWeight: TextWeight = TextWeight.Normal,

    @Persisted(key = "use_system_font")
    val useSystemFont: Boolean = true,

    @Persisted(key = "custom_font_path")
    val customFontPath: String = "",

    @Persisted(key = "item_spacing")
    val itemSpacing: ItemSpacing = ItemSpacing.Small,

    @Persisted(key = "search_results_use_home_font")
    val searchResultsUseHomeFont: Boolean = false,

    @Persisted(key = "search_results_font_size")
    val searchResultsFontSize: Float = 1.0f,

    @Persisted(key = "icon_corner_radius")
    val iconCornerRadius: Int = 0,

    @Persisted(key = "text_color")
    val textColor: Int = 0,

    @Persisted(key = "use_custom_text_color")
    val useCustomTextColor: Boolean = false,

    @Persisted(key = "show_home_screen_icons")
    val showHomeScreenIcons: Boolean = false,

    @Persisted(key = "app_label_alignment")
    val appLabelAlignment: LabelAlignment = LabelAlignment.Left,

    @Persisted(key = "search_results_alignment")
    val searchResultsAlignment: LabelAlignment = LabelAlignment.Left,

    @Persisted(key = "scale_home_apps")
    val scaleHomeApps: Boolean = true,

    @Persisted(key = "home_screen_rows")
    val homeScreenRows: Int = 8,

    @Persisted(key = "home_screen_columns")
    val homeScreenColumns: Int = 4,

    @Persisted(key = "search_bar_position")
    val searchBarPosition: SearchBarPosition = SearchBarPosition.Top,

    @Persisted(key = "reverse_search_results")
    val reverseSearchResults: Boolean = false,

    @Persisted(key = "first_open")
    val firstOpen: Boolean = true,

    @Persisted(key = "first_open_time")
    val firstOpenTime: Long = 0L,

    @Persisted(key = "first_settings_open")
    val firstSettingsOpen: Boolean = true,

    @Persisted(key = "first_hide")
    val firstHide: Boolean = true,

    @Persisted(key = "show_hint_counter")
    val showHintCounter: Int = 1,

    @Persisted(key = "accessibility_consent")
    val accessibilityConsent: Boolean = false,
)
