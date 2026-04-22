import WidgetKit
import SwiftUI
import shared

struct FeastItem: Identifiable {
    let id = UUID()
    let name: String
    let feastId: String
    let dayOfFeast: Int
    let totalDays: Int
    let dateStr: String
    let daysUntil: Int
    let calendarType: CalendarType

    /// Localized label, resolved via the shared Kotlin WidgetStrings helper.
    var displayLabel: String {
        let base = WidgetStrings.shared.feastName(id: feastId, englishFallback: name)
        if totalDays > 1 {
            let dayText = WidgetStrings.shared.dayOfFeast(day: Int32(dayOfFeast))
            return "\(base) (\(dayText))"
        }
        return base
    }
}

struct FeastEntry: TimelineEntry {
    let date: Date
    let feasts: [FeastItem]
}

struct FeastProvider: TimelineProvider {
    func placeholder(in context: Context) -> FeastEntry {
        FeastEntry(date: .now, feasts: [
            FeastItem(name: "Passover", feastId: "passover", dayOfFeast: 0, totalDays: 1, dateStr: "Apr 13", daysUntil: 5, calendarType: .hebrew),
            FeastItem(name: "Unleavened Bread", feastId: "unleavened", dayOfFeast: 1, totalDays: 7, dateStr: "Apr 14", daysUntil: 6, calendarType: .hebrew),
            FeastItem(name: "Firstfruits", feastId: "firstfruits", dayOfFeast: 0, totalDays: 1, dateStr: "Apr 16", daysUntil: 8, calendarType: .essene)
        ])
    }

    func getSnapshot(in context: Context, completion: @escaping (FeastEntry) -> Void) {
        completion(buildEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FeastEntry>) -> Void) {
        let entry = buildEntry()
        let nextUpdate = Calendar.current.startOfDay(for: Calendar.current.date(byAdding: .day, value: 1, to: .now)!)
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }

    private func buildEntry() -> FeastEntry {
        let cal = Calendar.current
        let y = cal.component(.year, from: .now)
        let m = cal.component(.month, from: .now)
        let d = cal.component(.day, from: .now)

        let raw = HebrewFeastCalc.upcomingFeasts(gYear: y, gMonth: m, gDay: d, count: 5)
        let monthFormatter = DateFormatter()
        monthFormatter.locale = Locale.current
        let monthSymbols = monthFormatter.shortMonthSymbols ?? []
        let items = raw.map { feast in
            let monthName = (feast.month >= 1 && feast.month <= monthSymbols.count) ? monthSymbols[feast.month - 1] : ""
            return FeastItem(
                name: feast.name,
                feastId: feast.id,
                dayOfFeast: feast.dayOfFeast,
                totalDays: feast.totalDays,
                dateStr: "\(monthName) \(feast.day)",
                daysUntil: feast.daysUntil,
                calendarType: feast.calendarType
            )
        }
        return FeastEntry(date: .now, feasts: items)
    }
}

struct FeastWidgetView: View {
    var entry: FeastEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        if entry.feasts.isEmpty {
            Text(WidgetStrings.shared.noFeasts())
                .font(.footnote)
                .foregroundColor(.secondary)
        } else {
            let maxItems: Int = {
                switch family {
                case .systemSmall: return 3
                case .systemMedium: return 5
                case .systemLarge: return 8
                default: return 5
                }
            }()
            let items = Array(entry.feasts.prefix(maxItems))

            VStack(alignment: .leading, spacing: 4) {
                Text("\u{2721} " + WidgetStrings.shared.feastsHeader())
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .foregroundColor(.secondary)

                ForEach(Array(items.enumerated()), id: \.element.id) { idx, feast in
                    if idx > 0 {
                        Divider()
                    }
                    let marker = feast.calendarType == .hebrew ? " \u{2721}" : " \u{2609}"
                    HStack {
                        Text("\(feast.displayLabel)\(marker)")
                            .font(idx == 0 ? .subheadline : .caption)
                            .fontWeight(idx == 0 ? .bold : .regular)
                            .foregroundColor(idx == 0 ? .purple : .primary)
                            .lineLimit(1)
                        Spacer()
                        VStack(alignment: .trailing, spacing: 0) {
                            Text(feast.dateStr)
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Text(daysText(feast.daysUntil))
                                .font(.caption2)
                                .fontWeight(feast.daysUntil <= 1 ? .bold : .regular)
                                .foregroundColor(feast.daysUntil <= 1 ? .purple : .secondary)
                        }
                    }
                }
            }
            .padding(12)
            .widgetURL(URL(string: "biblecompanion://open?route=feast_calendar"))
        }
    }

    private func daysText(_ days: Int) -> String {
        switch days {
        case 0: return WidgetStrings.shared.today()
        case 1: return WidgetStrings.shared.tomorrow()
        default: return WidgetStrings.shared.daysShort(days: Int32(days))
        }
    }
}

struct FeastCalendarWidget: Widget {
    let kind = "FeastCalendarWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: FeastProvider()) { entry in
            FeastWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName(WidgetStrings.shared.listDisplayName())
        .description(WidgetStrings.shared.listDescription())
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
