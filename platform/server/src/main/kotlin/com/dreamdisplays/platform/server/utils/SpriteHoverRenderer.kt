package com.dreamdisplays.platform.server.utils

import io.github.arnodoelinger.platformweaver.PaperOnly
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.`object`.ObjectContents
import org.bukkit.Material

/**
 * Renders an inline item-sprite icon with an item tooltip using the Adventure object / sprite hover
 * API introduced in Minecraft 1.21.11 ([ObjectContents], [HoverEvent.showItem]).
 */
@PaperOnly
internal object SpriteHoverRenderer {
    /**
     * Block items live in the `minecraft:blocks` atlas (`block/<name>`); pure items live in
     * `minecraft:items` (`item/<name>`).
     */
    fun render(mat: Material): Component {
        val ns = mat.key().namespace()
        val name = mat.key().value()
        val atlas: Key
        val spriteKey: Key
        if (mat.isBlock) {
            atlas = Key.key("minecraft", "blocks")
            spriteKey = Key.key(ns, "block/$name")
        } else {
            atlas = Key.key("minecraft", "items")
            spriteKey = Key.key(ns, "item/$name")
        }
        return Component.`object`(ObjectContents.sprite(atlas, spriteKey))
            .hoverEvent(HoverEvent.showItem(mat.key(), 1))
    }
}
