# SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
# SPDX-License-Identifier: GPL-3.0-only
"""用上游 PC 版 LDDC 的解析器/转换器生成 golden 数据，供 Android 端单元测试逐字节比对。

用法：
    python tools/golden/gen_golden.py
输出：
    app/src/test/resources/golden.json
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE / "pyside_stub"))
sys.path.insert(0, str(ROOT / "upstream"))

from LDDC.common.data.config import cfg  # noqa: E402
from LDDC.common.models import Lyrics, LyricsFormat, LyricInfo, SongInfo, Source  # noqa: E402
from LDDC.core.parser.krc import krc2mdata  # noqa: E402
from LDDC.core.parser.lrc import lrc2mdata  # noqa: E402
from LDDC.core.parser.qrc import qrc2data  # noqa: E402
from LDDC.core.parser.utils import judge_lyrics_type  # noqa: E402
from LDDC.core.parser.yrc import yrc2data  # noqa: E402
from LDDC.core.converter import convert2  # noqa: E402

cfg["langs_order"] = ["orig", "ts", "roma"]
cfg["lrc_ms_digit_count"] = 3
cfg["add_end_timestamp_line"] = False
cfg["last_ref_line_time_sty"] = 0

SAMPLE_LRC_VERBATIM = """[ti:测试歌曲]
[ar:测试歌手]
[00:00.00]<00:00.00>Hel<00:00.50>lo <00:01.00>World<00:01.50>
[00:02.00]<00:02.00>第二<00:02.60>行<00:03.20>歌词<00:03.90>
[00:04.00]没有逐字的一行
"""

SAMPLE_LRC_MULTI = """[00:00.00]第一行原文
[00:02.00]第二行原文
[00:04.00]第三行原文
[00:00.00]第一行译文
[00:02.00]第二行译文
[00:04.00]第三行译文
"""

SAMPLE_YRC = """[0,2000](0,500,0)Hello (500,500,0)World
[2000,2000](2000,600,0)第二(2600,600,0)行(3200,800,0)歌词
"""

SAMPLE_QRC = """<?xml version="1.0" encoding="utf-8"?>
<QrcInfos>
<QrcHeadInfo SaveTime="1" Version="100"/>
<LyricInfo LyricCount="1">
<Lyric_1 LyricType="1" LyricContent="[ti:测试歌曲]
[0,2000]Hello (0,900)World(900,1100)
[2000,2500]第二(2000,800)行(2800,900)歌词(3700,800)
"/>
</LyricInfo>
</QrcInfos>"""

SAMPLE_KRC = """krc18
[id:$00000000]
[ar:测试歌手]
[ti:测试歌曲]
[0,2000]<0,900,0>Hel<900,600,0>lo <1500,500,0>World
[2000,2500]<0,900,0>第二<900,700,0>行<1600,900,0>歌词
"""

FORMATS = [
    ("VERBATIMLRC", LyricsFormat.VERBATIMLRC),
    ("LINEBYLINELRC", LyricsFormat.LINEBYLINELRC),
    ("ENHANCEDLRC", LyricsFormat.ENHANCEDLRC),
    ("SRT", LyricsFormat.SRT),
    ("ASS", LyricsFormat.ASS),
]


def build_lyrics(source, tags, data_map, title="测试歌曲", duration_ms=30000):
    info = LyricInfo(Source.QM, SongInfo(Source.QM, title=title, artist=None, duration=duration_ms // 1000))
    lyrics = Lyrics(info)
    lyrics.tags = tags
    for lang, data in data_map.items():
        lyrics[lang] = data
        lyrics.types[lang] = judge_lyrics_type(data)
    return lyrics


def gen():
    cases = []

    def add(name, input_text, source, tags, data_map, langs):
        lyrics = build_lyrics(source, tags, data_map)
        outputs = {}
        for fmt_name, fmt in FORMATS:
            outputs[fmt_name] = convert2(lyrics, langs=langs, lyrics_format=fmt)
        cases.append({
            "name": name,
            "input": input_text,
            "source": source.name,
            "langs": langs,
            "outputs": outputs,
        })

    tags, mdata = lrc2mdata(SAMPLE_LRC_VERBATIM, Source.NE)
    add("lrc_verbatim", SAMPLE_LRC_VERBATIM, Source.NE, tags, mdata, ["orig"])

    tags, mdata = lrc2mdata(SAMPLE_LRC_MULTI, Source.QM)
    add("lrc_multi_lang", SAMPLE_LRC_MULTI, Source.QM, tags, mdata, ["orig", "ts"])

    yrc_data = yrc2data(SAMPLE_YRC)
    add("yrc", SAMPLE_YRC, Source.NE, {}, {"orig": yrc_data}, ["orig"])

    tags, qrc_data = qrc2data(SAMPLE_QRC)
    add("qrc", SAMPLE_QRC, Source.QM, tags, {"orig": qrc_data}, ["orig"])

    tags, krc_map = krc2mdata(SAMPLE_KRC)
    add("krc", SAMPLE_KRC, Source.KG, tags, krc_map, ["orig"])

    return {"generator": "LDDC upstream parsers", "cases": cases}


if __name__ == "__main__":
    result = gen()
    target = ROOT / "android" / "app" / "src" / "test" / "resources" / "golden.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {target}")
    for case in result["cases"]:
        print(f"  {case['name']}: " + ", ".join(f"{k}={len(v)}" for k, v in case["outputs"].items()))
