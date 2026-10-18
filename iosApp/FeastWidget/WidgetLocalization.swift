import Foundation
import SwiftUI
import UIKit

/// Native localization helpers for the iOS feast widgets. Strings live in the
/// extension's own Localizable.strings; nothing here touches the shared
/// framework or its Compose resources.
enum WidgetText {
    static func text(_ key: String) -> String {
        NSLocalizedString(key, comment: "")
    }

    private static let keyByFeastID: [String: String] = [
        "passover": "feast_passover",
        "unleavened": "feast_unleavened",
        "firstfruits": "feast_firstfruits",
        "second_passover": "feast_second_passover",
        "pentecost": "feast_pentecost",
        "trumpets": "feast_trumpets",
        "atonement": "feast_atonement",
        "tabernacles": "feast_tabernacles",
        "assembly": "feast_assembly",
        "fast_tevet": "feast_fast_tevet",
        "fast_esther": "feast_fast_esther",
        "purim": "feast_purim",
        "fast_tammuz": "feast_fast_tammuz",
        "tisha_bav": "feast_tisha_bav",
        "fast_gedaliah": "feast_fast_gedaliah",
        "hanukkah": "feast_hanukkah",
        "new_wine": "feast_new_wine",
        "new_oil": "feast_new_oil",
        "memorial_q2": "feast_memorial_q2",
        "memorial_q4": "feast_memorial_q4",
    ]

    static func feastName(_ marker: WidgetFeastMarker) -> String {
        guard let key = keyByFeastID[marker.id] else { return marker.displayName }
        let localized = text(key)
        return localized == key ? marker.displayName : localized
    }

    static func feastLabel(_ marker: WidgetFeastMarker) -> String {
        let name = feastName(marker)
        guard marker.totalDays > 1 else { return name }
        let day = String(format: text("widget_day_of_feast"), marker.dayOfFeast)
        return "\(name) (\(day))"
    }

    static func countdown(_ days: Int) -> String {
        if days <= 0 { return text("widget_today") }
        if days == 1 { return text("widget_tomorrow") }
        return String(format: text("widget_days_short"), days)
    }

    static func calendarLabel(_ filter: WidgetCalendarFilter) -> String {
        switch filter {
        case .all: return text("widget_filter_all")
        case .hebrew: return text("widget_filter_hebrew")
        case .essene: return text("widget_filter_essene")
        case .karaite: return text("widget_filter_karaite")
        }
    }

    static func calendarLabel(_ calendar: WidgetCalendarType) -> String {
        switch calendar {
        case .hebrew: return text("widget_filter_hebrew")
        case .essene: return text("widget_filter_essene")
        case .karaite: return text("widget_filter_karaite")
        }
    }

    /// Same glyphs as the Android widgets: Star of David, sun, moon.
    static func marker(_ calendar: WidgetCalendarType) -> String {
        switch calendar {
        case .hebrew: return "\u{2721}"
        case .essene: return "\u{2609}"
        case .karaite: return "\u{263E}"
        }
    }

    static func filterSymbols(_ filter: WidgetCalendarFilter) -> String {
        switch filter {
        case .all: return "\u{2721}\u{2609}\u{263E}"
        case .hebrew: return marker(.hebrew)
        case .essene: return marker(.essene)
        case .karaite: return marker(.karaite)
        }
    }

    static func filterBadge(_ filter: WidgetCalendarFilter, showName: Bool) -> String {
        let symbols = filterSymbols(filter)
        return showName ? "\(symbols) \(calendarLabel(filter))" : symbols
    }
}

/// Gregorian date helpers. The proleptic Gregorian calendar is forced so a
/// device set to the Japanese or Buddhist calendar still yields Gregorian
/// year numbers for the shared feast arithmetic.
enum WidgetDates {
    static var gregorian: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale.current
        calendar.timeZone = TimeZone.current
        return calendar
    }

    static func today(_ now: Date = Date()) -> (year: Int, month: Int, day: Int) {
        let components = gregorian.dateComponents([.year, .month, .day], from: now)
        return (components.year ?? 2000, components.month ?? 1, components.day ?? 1)
    }

    static func nextMidnight(after date: Date = Date()) -> Date {
        let calendar = gregorian
        let start = calendar.startOfDay(for: date)
        return calendar.date(byAdding: .day, value: 1, to: start) ?? date.addingTimeInterval(86_400)
    }

    static func shiftMonth(year: Int, month: Int, by offset: Int) -> (year: Int, month: Int) {
        let index = year * 12 + (month - 1) + offset
        guard index >= 0 else { return (year, month) }
        return (index / 12, index % 12 + 1)
    }

    static func date(year: Int, month: Int, day: Int) -> Date? {
        gregorian.date(from: DateComponents(year: year, month: month, day: day, hour: 12))
    }

    private static func formatter(template: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = gregorian
        formatter.locale = Locale.current
        formatter.timeZone = TimeZone.current
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter
    }

    /// Short day label such as "Apr 13" in the device locale.
    static func shortDate(year: Int, month: Int, day: Int) -> String {
        guard let date = date(year: year, month: month, day: day) else { return "\(month)/\(day)" }
        return formatter(template: "MMMd").string(from: date)
    }

    /// Month title such as "April 2026" in the device locale.
    static func monthTitle(year: Int, month: Int) -> String {
        guard let date = date(year: year, month: month, day: 1) else { return "\(year)-\(month)" }
        return formatter(template: "yMMMM").string(from: date)
    }

    /// Sunday-first narrow weekday labels in the device locale; the grid is
    /// laid out Sunday through Saturday to match the shared firstDow values.
    static var narrowWeekdays: [String] {
        let symbols = formatter(template: "EEEEE").veryShortStandaloneWeekdaySymbols ?? []
        return symbols.count == 7 ? symbols : ["S", "M", "T", "W", "T", "F", "S"]
    }
}

/// Parchment, the app's default theme (warm cream and burnt umber), resolved
/// per appearance. The extension cannot read the in-app theme choice without an
/// App Group, so it carries the default palette the app ships with.
enum WidgetPalette {
    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    static let primary = dynamic(0x7D4A3E, 0xF6B8A6)
    static let onPrimary = dynamic(0xFFFFFF, 0x4D2116)
    static let primaryContainer = dynamic(0xFFDBCE, 0x673227)
    static let onPrimaryContainer = dynamic(0x2E1411, 0xFFDAD0)
    static let secondary = dynamic(0x76584A, 0xE8BEA9)
    static let secondaryContainer = dynamic(0xFFDBCA, 0x5D4031)
    static let onSecondaryContainer = dynamic(0x2B160C, 0xFFDAC6)
    static let tertiary = dynamic(0x785548, 0xEBBDA8)
    static let tertiaryContainer = dynamic(0xFFDBCB, 0x5F3D2F)
    static let background = dynamic(0xF8F3EA, 0x211A16)
    static let surfaceVariant = dynamic(0xF3DFD6, 0x524340)
    static let onSurface = dynamic(0x221A14, 0xEFE4D6)
    static let onSurfaceVariant = dynamic(0x524340, 0xD6C4BD)
    static let outlineVariant = dynamic(0xD6C4BD, 0x79675E)

    // Same season colors as the in-app feast calendar: spring gold, fall indigo.
    static let spring = Color(red: 0xD4 / 255.0, green: 0xA0 / 255.0, blue: 0x17 / 255.0)
    static let fall = Color(red: 0x5B / 255.0, green: 0x6B / 255.0, blue: 0xBF / 255.0)

    static func season(_ isSpring: Bool) -> Color {
        isSpring ? spring : fall
    }

    /// Marker color per calendar, matching the in-app legend.
    static func marker(_ calendar: WidgetCalendarType, isSpring: Bool) -> Color {
        switch calendar {
        case .hebrew: return season(isSpring)
        case .essene: return tertiary
        case .karaite: return secondary
        }
    }

    /// Same tinting rule as the in-app calendar day cells.
    static func feastBackground(_ feasts: [WidgetFeastMarker]) -> Color {
        let hasHebrew = feasts.contains { $0.calendar == .hebrew }
        let hasEssene = feasts.contains { $0.calendar == .essene }
        let hasKaraite = feasts.contains { $0.calendar == .karaite }
        let isSpring = feasts.contains { $0.isSpring }
        if (hasHebrew || hasKaraite) && hasEssene { return tertiaryContainer.opacity(0.6) }
        if hasHebrew { return season(isSpring).opacity(0.25) }
        if hasKaraite { return season(isSpring).opacity(0.22) }
        return season(isSpring).opacity(0.15)
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255.0,
            green: CGFloat((rgb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(rgb & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

/// A pill in the theme's secondary container, the same control style as the
/// app's chips. Wrap it in `Button(intent:)` for interactive controls.
struct WidgetChip<Content: View>: View {
    private let content: Content
    private let horizontalPadding: CGFloat
    private let verticalPadding: CGFloat

    init(
        horizontalPadding: CGFloat = 8,
        verticalPadding: CGFloat = 3,
        @ViewBuilder content: () -> Content
    ) {
        self.content = content()
        self.horizontalPadding = horizontalPadding
        self.verticalPadding = verticalPadding
    }

    var body: some View {
        content
            .foregroundStyle(WidgetPalette.onSecondaryContainer)
            .lineLimit(1)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .background(Capsule().fill(WidgetPalette.secondaryContainer))
            .contentShape(Capsule())
    }
}

enum WidgetLinks {
    /// Accepted by the app's external route allowlist and routed to the feast calendar.
    static let calendar = URL(string: "biblecompanion://open?route=feast_calendar")
}
