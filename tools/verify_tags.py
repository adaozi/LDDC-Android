# SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
# SPDX-License-Identifier: GPL-3.0-only
"""校验歌曲文件里的歌词标签是否写入正确（PC 侧用，依赖 mutagen）。

用法：
    python tools/verify_tags.py <音频文件或目录> [...]

对照 Android 端写入的内容：
    - MP3 / DSF / DFF / AIFF / TTA / WAV：ID3v2 的 USLT（全文）与 SYLT（字级时间戳）
    - FLAC / OGG / OPUS / APE / WV / MPC：LYRICS 字段
    - M4A / MP4：©lyr
    - WMA / ASF：WM/LYRICS
"""

import sys
from pathlib import Path

from mutagen import File
from mutagen.apev2 import APEv2
from mutagen.asf import ASFTags
from mutagen.flac import VCommentDict
from mutagen.id3 import ID3, SYLT, USLT
from mutagen.mp4 import MP4Tags

AUDIO_EXT = {
    ".mp3", ".flac", ".m4a", ".m4b", ".mp4", ".ogg", ".oga", ".opus", ".spx",
    ".wav", ".aif", ".aiff", ".wma", ".ape", ".wv", ".tta", ".mpc", ".dsf", ".dff",
}


def describe(path: Path) -> str:
    out = [f"\n=== {path.name}  ({path.stat().st_size / 1024:.0f} KB)"]
    audio = File(path)
    if audio is None:
        return out[0] + "\n  ! 无法识别格式"

    tags = audio.tags
    out.append(f"  标签类型: {type(tags).__name__ if tags is not None else 'None'}")

    if isinstance(tags, ID3):
        uslt = tags.getall("USLT")
        out.append(f"  USLT: {len(uslt)} 个")
        for i, frame in enumerate(uslt[:2]):
            preview = (frame.text or "").splitlines()[:3]
            out.append(f"    [{i}] lang={frame.lang} 首行={preview}")
        sylt = tags.getall("SYLT")
        out.append(f"  SYLT: {len(sylt)} 个")
        for i, frame in enumerate(sylt[:2]):
            items = frame.text or []
            out.append(f"    [{i}] 时间戳格式={frame.format} 类型={frame.type} 条目={len(items)}")
            out.append(f"        前 5 条: {[(t, s) for s, t in items[:5]]}")
    elif isinstance(tags, (VCommentDict, APEv2)):
        value = tags.get("LYRICS")
        text = "\n".join(value) if isinstance(value, list) else (value or "")
        out.append(f"  LYRICS: {len(text)} 字符, 首行={text.splitlines()[:1]}")
    elif isinstance(tags, MP4Tags):
        value = tags.get("©lyr")
        text = value[0] if value else ""
        out.append(f"  ©lyr: {len(text)} 字符, 首行={text.splitlines()[:1]}")
    elif isinstance(tags, ASFTags):
        value = tags.get("WM/LYRICS") or tags.get("Lyrics")
        out.append(f"  WM/LYRICS: {bool(value)}")
    else:
        out.append("  ! 未知标签类型")

    if audio.info:
        out.append(f"  时长: {int(audio.info.length * 1000)} ms")
    return "\n".join(out)


def main(argv: list[str]) -> int:
    targets: list[Path] = []
    for arg in argv or ["."]:
        path = Path(arg)
        if path.is_dir():
            targets.extend(p for p in path.rglob("*") if p.suffix.lower() in AUDIO_EXT)
        elif path.is_file():
            targets.append(path)

    if not targets:
        print("没有找到音频文件")
        return 1

    for target in targets:
        try:
            print(describe(target))
        except Exception as exc:  # noqa: BLE001
            print(f"\n=== {target.name}\n  ! 读取失败: {exc}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
