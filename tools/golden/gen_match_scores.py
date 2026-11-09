# SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
# SPDX-License-Identifier: GPL-3.0-only
"""用上游 algorithm.py 生成匹配分数基准，供 Android 端逐值比对。

用法：python tools/golden/gen_match_scores.py
输出：app/src/test/resources/match_scores.json
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE / "pyside_stub"))
sys.path.insert(0, str(ROOT / "upstream"))

from LDDC.core.algorithm import calculate_artist_score, calculate_title_score  # noqa: E402

TITLE_PAIRS = [
    ("夜曲", "夜曲"),
    ("夜曲", "夜曲 (Live)"),
    ("夜曲", "夜曲 - Live"),
    ("爱错", "爱错(Live)"),
    ("起风了", "起风了（伴奏）"),
    ("Hello", "Hello - Remix"),
    ("Asterlore", "アスタロア"),
    ("千本桜", "千本桜 (TV size)"),
    ("Lemon", "Lemon (Anime Ver.)"),
    ("Sayonara", "さよなら"),
    ("Song Name", "Song  Name"),
    ("完全不同的标题", "Totally Different"),
    ("A", ""),
]

ARTIST_PAIRS = [
    ("周杰伦", "周杰伦"),
    ("王力宏", "王力宏/Leehom"),
    ("KOTOKO", "KOTOKO"),
    ("鈴木このみ", "铃木木乃美 (鈴木このみ)"),
    ("A feat. B", "A"),
    ("周杰伦、费玉清", "周杰伦/费玉清"),
    ("Various Artists", "群星"),
    ("米津玄師", "米津玄师"),
    ("A・B", "A&B"),
    ("", "周杰伦"),
]


def main() -> None:
    titles = [{"a": a, "b": b, "score": round(calculate_title_score(a, b), 4)} for a, b in TITLE_PAIRS]
    artists = [{"a": a, "b": b, "score": round(calculate_artist_score(a, b), 4)} for a, b in ARTIST_PAIRS]
    result = {"titles": titles, "artists": artists}
    target = ROOT / "android" / "app" / "src" / "test" / "resources" / "match_scores.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {target}")
    for item in titles:
        print(f"  title  {item['a']!r} vs {item['b']!r} -> {item['score']}")
    for item in artists:
        print(f"  artist {item['a']!r} vs {item['b']!r} -> {item['score']}")


if __name__ == "__main__":
    main()
