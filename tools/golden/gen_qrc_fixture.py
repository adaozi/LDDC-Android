# SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
# SPDX-License-Identifier: GPL-3.0-only
"""生成 QRC 解密测试夹具（用真实 QQ 加密数据）。

数据来源：上游 tests/data 里由 QQ 下载的 .qrc 文件（qmc1 加密 + 3DES + zlib）。
流程：
  1. 用上游 qmc1_decrypt 还原第一层
  2. 去掉前 11 字节头部，得到纯 3DES 密文
  3. 用上游 qrc_decrypt(LOCAL) 得到期望明文
输出：
  app/src/test/resources/qrc_fixture.json
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE / "pyside_stub"))
sys.path.insert(0, str(ROOT / "upstream"))

from LDDC.common.models import QrcType  # noqa: E402
from LDDC.core.decryptor import qrc_decrypt  # noqa: E402
from LDDC.core.decryptor.qmc1 import qmc1_decrypt  # noqa: E402

DATA_DIR = ROOT / "upstream" / "tests" / "data"


def pick_source() -> Path:
    candidates = sorted(DATA_DIR.glob("*_qm.qrc"))
    if not candidates:
        raise SystemExit("找不到 *_qm.qrc 样本")
    return candidates[0]


if __name__ == "__main__":
    source = pick_source()
    raw = bytearray(source.read_bytes())

    # 期望明文：交给上游完整解密流程
    expected = qrc_decrypt(bytearray(raw), QrcType.LOCAL)

    # 只保留 3DES 层：qmc1 还原后去掉 11 字节头
    layer = bytearray(raw)
    qmc1_decrypt(layer)
    cipher = layer[11:]
    # 截断到 8 的整数倍（尾部可能有多余填充）
    usable = len(cipher) - len(cipher) % 8
    cipher = cipher[:usable]

    result = {
        "source": source.name,
        "encryptedHex": cipher.hex(),
        "plaintext": expected,
    }
    target = ROOT / "android" / "app" / "src" / "test" / "resources" / "qrc_fixture.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"source   : {source.name}")
    print(f"cipher   : {len(cipher)} bytes")
    print(f"plaintext: {len(expected)} chars, head={expected[:40]!r}")
    print(f"written  : {target}")
