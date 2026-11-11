package com.forgeprom.codestudio.core.security

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * فاحص البصمة الرقمية الإلزامي (SHA-256 Checksum Validator)
 * يضمن سلامة النماذج المنزلة أو المستوردة لمنع انهيار النظام الناتج عن تلف البيانات.
 */
object ChecksumValidator {

    fun calculateSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedHash: String): Boolean {
        if (!file.exists() || expectedHash.isBlank()) return false
        val calculated = calculateSha256(file)
        return calculated.equals(expectedHash.trim(), ignoreCase = true)
    }
}
