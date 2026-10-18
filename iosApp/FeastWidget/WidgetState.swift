import AppIntents
import Foundation
import WidgetKit

/// Extension-owned state. `UserDefaults.standard` inside a widget extension is
/// the extension's private container, so no App Group entitlement is needed and
/// nothing here is shared with the app. Every instance of a widget kind shares
/// the same month offset and calendar filter, mirroring one tap target per kind.
enum WidgetState {
    static let gridKind = "CalendarGridWidget"
    static let feastKind = "FeastCalendarWidget"

    private static let gridFilterKey = "grid_calendar_filter"
    private static let gridOffsetKey = "grid_month_offset"
    private static let feastFilterKey = "feast_calendar_filter"
    private static let offsetLimit = 120

    private static var defaults: UserDefaults { UserDefaults.standard }

    static var gridFilter: WidgetCalendarFilter {
        get { WidgetCalendarFilter(rawValue: defaults.string(forKey: gridFilterKey) ?? "") ?? .all }
        set { defaults.set(newValue.rawValue, forKey: gridFilterKey) }
    }

    static var gridMonthOffset: Int {
        get { clampOffset(defaults.integer(forKey: gridOffsetKey)) }
        set { defaults.set(clampOffset(newValue), forKey: gridOffsetKey) }
    }

    static var feastFilter: WidgetCalendarFilter {
        get { WidgetCalendarFilter(rawValue: defaults.string(forKey: feastFilterKey) ?? "") ?? .all }
        set { defaults.set(newValue.rawValue, forKey: feastFilterKey) }
    }

    private static func clampOffset(_ value: Int) -> Int {
        min(offsetLimit, max(-offsetLimit, value))
    }
}

extension WidgetCalendarFilter {
    /// Same cycle as the Android widgets: all, Hebrew, Essene, Karaite, all.
    var next: WidgetCalendarFilter {
        switch self {
        case .all: return .hebrew
        case .hebrew: return .essene
        case .essene: return .karaite
        case .karaite: return .all
        }
    }
}

// Interactive widget controls run these intents inside the extension process
// (iOS 17), then WidgetKit reloads the affected timeline.

struct PreviousMonthIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_previous_month", defaultValue: "Previous month")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetState.gridMonthOffset -= 1
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetState.gridKind)
        return .result()
    }
}

struct NextMonthIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_next_month", defaultValue: "Next month")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetState.gridMonthOffset += 1
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetState.gridKind)
        return .result()
    }
}

struct ResetMonthIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_today_button", defaultValue: "Today")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetState.gridMonthOffset = 0
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetState.gridKind)
        return .result()
    }
}

struct CycleGridCalendarIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_calendar_filter", defaultValue: "Calendar")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetState.gridFilter = WidgetState.gridFilter.next
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetState.gridKind)
        return .result()
    }
}

struct CycleFeastCalendarIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_calendar_filter", defaultValue: "Calendar")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetState.feastFilter = WidgetState.feastFilter.next
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetState.feastKind)
        return .result()
    }
}
