// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
//
// TagLib JNI 桥：为 Kotlin 侧提供最小的标签读写接口。
// 对齐上游 LDDC/core/song_info.py 的 read_lyrics / write_lyrics 行为：
//   - ID3v2 系（MP3 / DSF / DFF / AIFF / TTA / WAV）：USLT 存全文 + SYLT 存字级时间戳
//   - Vorbis / APEv2 / MP4 / ASF：写入 LYRICS 字段（MP4 映射到 (c)lyr，ASF 映射到 WM/LYRICS）
// 所有字符串以 UTF-8 字节数组进出，避免 JNI Modified-UTF8 对 4 字节字符（emoji）的破坏。

#include <jni.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

// 注意：TagLib 源码树内头文件为"扁平"布局（安装后才合并到 include/taglib），
// 因此这里使用扁平包含名，CMake 已把所有含头文件的目录加入搜索路径。
#include <taglib.h>
#include <fileref.h>
#include <tfile.h>
#include <tag.h>
#include <tpropertymap.h>
#include <tstring.h>
#include <tstringlist.h>

#include <mpegfile.h>
#include <id3v2tag.h>
#include <id3v2frame.h>
#include <unsynchronizedlyricsframe.h>
#include <synchronizedlyricsframe.h>

#include <dsffile.h>
#include <dsdifffile.h>
#include <aifffile.h>
#include <wavfile.h>
#include <trueaudiofile.h>

namespace {

constexpr int RESULT_OK = 0;
constexpr int RESULT_UNSUPPORTED = 1;
constexpr int RESULT_IO_ERROR = 2;
constexpr int RESULT_BAD_ARGUMENT = 3;

std::string toUtf8(JNIEnv *env, jbyteArray array) {
    if (array == nullptr) {
        return {};
    }
    const jsize len = env->GetArrayLength(array);
    std::string out(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(out.data()));
    }
    return out;
}

jbyteArray toByteArray(JNIEnv *env, const std::string &value) {
    auto *result = env->NewByteArray(static_cast<jsize>(value.size()));
    if (result != nullptr && !value.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(value.size()),
                                reinterpret_cast<const jbyte *>(value.data()));
    }
    return result;
}

TagLib::String tagString(const std::string &utf8) {
    return TagLib::String(utf8, TagLib::String::UTF8);
}

std::string jsonEscape(const std::string &value) {
    std::string out;
    out.reserve(value.size() + 8);
    for (const char c : value) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                    out += buf;
                } else {
                    out += c;
                }
        }
    }
    return out;
}

/** 取得该文件的 ID3v2 标签句柄；不支持的格式返回 nullptr */
TagLib::ID3v2::Tag *id3TagOf(TagLib::File *file) {
    if (auto *f = dynamic_cast<TagLib::MPEG::File *>(file)) {
        return f->ID3v2Tag(true);
    }
    if (auto *f = dynamic_cast<TagLib::DSF::File *>(file)) {
        return f->tag();  // DSF::File::tag() 直接返回 ID3v2::Tag*
    }
    if (auto *f = dynamic_cast<TagLib::DSDIFF::File *>(file)) {
        return f->ID3v2Tag();
    }
    if (auto *f = dynamic_cast<TagLib::RIFF::AIFF::File *>(file)) {
        return f->tag();
    }
    if (auto *f = dynamic_cast<TagLib::TrueAudio::File *>(file)) {
        return f->ID3v2Tag(true);
    }
    if (auto *f = dynamic_cast<TagLib::RIFF::WAV::File *>(file)) {
        return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    }
    return nullptr;
}

bool saveWithVersion(TagLib::File *file, int id3Version) {
    const TagLib::ID3v2::Version version = (id3Version == 3) ? TagLib::ID3v2::v3 : TagLib::ID3v2::v4;
    if (auto *f = dynamic_cast<TagLib::MPEG::File *>(file)) {
        return f->save(TagLib::MPEG::File::ID3v2, TagLib::MPEG::File::StripOthers, version);
    }
    if (auto *f = dynamic_cast<TagLib::DSF::File *>(file)) {
        return f->save(version);
    }
    return file->save();
}

/** 写入 USLT（全文）与可选 SYLT（字级时间戳） */
void writeId3Lyrics(TagLib::ID3v2::Tag *id3, const std::string &text,
                    JNIEnv *env, jintArray times, jobjectArray words) {
    id3->removeFrames("USLT");
    auto *uslt = new TagLib::ID3v2::UnsynchronizedLyricsFrame(TagLib::String::UTF8);
    uslt->setTextEncoding(TagLib::String::UTF8);
    uslt->setLanguage(TagLib::ByteVector("chi"));
    uslt->setText(tagString(text));
    id3->addFrame(uslt);

    if (times == nullptr || words == nullptr) {
        return;
    }
    const jsize count = env->GetArrayLength(times);
    if (count <= 0 || env->GetArrayLength(words) != count) {
        return;
    }

    std::vector<jint> ms(static_cast<size_t>(count));
    env->GetIntArrayRegion(times, 0, count, ms.data());

    id3->removeFrames("SYLT");
    auto *sylt = new TagLib::ID3v2::SynchronizedLyricsFrame(TagLib::String::UTF8);
    sylt->setTextEncoding(TagLib::String::UTF8);
    sylt->setLanguage(TagLib::ByteVector("chi"));
    sylt->setType(TagLib::ID3v2::SynchronizedLyricsFrame::Lyrics);
    sylt->setTimestampFormat(TagLib::ID3v2::SynchronizedLyricsFrame::AbsoluteMilliseconds);

    TagLib::ID3v2::SynchronizedLyricsFrame::SynchedTextList list;
    for (jsize i = 0; i < count; ++i) {
        auto *wordArray = static_cast<jbyteArray>(env->GetObjectArrayElement(words, i));
        const std::string word = toUtf8(env, wordArray);
        if (wordArray != nullptr) {
            env->DeleteLocalRef(wordArray);
        }
        if (ms[static_cast<size_t>(i)] < 0) {
            continue;
        }
        list.append(TagLib::ID3v2::SynchronizedLyricsFrame::SynchedText(
                static_cast<unsigned int>(ms[static_cast<size_t>(i)]), tagString(word)));
    }
    sylt->setSynchedText(list);
    id3->addFrame(sylt);
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_org_lddc_android_data_tag_TagLibBridge_writeLyrics(
        JNIEnv *env, jobject /*thiz*/, jbyteArray path, jbyteArray text,
        jintArray syltTimes, jobjectArray syltWords, jint id3Version) {
    const std::string filePath = toUtf8(env, path);
    const std::string lyricsText = toUtf8(env, text);
    if (filePath.empty() || lyricsText.empty()) {
        return RESULT_BAD_ARGUMENT;
    }

    TagLib::FileRef ref(filePath.c_str(), true, TagLib::AudioProperties::Fast);
    if (ref.isNull() || ref.file() == nullptr) {
        return RESULT_UNSUPPORTED;
    }
    TagLib::File *file = ref.file();

    bool saved;
    if (auto *id3 = id3TagOf(file)) {
        writeId3Lyrics(id3, lyricsText, env, syltTimes, syltWords);
        saved = saveWithVersion(file, id3Version);
    } else {
        TagLib::PropertyMap props = file->properties();
        props.replace("LYRICS", tagString(lyricsText));
        file->setProperties(props);
        saved = file->save();
    }
    return saved ? RESULT_OK : RESULT_IO_ERROR;
}

JNIEXPORT jbyteArray JNICALL
Java_org_lddc_android_data_tag_TagLibBridge_readLyrics(
        JNIEnv *env, jobject /*thiz*/, jbyteArray path) {
    const std::string filePath = toUtf8(env, path);
    if (filePath.empty()) {
        return nullptr;
    }
    TagLib::FileRef ref(filePath.c_str(), true, TagLib::AudioProperties::Fast);
    if (ref.isNull() || ref.file() == nullptr) {
        return nullptr;
    }
    TagLib::File *file = ref.file();

    // 1) ID3：优先 USLT，其次 SYLT
    if (auto *id3 = id3TagOf(file)) {
        const auto uslt = id3->frameList("USLT");
        for (auto *frame : uslt) {
            if (auto *u = dynamic_cast<TagLib::ID3v2::UnsynchronizedLyricsFrame *>(frame)) {
                const std::string value = u->text().to8Bit(true);
                if (!value.empty()) {
                    return toByteArray(env, value);
                }
            }
        }
        const auto sylt = id3->frameList("SYLT");
        for (auto *frame : sylt) {
            if (auto *s = dynamic_cast<TagLib::ID3v2::SynchronizedLyricsFrame *>(frame)) {
                std::string merged;
                for (const auto &item : s->synchedText()) {
                    merged += item.text.to8Bit(true);
                }
                if (!merged.empty()) {
                    return toByteArray(env, merged);
                }
            }
        }
    }

    // 2) Vorbis / APE / MP4 / ASF
    const TagLib::PropertyMap props = file->properties();
    const auto it = props.find("LYRICS");
    if (it != props.end() && !it->second.isEmpty()) {
        return toByteArray(env, it->second.front().to8Bit(true));
    }
    return nullptr;
}

JNIEXPORT jboolean JNICALL
Java_org_lddc_android_data_tag_TagLibBridge_hasLyrics(
        JNIEnv *env, jobject thiz, jbyteArray path) {
    jbyteArray result = Java_org_lddc_android_data_tag_TagLibBridge_readLyrics(env, thiz, path);
    if (result == nullptr) {
        return JNI_FALSE;
    }
    env->DeleteLocalRef(result);
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_org_lddc_android_data_tag_TagLibBridge_readAudioInfo(
        JNIEnv *env, jobject /*thiz*/, jbyteArray path) {
    const std::string filePath = toUtf8(env, path);
    if (filePath.empty()) {
        return nullptr;
    }
    TagLib::FileRef ref(filePath.c_str(), true, TagLib::AudioProperties::Fast);
    if (ref.isNull() || ref.file() == nullptr) {
        return nullptr;
    }

    std::string title;
    std::string artist;
    std::string album;
    int durationMs = 0;
    if (const TagLib::Tag *tag = ref.tag()) {
        title = tag->title().to8Bit(true);
        artist = tag->artist().to8Bit(true);
        album = tag->album().to8Bit(true);
    }
    if (const TagLib::AudioProperties *props = ref.audioProperties()) {
        durationMs = props->lengthInMilliseconds();
    }

    std::string json = "{";
    json += "\"title\":\"" + jsonEscape(title) + "\",";
    json += "\"artist\":\"" + jsonEscape(artist) + "\",";
    json += "\"album\":\"" + jsonEscape(album) + "\",";
    json += "\"duration\":" + std::to_string(durationMs);
    json += "}";
    return toByteArray(env, json);
}

JNIEXPORT jstring JNICALL
Java_org_lddc_android_data_tag_TagLibBridge_nativeVersion(JNIEnv *env, jobject /*thiz*/) {
    const std::string version = "TagLib/" + std::to_string(TAGLIB_MAJOR_VERSION) + "." +
                                std::to_string(TAGLIB_MINOR_VERSION) + "." +
                                std::to_string(TAGLIB_PATCH_VERSION);
    return env->NewStringUTF(version.c_str());
}

}  // extern "C"
