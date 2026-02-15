package com.example.lddc.service.network

import java.security.MessageDigest
import java.util.Random

/**
 * 网络模块，提供HTTP客户端和相关工具
 */
object NetworkModule {

    /**
     * MD5加密
     */
    fun md5(input: String): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        val digest = messageDigest.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 生成随机字符串
     */
    fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * 生成随机MAC地址
     */
    fun generateRandomMac(): String {
        val random = Random()
        return (1..6).joinToString(":") { "%02X".format(random.nextInt(256)) }
    }

    /**
     * 获取当前时间戳（秒）
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis() / 1000
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    fun getCurrentTimestampMillis(): Long {
        return System.currentTimeMillis()
    }

}

/**
 * API错误异常
 */
class ApiException(override val message: String) : Exception(message)

