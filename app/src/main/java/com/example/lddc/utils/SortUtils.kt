package com.example.lddc.utils

import com.example.lddc.model.LocalMusicInfo
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination

/**
 * 排序工具类
 *
 * 提供音乐列表的排序功能，使用 pinyin4j 进行中文转拼音
 */
object SortUtils {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    /**
     * 按标题排序（拼音/字母顺序，符号开头的排后面）
     *
     * 排序规则：
     * - 0_ 前缀：中文字符开头（按拼音排序）
     * - 1_ 前缀：英文字母开头
     * - 2_ 前缀：数字开头
     * - 9_ 前缀：符号开头（排最后）
     *
     * @param musicList 音乐列表
     * @return 排序后的列表
     */
    fun sortByTitle(musicList: List<LocalMusicInfo>): List<LocalMusicInfo> {
        return musicList.sortedWith(compareBy { music ->
            getSortKey(music.title)
        })
    }

    /**
     * 获取排序键
     *
     * @param str 字符串
     * @return 排序键
     */
    private fun getSortKey(str: String): String {
        if (str.isEmpty()) return ""

        // 将整个字符串转换为拼音/字母形式用于排序
        val pinyinStr = convertToPinyin(str)

        val firstChar = str.first()
        firstChar.code

        // 根据首字符类型确定排序前缀
        val prefix = when {
            // 中文字符（CJK Unified Ideographs 范围）- 优先级最高
            isChineseChar(firstChar) -> "0_"
            // 英文字母
            firstChar in 'a'..'z' || firstChar in 'A'..'Z' -> "1_"
            // 数字
            firstChar in '0'..'9' -> "2_"
            // 其他符号开头的排后面
            else -> "9_"
        }

        return prefix + pinyinStr.lowercase()
    }

    /**
     * 将字符串转换为拼音
     *
     * @param str 字符串
     * @return 拼音字符串
     */
    private fun convertToPinyin(str: String): String {
        val sb = StringBuilder()

        for (char in str) {
            when {
                // 中文字符
                isChineseChar(char) -> {
                    val pinyin = getPinyin(char)
                    sb.append(pinyin)
                }
                // 英文字母
                char in 'a'..'z' || char in 'A'..'Z' -> {
                    sb.append(char.lowercase())
                }
                // 数字
                char in '0'..'9' -> {
                    sb.append(char)
                }
                // 其他字符（空格、符号等）
                else -> {
                    sb.append(char)
                }
            }
        }

        return sb.toString()
    }

    /**
     * 判断是否为中文字符
     *
     * @param char 字符
     * @return 是否为中文字符
     */
    private fun isChineseChar(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF ||
               code in 0x3400..0x4DBF ||
               code in 0x20000..0x2A6DF ||
               code in 0x2A700..0x2B73F ||
               code in 0x2B740..0x2B81F ||
               code in 0x2B820..0x2CEAF ||
               code in 0x2CEB0..0x2EBEF ||
               code in 0x30000..0x3134F
    }

    /**
     * 获取单个中文字符的拼音
     *
     * @param char 中文字符
     * @return 拼音，如果转换失败返回原字符
     */
    private fun getPinyin(char: Char): String {
        return try {
            val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
            pinyinArray?.firstOrNull() ?: char.toString()
        } catch (e: BadHanyuPinyinOutputFormatCombination) {
            char.toString()
        }
    }
}
