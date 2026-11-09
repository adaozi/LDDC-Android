# SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
# SPDX-License-Identifier: GPL-3.0-only
"""PySide6 最小桩：只为在无 GUI 环境下跑通上游 LDDC 的解析/转换逻辑（生成 golden 数据）。"""


class Signal:
    def __init__(self, *args, **kwargs):
        pass

    def __get__(self, instance, owner=None):
        return self

    def __set_name__(self, owner, name):
        pass

    def emit(self, *args, **kwargs):
        pass

    def connect(self, *args, **kwargs):
        pass

    def disconnect(self, *args, **kwargs):
        pass


class QObject:
    def __init__(self, *args, **kwargs):
        pass


class _Translator:
    @staticmethod
    def translate(context, text, *args):
        return text


class QCoreApplication(_Translator):
    @staticmethod
    def instance():
        return None


class QTimer(QObject):
    timeout = Signal()

    def start(self, *args, **kwargs):
        pass

    def stop(self):
        pass


class QEventLoop(QObject):
    def exec(self):
        return 0

    def exit(self, *args):
        pass

    def quit(self):
        pass


class QMimeData:
    pass


# ---- logging 相关桩（上游 common/logger.py 使用）----
class QLoggingCategory:
    @staticmethod
    def setFilterRules(rules):
        pass

    @staticmethod
    def setFilterRulesFromFile(path):
        pass


class QMessageLogContext:
    file = ""
    line = 0
    function = ""
    category = ""


class QtMsgType:
    QtDebugMsg = 0
    QtInfoMsg = 1
    QtWarningMsg = 2
    QtCriticalMsg = 3
    QtFatalMsg = 4


def qInstallMessageHandler(handler):
    return None
