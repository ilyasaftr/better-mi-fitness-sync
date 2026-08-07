import SwiftUI
import UIKit

/// Design tokens: **system semantic colors** for structure + **brand orange** as primary accent.
/// Matches Apple HIG / industry practice for light & dark appearance.
enum Brand {

    // MARK: - Brand accent (only strong custom brand UI color)

    /// Primary brand orange — slightly brighter in dark for contrast on dark surfaces.
    static let primary = dynamic(
        light: UIColor(red: 1.0, green: 0.420, blue: 0.208, alpha: 1),   // #FF6B35
        dark: UIColor(red: 1.0, green: 0.561, blue: 0.369, alpha: 1)    // #FF8F5E
    )

    static let primaryBright = dynamic(
        light: UIColor(red: 1.0, green: 0.561, blue: 0.369, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.65, blue: 0.48, alpha: 1)
    )

    /// Text/icons on primary fills.
    static let onPrimary = Color.white

    /// Fixed navy mark for logo/avatar **fills** (pair with white glyphs). Not for body text.
    static let mark = Color(red: 0.059, green: 0.153, blue: 0.267)

    // MARK: - Semantic text (system — auto light/dark)

    static let label = Color(uiColor: .label)
    static let secondaryLabel = Color(uiColor: .secondaryLabel)
    static let tertiaryLabel = Color(uiColor: .tertiaryLabel)
    static let placeholder = Color(uiColor: .placeholderText)

    // MARK: - Semantic surfaces (system grouped hierarchy)

    /// Page background.
    static let pageBackground = Color(uiColor: .systemGroupedBackground)

    /// Cards / grouped blocks on the page.
    static let cardBackground = Color(uiColor: .secondarySystemGroupedBackground)

    /// Text fields / elevated controls sitting on the page (grouped secondary surface).
    static let fieldBackground = Color(uiColor: .secondarySystemGroupedBackground)

    /// Hairlines, secondary button borders.
    static let separator = Color(uiColor: .separator)

    /// Disabled / secondary fills.
    static let fill = Color(uiColor: .secondarySystemFill)

    // MARK: - Status (system adaptive)

    static let success = Color(uiColor: .systemGreen)
    /// Yellow separates “warning” from brand primary orange.
    static let caution = Color(uiColor: .systemYellow)
    static let danger = Color(uiColor: .systemRed)

    // MARK: - Layout

    static let cardRadius: CGFloat = 20
    static let buttonRadius: CGFloat = 16

    // MARK: - Compatibility aliases (existing call sites)

    static var orange: Color { primary }
    static var orangeBright: Color { primaryBright }
    static var navy: Color { mark }
    static var navyDeep: Color { mark }
    static var ink: Color { label }
    static var inkMuted: Color { secondaryLabel }
    static var canvas: Color { pageBackground }
    static var border: Color { separator }
    static var fieldPlaceholder: Color { placeholder }

    // MARK: - Helpers

    private static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? dark : light
        })
    }
}

// MARK: - Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var enabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(enabled ? Brand.onPrimary : Brand.tertiaryLabel)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(enabled ? Brand.primary : Brand.fill)
            .clipShape(RoundedRectangle(cornerRadius: Brand.buttonRadius, style: .continuous))
            .opacity(configuration.isPressed && enabled ? 0.9 : 1)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    var enabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(enabled ? Brand.label : Brand.tertiaryLabel)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Brand.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: Brand.buttonRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Brand.buttonRadius, style: .continuous)
                    .strokeBorder(Brand.separator, lineWidth: 1)
            )
            .opacity(configuration.isPressed && enabled ? 0.88 : 1)
    }
}

struct BrandCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: Brand.cardRadius, style: .continuous))
    }
}
