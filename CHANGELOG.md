# Version 1.6.0

Mod:

- [ ] Add spacial audio support
- [ ] Support single-player mode ([#31](https://github.com/arsmotorin/dreamdisplays/issues/59))
- [ ] Support Fabric servers ([#56](https://github.com/arsmotorin/dreamdisplays/issues/56))
- [ ] Add toggle button for window focus
- [ ] Add toggle button for video repeating
- [ ] Add action bar messages when player looks at display
- [ ] Support natives

Plugin:

- [ ] Add config migrator from ancient versions of the plugin

# Version 1.5.0

Mod:

- [x] Add video timeline
- [x] Smoother video playback and optimizations
- [x] Support lying and ceiling displays (server must have 1.5.0 or newer plugin version)
- [x] Support timecodes in YouTube links
- [x] Make buttons smaller for better UI
- [x] Rewrite main part of the mod in Kotlin
- [x] Ignore radio parameter in YouTube links
- [x] Fix NeoForge Gradle conflict while syncing with IntelliJ IDEA Gradle plugin
- [x] Fix null-pointer exception when window focus tries to work on not initialized Minecraft
- [x] Fix client crash when server restarts while player is connected

Plugin:

- [x] Support lying and ceiling displays
- [x] Support `&start_radio=x` and `&t=x` YouTube parameters

# Version 1.4.0

Mod:

- [x] Support Quilt
- [x] Update dependencies
- [x] Improve building workflow
- [x] Cleanup codebase

Plugin:

- [x] Fix display directions not being created properly in some cases
- [x] Cleanup codebase

# Version 1.3.2

Mod:

- [x] Fix display deletion not working properly

Plugin:

- [x] No changes

# Version 1.3.1

Mod:

- [x] Fix displays disappearing permanently when player walks out of render distance
- [x] Displays now load immediately when entering render distance
- [x] Fewer logs
- [x] Updated dependencies

Plugin:

- [x] Detect snapshot versions correctly

# Version 1.3.0

Mod:

- [x] We've created [Discord server](https://discord.gg/uwMMZ2KWk6)!
- [x] Smoother video playback and some optimizations
- [x] Video brightness control
- [x] Store paused state of display
- [x] Change maximum of render distance to 128 blocks ([#59](https://github.com/arsmotorin/dreamdisplays/issues/59))
- [x] Change maximum volume to 200% ([#60](https://github.com/arsmotorin/dreamdisplays/issues/60))
- [x] Fix playing videos after changing quality
- [x] Support CurseForge releases
- [x] Documentation in codebase of the mod

Plugin:

- [x] Refactors and small improvements
- [x] Documentation in codebase of the plugin
- [x] Improve update logic and fix ignoring mod versions ([#63](https://github.com/arsmotorin/dreamdisplays/issues/63))

# Version 1.2.0

Mod:

- [x] New, refreshed logo
- [x] All messages from plugin are in client's language now
- [x] Add missing messages for some commands
- [x] Remove client command `/displays` and move its functionality to plugin's `/display` command
- [x] Improve README and wiki
- [x] Show report button only if server has configured webhook URL
- [x] Fix an issue when after re-enabling displays they don't load until relog

Plugin:

- [x] New languages: Belarusian, Czech, German and Hebrew for plugin messages
- [x] Improve permissions handling for `/display create` and `/display video`
- [x] Add permission message when player lacks permission
- [x] Improve `/display list` command output
- [x] Add `/display help` and `/display stats` commands
- [x] Add links to some messages
- [x] Fix reporting message not showing correctly
- [x] Fix wrong command usage message logic

# Version 1.1.3

Mod:

- [x] Fix sync packet registration issues
- [x] Fix video playback time saving for non-synced displays
- [x] Fix texture errors when changing video quality
- [x] Fix NeoForge screen loading on server join

Plugin:

- [x] No changes

# Version 1.1.2

Mod:

- [x] Fix missing translations
- [x] Fix snapshot version detection as stable
- [x] Better releases system of mod
- [x] Update mappings

Plugin:

- [x] Add message when client doesn't have the mod installed
- [x] Better releases system of mod

# Version 1.1.1

Mod:

- [x] Fix display desynchronization with server and client
- [x] A bit improved screen rendering
- [x] Less logging
- [x] Code cleanup

Plugin:

- [x] Fix display desynchronization with server and client

# Version 1.1.0

Mod:

- [x] Support 1.21.11 version
- [x] Support NeoForge
- [x] Huge reduction of CPU usage, more stable and optimized
- [x] Store all displays from the servers
- [x] Support more YouTube links
- [x] Don’t mute displays on alt-tab by default
- [x] Better volume UI
- [x] Switched to Mojang mappings
- [x] Improved overall code quality
- [x] Enhanced logging
- [x] Improved wiki

Plugin:

- [x] Fixed repeated update notifications when switching dimensions
- [x] Refined, new configuration
- [x] Enhanced particle effects for selections
- [x] Created messages for empty report, display deletion, etc.
- [x] Separated update logic between mod and plugin
- [x] Plugin rewritten in Kotlin
- [x] Improved overall code quality
- [x] Corrected premium permission name
- [x] Removed hourly update notifications from the console
- [x] bStats

# Version 1.0.8

Mod:

- [x] Expanded max quality from 1080p to 4K
- [x] Tips for removing and reporting display
- [x] Warn player when switching to 1080p+

Plugin:

- [x] Support Spigot and Bukkit servers
- [x] New commands: /display list and /display reload
- [x] More languages for plugin configuration
- [x] .toml format for configuration files

# Version 1.0.7

Mod:

- [x] Discontinue FrogDisplays channel support

Plugin:

- [x] Folia support
- [x] Better comments in plugin configuration
- [x] Discontinue FrogDisplays channel support

# Version 1.0.6

Mod:

- [x] Added Hebrew, Czech and Belarussian languages support
- [x] Disabled volume relativity to Minecraft's volume
- [x] Vanilla language system
- [x] Improved volume configuration options
- [x] Default video quality is now 720p instead of 480p
- [x] Fixed GStreamer dead link

Plugin:

- [x] Bump version

# Version 1.0.5

Mod:

- [x] Added multi-language support for Russian, Ukrainian, Polish and German

Plugin:

- [x] Bump version

# Version 1.0.4

Mod:

- [x] Release channel is now Beta for Fabric
- [x] Project is now pen-source with LGPL-3.0 license
- [x] English is now the default language instead of Russian
- [x] New documentation with proper project information
- [x] Cleaned up redundant code and improved code quality
- [x] Added support for old mod versions
- [x] Added mod information
- [x] New icon

Plugin:

- [x] Release channel is now Release
- [x] English as the default language
- [x] New configuration
- [x] New mod name Dream Displays
- [x] Added support for old mod clients
- [x] Added plugin information

# Version 1.0.3

Mod:

- [x] Ignore GStreamer library if macOS

Plugin:

- [x] First public version

# Version 1.0.2

Mod:

- [x] Added other languages for videos

Plugin:

- [x] Bump version (not public)

# Version 1.0.1

Mod:

- [x] Fix client crash

Plugin:

- [x] Bump version (not public)

# Version 1.0.0

Mod:

- [x] First version

Plugin:

- [x] First version (not public)
