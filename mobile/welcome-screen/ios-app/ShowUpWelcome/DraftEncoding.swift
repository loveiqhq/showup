import Foundation

/// A draft that is compared as a string (the `lastPersisted` dedupe of the date sheets) must encode the same way
/// every time. `JSONEncoder` keeps keys in a hashed container, so without `.sortedKeys` two encodes of one value can
/// differ in key order, the dedupe sees a change, and a clock tick writes scene storage. Decoding is order-blind,
/// so a draft stored before this still restores.
enum DraftEncoding {
    static func string(_ draft: some Encodable) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        guard let data = try? encoder.encode(draft) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }
}
