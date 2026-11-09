cmake_minimum_required(VERSION 3.22.1)

project(lddc_tagbridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# TagLib 配置：按需开启格式，关掉用不到的（Matroska / Tracker / Shorten）以减小体积
# ---------------------------------------------------------------------------
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(WITH_APE ON CACHE BOOL "" FORCE)        # APE / MPC / WavPack
set(WITH_ASF ON CACHE BOOL "" FORCE)        # WMA
set(WITH_DSF ON CACHE BOOL "" FORCE)        # DSF / DFF
set(WITH_MP4 ON CACHE BOOL "" FORCE)        # M4A
set(WITH_RIFF ON CACHE BOOL "" FORCE)       # WAV / AIFF
set(WITH_TRUEAUDIO ON CACHE BOOL "" FORCE)  # TTA
set(WITH_VORBIS ON CACHE BOOL "" FORCE)     # FLAC / OGG / OPUS
set(WITH_MATROSKA OFF CACHE BOOL "" FORCE)
set(WITH_MOD OFF CACHE BOOL "" FORCE)
set(WITH_SHORTEN OFF CACHE BOOL "" FORCE)

add_subdirectory(taglib EXCLUDE_FROM_ALL)

# TagLib 的头文件是"扁平"布局（安装时才合并到 include/taglib），
# 源码树内使用需要把所有含头文件的目录都加进搜索路径。
file(GLOB_RECURSE TAGLIB_ALL_HEADERS "${CMAKE_CURRENT_SOURCE_DIR}/taglib/taglib/*.h")
set(TAGLIB_INCLUDE_DIRS "")
foreach(header ${TAGLIB_ALL_HEADERS})
    get_filename_component(header_dir "${header}" DIRECTORY)
    list(APPEND TAGLIB_INCLUDE_DIRS "${header_dir}")
endforeach()
list(REMOVE_DUPLICATES TAGLIB_INCLUDE_DIRS)

add_library(lddc_tagbridge SHARED taglib_bridge.cpp)

target_include_directories(lddc_tagbridge PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/taglib      # 支持 #include <taglib/xxx.h>
        ${TAGLIB_INCLUDE_DIRS}                  # 支持 TagLib 内部的 "tfile.h" 扁平包含
)

target_link_libraries(lddc_tagbridge PRIVATE tag)

target_compile_options(lddc_tagbridge PRIVATE -fexceptions -frtti -O2)
