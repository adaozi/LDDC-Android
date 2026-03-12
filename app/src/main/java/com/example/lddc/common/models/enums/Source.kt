package com.example.lddc.common.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class Source {
    MULTI,
    QM,
    KG,
    NE,
    LRCLIB,
    LOCAL;

    val displayName: String
        get() = when (this) {
            MULTI -> "聚合"
            QM -> "QQ音乐"
            KG -> "酷狗音乐"
            NE -> "网易云音乐"
            LRCLIB -> "Lrclib"
            LOCAL -> "本地"
        }

}
