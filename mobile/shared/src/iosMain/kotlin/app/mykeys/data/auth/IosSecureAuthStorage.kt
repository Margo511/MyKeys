package app.mykeys.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.getPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureAuthStorage : SecureAuthStorage {
    private val mutex = Mutex()

    override suspend fun read(key: String): String? = mutex.withLock {
        autoreleasepool {
            memScoped {
                usingQuery(
                    account = key,
                    configure = { query ->
                        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
                    },
                ) { query ->
                    val result = alloc<CFTypeRefVar>()
                    when (val status = SecItemCopyMatching(query, result.ptr)) {
                        errSecSuccess -> (CFBridgingRelease(result.value) as NSData)
                            .toByteArray()
                            .decodeToString()
                        errSecItemNotFound -> null
                        else -> error("Keychain read failed with status $status")
                    }
                }
            }
        }
    }

    override suspend fun write(key: String, value: String) = mutex.withLock {
        autoreleasepool {
            memScoped {
                val bytes = value.encodeToByteArray()
                val data = NSData.create(
                    bytes = bytes.refTo(0).getPointer(this),
                    length = bytes.size.toULong(),
                )
                val dataRef = CFBridgingRetain(data)
                try {
                    val update = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
                    try {
                        CFDictionarySetValue(update, kSecValueData, dataRef)
                        CFDictionarySetValue(
                            update,
                            kSecAttrAccessible,
                            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        )
                        val updateStatus = usingQuery(key) { query -> SecItemUpdate(query, update) }
                        if (updateStatus != errSecSuccess && updateStatus != errSecItemNotFound) {
                            error("Keychain update failed with status $updateStatus")
                        }
                        if (updateStatus == errSecItemNotFound) {
                            val addStatus = usingQuery(
                                account = key,
                                configure = { query ->
                                    CFDictionarySetValue(query, kSecValueData, dataRef)
                                    CFDictionarySetValue(
                                        query,
                                        kSecAttrAccessible,
                                        kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                                    )
                                },
                            ) { query -> SecItemAdd(query, null) }
                            if (addStatus == errSecDuplicateItem) {
                                val retryStatus = usingQuery(key) { query -> SecItemUpdate(query, update) }
                                check(retryStatus == errSecSuccess) {
                                    "Keychain concurrent update failed with status $retryStatus"
                                }
                            } else {
                                check(addStatus == errSecSuccess) {
                                    "Keychain add failed with status $addStatus"
                                }
                            }
                        }
                    } finally {
                        CFRelease(update as CFTypeRef?)
                    }
                } finally {
                    CFRelease(dataRef)
                }
            }
        }
    }

    override suspend fun delete(key: String) = mutex.withLock {
        autoreleasepool {
            memScoped {
                val status = usingQuery(key) { query -> SecItemDelete(query) }
                check(status == errSecSuccess || status == errSecItemNotFound) {
                    "Keychain delete failed with status $status"
                }
            }
        }
    }

    private inline fun <R> usingQuery(
        account: String,
        configure: (CFMutableDictionaryRef?) -> Unit = {},
        block: (CFMutableDictionaryRef?) -> R,
    ): R {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
        val serviceRef = CFBridgingRetain(SERVICE_NAME)
        val accountRef = CFBridgingRetain(account)
        return try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            configure(query)
            block(query)
        } finally {
            CFRelease(query as CFTypeRef?)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply {
            usePinned { pinned ->
                memcpy(
                    pinned.addressOf(0),
                    this@toByteArray.bytes,
                    this@toByteArray.length,
                )
            }
        }
    }

    private companion object {
        const val SERVICE_NAME = "app.mykeys.auth"
    }
}
