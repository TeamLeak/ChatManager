package com.github.saintedlittle.chat

data class Channel(
    val id: String,
    val name: String,
    val trigger: String,
    val sendPerm: String?,
    val receivePerm: String?,
    val range: Int,            // -1 = global
    val format: String
)
