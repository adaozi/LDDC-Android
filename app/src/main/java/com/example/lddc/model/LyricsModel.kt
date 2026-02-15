package com.example.lddc.model

/**
 * 歌词源枚举
 *
 * 支持的音乐平台：
 * - QM: QQ音乐
 * - NE: 网易云音乐
 * - KG: 酷狗音乐
 */
enum class Source {
    QM,    // QQ音乐
    NE,    // 网易云音乐
    KG     // 酷狗音乐
}

/**
 * 搜索类型枚举
 *
 * 当前仅支持歌曲搜索
 */
enum class SearchType {
    SONG      // 歌曲
}

/**
 * 歌词格式枚举
 *
 * 不同平台使用不同的歌词格式：
 * - VERBATIMLRC: 逐字LRC（精确到每个字的时间戳）
 * - LINEBYLINELRC: 逐行LRC（标准LRC格式）
 * - ENHANCEDLRC: 增强型LRC（支持额外元数据）
 */
enum class LyricsFormat {
    VERBATIMLRC,    // 逐字LRC
    LINEBYLINELRC,  // 逐行LRC
    ENHANCEDLRC,    // 增强型LRC
}

/**
 * 语言枚举
 *
 * 歌曲语言分类：
 * - CHINESE: 中文
 * - ENGLISH: 英语
 * - JAPANESE: 日语
 * - KOREAN: 韩语
 * - INSTRUMENTAL: 纯音乐（无歌词）
 * - OTHER: 其他语言
 */
enum class Language {
    CHINESE,      // 中文
    ENGLISH,      // 英语
    JAPANESE,     // 日语
    KOREAN,       // 韩语
    INSTRUMENTAL, // 纯音乐
    OTHER         // 其他
}

/**
 * 艺术家信息
 *
 * @param name 艺术家/歌手名称
 */
data class Artist(val name: String)

/**
 * 歌曲信息
 *
 * 包含歌曲的基本信息和平台特定字段
 *
 * @param id 歌曲唯一标识
 * @param title 歌曲标题
 * @param artist 主艺术家
 * @param album 专辑名称
 * @param duration 歌曲时长（毫秒）
 * @param source 来源平台
 * @param year 发行年份
 * @param imageUrl 专辑封面图片URL
 * @param mid QQ音乐歌曲MID（仅QQ音乐平台）
 * @param hash 酷狗音乐歌曲Hash（仅酷狗平台）
 * @param albumId 专辑ID（用于获取年代信息）
 * @param subtitle 歌曲副标题
 * @param language 歌曲语言
 * @param singers 所有参与演唱的艺术家列表（合唱歌曲）
 */
data class SongInfo(
    val id: String,
    val title: String,
    val artist: Artist,
    val album: String,
    val duration: Long = 0, // 毫秒
    val source: Source,
    val year: String = "",
    val imageUrl: String = "",
    // 平台特定字段
    val mid: String? = null,      // QQ音乐歌曲mid
    val hash: String? = null,     // 酷狗音乐歌曲hash
    val albumId: String? = null,  // 专辑ID
    val subtitle: String = "",    // 副标题
    val language: Language = Language.OTHER,
    val singers: List<Artist>? = null
)

/**
 * 歌词信息
 *
 * 描述特定版本的歌词元数据
 *
 * @param id 歌词唯一标识
 * @param title 歌曲标题
 * @param artist 艺术家名称
 * @param album 专辑名称
 * @param source 来源平台
 * @param type 歌词类型（如"官方歌词"、"用户上传"）
 * @param language 歌词语言
 * @param accesskey 酷狗音乐歌词访问密钥
 * @param creator 歌词创作者/上传者
 * @param score 歌词质量评分（0-100）
 * @param songinfo 关联的歌曲详细信息
 */
data class LyricInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val source: Source,
    val type: String = "",
    val language: String = "",
    // 平台特定字段
    val accesskey: String? = null,  // 酷狗音乐歌词accesskey
    val creator: String? = null,    // 歌词创作者
    val score: Int = 0,             // 歌词评分
    val songinfo: SongInfo? = null  // 关联的歌曲信息
)

/**
 * 歌词内容
 *
 * 包含歌词文本和多种语言版本
 *
 * @param title 歌曲标题
 * @param artist 艺术家名称
 * @param album 专辑名称
 * @param content 主歌词内容（已解析的标准格式）
 * @param source 来源平台
 * @param duration 歌曲时长（毫秒）
 * @param tags 歌词标签元数据（如[offset:500]）
 * @param orig 原始歌词（未经处理的原格式）
 * @param ts 翻译歌词（Translation）
 * @param roma 罗马音歌词（Romaji，主要用于日语/韩语歌曲）
 * @param types 各字段的歌词类型映射
 */
data class Lyrics(
    val title: String,
    val artist: String,
    val album: String,
    val content: String,
    val source: Source,
    val duration: Long = 0, // 毫秒
    val tags: Map<String, String> = emptyMap(), // 歌词标签
    val orig: String? = null,   // 原始歌词
    val ts: String? = null,     // 翻译歌词
    val roma: String? = null,   // 罗马音歌词
    val types: Map<String, String> = emptyMap() // 歌词类型
)

/**
 * API结果列表包装类
 *
 * 包装API返回的列表数据，实现List接口以便直接使用
 *
 * @param T 列表元素类型
 * @param results 实际的列表数据
 */
class APIResultList<T>(
    val results: List<T>
) : List<T> by results

/**
 * QQ音乐会话信息
 *
 * 用于QQ音乐API的身份验证
 *
 * @param uid 用户ID
 * @param sid 会话ID
 * @param userip 用户IP地址
 */
data class QQMusicSession(
    val uid: String,
    val sid: String,
    val userip: String
)

/**
 * 网易云音乐认证信息
 *
 * @param userId 用户ID
 * @param cookies 登录凭证（包含MUSIC_U等关键cookie）
 * @param expire 过期时间戳（毫秒）
 */
data class NetEaseAuth(
    val userId: String,
    val cookies: Map<String, String>,
    val expire: Long
)
