package com.example.lddc.common.utils

object TextUtils {

    private val symbolMap = mapOf(
        "（" to "(", "）" to ")", "：" to ":", "！" to "!",
        "？" to "?", "／" to "/", "＆" to "&", "＊" to "*",
        "＠" to "@", "＃" to "#", "＄" to "$", "％" to "%",
        "＼" to "\\", "｜" to "|", "＝" to "=", "＋" to "+",
        "－" to "-", "＜" to "<", "＞" to ">", "［" to "[",
        "］" to "]", "｛" to "{", "｝" to "}"
    )

    fun unifySymbols(text: String): String {
        var result = text.trim()
        symbolMap.forEach { (k, v) ->
            result = result.replace(k, v)
        }
        return result.replace(Regex("\\s+"), " ")
    }

}
