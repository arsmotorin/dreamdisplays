package com.dreamdisplays.platform.client.render

import com.dreamdisplays.api.util.WireEnum
import com.dreamdisplays.api.util.wireEnumValueOf

/** Render backend reported by the active Minecraft render device. */
enum class RenderBackend(override val wire: String) : WireEnum {
    /** OpenGL renderer. Legacy renderer. */
    OPENGL("opengl"),

    /** Vulkan renderer. New renderer. */
    VULKAN("vulkan"),

    /**
     * Vulkan renderer, but as a mod.
     *
     * @see <a href="https://modrinth.com/mod/vulkanmod">VulkanMod</a>
     */
    VULKAN_MOD("vulkanmod"),

    /** Other renderer. */
    OTHER("other"),

    /** Unknown renderer. */
    UNKNOWN("unknown");

    companion object {
        /** Returns the enum value corresponding to the given wire value, or [UNKNOWN] if not found. */
        fun fromWire(raw: String?): RenderBackend = wireEnumValueOf(raw, UNKNOWN)
    }
}
