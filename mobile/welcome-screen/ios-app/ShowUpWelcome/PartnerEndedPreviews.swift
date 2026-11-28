// PartnerEndedPreviews.swift
// ShowUp · Date 08's three payloads at the narrowest phone (320 x 686) and the widest (430 x 932), from
// `PartnerEndedSamples` (SHOWUP-199). Android's `PartnerEndedPreviews.kt`. The fit sweep measures every size between.

import SwiftUI

#Preview("Date 08 · A no-show · 320", traits: .fixedLayout(width: 320, height: 686)) {
    PartnerEndedSamples.frame(.youNoShowed)
}

#Preview("Date 08 · A no-show · 430", traits: .fixedLayout(width: 430, height: 932)) {
    PartnerEndedSamples.frame(.youNoShowed)
}

#Preview("Date 08 · B uncomfortable · 320", traits: .fixedLayout(width: 320, height: 686)) {
    PartnerEndedSamples.frame(.dateEndedDiscomfort)
}

#Preview("Date 08 · B uncomfortable · 430", traits: .fixedLayout(width: 430, height: 932)) {
    PartnerEndedSamples.frame(.dateEndedDiscomfort)
}

#Preview("Date 08 · C met · 320", traits: .fixedLayout(width: 320, height: 686)) {
    PartnerEndedSamples.frame(.dateClosedPositive)
}

#Preview("Date 08 · C met · 430", traits: .fixedLayout(width: 430, height: 932)) {
    PartnerEndedSamples.frame(.dateClosedPositive)
}
