package com.algoryqr.shared.session

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
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

/** Stores the session as a generic password item in the iOS Keychain, readable only on this device. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionStorage(private val service: String) : SessionStorage {

    override fun read(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
            SecItemCopyMatching(query, result.ptr)
        }
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun write(value: String) {
        clear()
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val bridgedData = CFBridgingRetain(data)
        withQuery(kSecValueData to bridgedData, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) { query ->
            SecItemAdd(query, null)
        }
        CFRelease(bridgedData)
    }

    override fun clear() {
        withQuery { query -> SecItemDelete(query) }
    }

    private fun <T> withQuery(vararg extra: Pair<CFTypeRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
        val serviceRef = CFBridgingRetain(service)
        val account = CFBridgingRetain(ACCOUNT)
        val entries = listOf(kSecClass to kSecClassGenericPassword, kSecAttrService to serviceRef, kSecAttrAccount to account) +
            extra
        val query = CFDictionaryCreateMutable(null, entries.size.toLong(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        entries.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
        try {
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(account)
        }
    }

    private companion object {
        const val ACCOUNT = "session"
    }
}
