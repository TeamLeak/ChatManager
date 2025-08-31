package com.github.saintedlittle.util

import net.luckperms.api.LuckPerms
import net.luckperms.api.model.group.Group
import net.luckperms.api.model.user.User
import net.luckperms.api.node.Node
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.query.QueryOptions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

object LPUtil {
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    fun prefixSuffix(lp: LuckPerms?, player: Player): Pair<Component, Component> {
        if (lp == null) return Component.empty() to Component.empty()
        return try {
            val user: User = lp.userManager.getUser(player.uniqueId) ?: return Component.empty() to Component.empty()
            val q: QueryOptions = lp.contextManager.getQueryOptions(user)
                .orElseGet { lp.contextManager.staticQueryOptions }

            val deepest = deepestGroupByInheritance(lp, user, q)
                ?: highestWeightedGroup(lp, user)
                ?: return Component.empty() to Component.empty()

            val prefixText = deepest.cachedData.getMetaData(q).prefix ?: ""
            val suffixText = deepest.cachedData.getMetaData(q).suffix ?: ""

            val prefix = if (prefixText.isNotEmpty()) legacy.deserialize(prefixText) else Component.empty()
            val suffix = if (suffixText.isNotEmpty()) legacy.deserialize(suffixText) else Component.empty()

            prefix to suffix
        } catch (_: Exception) {
            Component.empty() to Component.empty()
        }
    }

    private fun deepestGroupByInheritance(lp: LuckPerms, user: User, q: QueryOptions): Group? {
        val gm = lp.groupManager

        val userGroupNames: Set<String> = user.nodes.stream()
            .filter(NodeType.INHERITANCE::matches)
            .map(NodeType.INHERITANCE::cast)
            .filter { nodeMatchesContexts(it, q) }
            .map { it.groupName }
            .toList()
            .toSet()

        if (userGroupNames.isEmpty()) return null

        val depthMemo = HashMap<String, Int>()

        fun depthOf(groupName: String, stack: MutableSet<String>): Int {
            depthMemo[groupName]?.let { return it }
            if (!stack.add(groupName)) {
                return 0
            }
            val g = gm.getGroup(groupName) ?: run {
                stack.remove(groupName); return 0
            }

            val parents: List<String> = g.nodes.stream()
                .filter(NodeType.INHERITANCE::matches)
                .map(NodeType.INHERITANCE::cast)
                .filter { nodeMatchesContexts(it, q) }
                .map { it.groupName }
                .toList()

            var maxParentDepth = -1
            for (p in parents) {
                val d = depthOf(p, stack)
                if (d > maxParentDepth) maxParentDepth = d
            }
            stack.remove(groupName)

            val dHere = maxParentDepth + 1
            depthMemo[groupName] = dHere
            return dHere
        }

        var best: Group? = null
        var bestDepth = Int.MIN_VALUE
        var bestWeight = Int.MIN_VALUE

        for (name in userGroupNames) {
            val g = gm.getGroup(name) ?: continue
            val d = depthOf(name, mutableSetOf())
            val w = g.weight.orElse(0)

            val better = when {
                d > bestDepth -> true
                d < bestDepth -> false
                w > bestWeight -> true
                w < bestWeight -> false
                else -> (g.name < (best?.name ?: "\uFFFF"))
            }

            if (better) {
                best = g
                bestDepth = d
                bestWeight = w
            }
        }
        return best
    }

    private fun highestWeightedGroup(lp: LuckPerms, user: User): Group? {
        val gm = lp.groupManager
        var best: Group? = null
        var bestWeight = Int.MIN_VALUE

        user.nodes.stream()
            .filter(NodeType.INHERITANCE::matches)
            .map(NodeType.INHERITANCE::cast)
            .forEach { inh ->
                val g = gm.getGroup(inh.groupName) ?: return@forEach
                val w = g.weight.orElse(0)
                if (w > bestWeight || (w == bestWeight && g.name < (best?.name ?: "\uFFFF"))) {
                    best = g
                    bestWeight = w
                }
            }
        return best
    }

    private fun nodeMatchesContexts(node: Node, q: QueryOptions): Boolean {
        val nodeCtx = node.contexts
        val cur = q.context() // ✅ правильный метод
        for (ctx in nodeCtx) {
            val values = cur.getValues(ctx.key)
            if (!values.contains(ctx.value)) return false
        }
        return true
    }
}
