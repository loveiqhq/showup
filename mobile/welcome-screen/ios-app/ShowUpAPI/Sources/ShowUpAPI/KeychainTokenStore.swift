import Foundation
import Security

/// Tokens in the Keychain.
///
/// WHY THE KEYCHAIN AND NOT UserDefaults
///
/// UserDefaults is an unencrypted plist inside the app container. A refresh token there is a
/// durable credential -- it mints new access tokens -- sitting in a file that any unencrypted
/// backup carries off the device. The Keychain is encrypted at rest and its contents are excluded
/// from unencrypted backups.
///
/// WHY kSecAttrAccessibleAfterFirstUnlock
///
/// The alternative, `WhenUnlocked`, would make a token unreadable while the phone is locked --
/// which breaks any background work, and would break notification handling later. `AfterFirstUnlock`
/// keeps the token available once the user has unlocked the device since boot, which is the normal
/// recommendation for a credential an app needs without user interaction. Deliberately NOT one of
/// the `ThisDeviceOnly` variants: those survive neither device migration nor restore, and a user
/// moving to a new phone should not be silently signed out.
public actor KeychainTokenStore: TokenStoring {

    private let service: String

    public init(service: String = "org.loveiq.showup.tokens") {
        self.service = service
    }

    public func accessToken() async -> String? { read(Key.access) }
    public func refreshToken() async -> String? { read(Key.refresh) }

    public func save(accessToken: String, refreshToken: String) async {
        write(Key.access, accessToken)
        write(Key.refresh, refreshToken)
    }

    public func clear() async {
        for key in [Key.access, Key.refresh] {
            SecItemDelete(baseQuery(key) as CFDictionary)
        }
    }

    // MARK: - Keychain plumbing

    private enum Key {
        static let access = "access_token"
        static let refresh = "refresh_token"
    }

    private func baseQuery(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(_ account: String) -> String? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }

        return String(data: data, encoding: .utf8)
    }

    private func write(_ account: String, _ value: String) {
        let data = Data(value.utf8)

        // Delete-then-add rather than SecItemUpdate. Update fails with errSecItemNotFound when
        // nothing is stored yet, so the add path would be needed anyway; doing it in this order is
        // one code path instead of two and cannot leave a stale duplicate behind.
        SecItemDelete(baseQuery(account) as CFDictionary)

        var attributes = baseQuery(account)
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        SecItemAdd(attributes as CFDictionary, nil)
    }
}
