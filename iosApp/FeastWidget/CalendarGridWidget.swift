import WidgetKit
import SwiftUI
import shared

private let springColor = Color(red: 0.83, green: 0.63, blue: 0.09)
private let fallColor = Color(red: 0.36, green: 0.42, blue: 0.75)

enum CalendarTypeOption: String, CaseIterable {
    case both = "B"
    case hebrew = "H"
    case essene = "E"

    var label: String {
        switch self {
        case .both: return "\u{2721}/\u{2609} " + WidgetStrings.shared.calBoth()
        case .hebrew: return "\u{2721} " + WidgetStrings.shared.calHebrew()
        case .essene: return "\u{2609} " + WidgetStrings.shared.calEssene()
        }
    }

    var next: CalendarTypeOption {
        switch self {
        case .both: return .hebrew
        case .hebrew: return .essene
        case .essene: return .both
        }
    }
}

struct MonthDataW {
    let year: Int
    let month: Int
    let todayDay: Int
    let daysInMonth: Int
    let firstDow: Int
    let feastMap: [Int: [FeastMarkerW]]
    let monthName: String
}

struct CalendarGridEntry: TimelineEntry {
    let date: Date
    let current: MonthDataW
    let next: MonthDataW
}

struct CalendarGridProvider: TimelineProvider {
    func placeholder(in context: Context) -> CalendarGridEntry {
        buildEntry()
    }

    func getSnapshot(in context: Context, completion: @escaping (CalendarGridEntry) -> Void) {
        completion(buildEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CalendarGridEntry>) -> Void) {
        let entry = buildEntry()
        let nextUpdate = Calendar.current.startOfDay(for: Calendar.current.date(byAdding: .day, value: 1, to: .now)!)
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }

    private func buildEntry() -> CalendarGridEntry {
        let cal = Calendar.current
        let y = cal.component(.year, from: .now)
        let m = cal.component(.month, from: .now)
        let d = cal.component(.day, from: .now)

        let current = MonthDataW(
            year: y, month: m, todayDay: d,
            daysInMonth: HebrewFeastCalc.daysInGregorianMonth(year: y, month: m),
            firstDow: HebrewFeastCalc.firstDayOfWeekInMonth(year: y, month: m),
            feastMap: FeastMapBuilder.buildFeastMap(gYear: y, gMonth: m),
            monthName: FeastMapBuilder.gregorianMonthName(m)
        )

        let nextY = m == 12 ? y + 1 : y
        let nextM = m == 12 ? 1 : m + 1
        let next = MonthDataW(
            year: nextY, month: nextM, todayDay: 0,
            daysInMonth: HebrewFeastCalc.daysInGregorianMonth(year: nextY, month: nextM),
            firstDow: HebrewFeastCalc.firstDayOfWeekInMonth(year: nextY, month: nextM),
            feastMap: FeastMapBuilder.buildFeastMap(gYear: nextY, gMonth: nextM),
            monthName: FeastMapBuilder.gregorianMonthName(nextM)
        )

        return CalendarGridEntry(date: .now, current: current, next: next)
    }
}

struct CalendarGridWidgetView: View {
    var entry: CalendarGridEntry
    @Environment(\.widgetFamily) var family

    @AppStorage("calendarType", store: UserDefaults(suiteName: "group.com.dividesbyzer0.biblecompanion"))
    private var calTypeRaw: String = "B"

    private var calType: CalendarTypeOption {
        CalendarTypeOption(rawValue: calTypeRaw) ?? .both
    }

    var body: some View {
        let isLarge = family == .systemLarge
        let dayFontSize: CGFloat = isLarge ? 13 : 11
        let headerFontSize: CGFloat = isLarge ? 10 : 9
        let hebrewFontSize: CGFloat = isLarge ? 8 : 7
        let dotSize: CGFloat = isLarge ? 5 : 4
        let feastNameFontSize: CGFloat = isLarge ? 7 : 6

        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("\(entry.current.monthName) \(String(entry.current.year))")
                    .font(isLarge ? .caption : .caption2)
                    .fontWeight(.bold)
                Spacer()
                Text(calType.label)
                    .font(.system(size: isLarge ? 11 : 9))
                    .foregroundColor(.purple)
            }

            monthGrid(
                data: entry.current, showHebrewDay: isLarge,
                dayFontSize: dayFontSize, headerFontSize: headerFontSize,
                hebrewFontSize: hebrewFontSize, dotSize: dotSize,
                feastNameFontSize: feastNameFontSize, showFeastName: true
            )

            if isLarge {
                Divider()
                    .padding(.vertical, 1)

                Text("\(entry.next.monthName) \(String(entry.next.year))")
                    .font(.caption)
                    .fontWeight(.bold)

                monthGrid(
                    data: entry.next, showHebrewDay: true,
                    dayFontSize: dayFontSize, headerFontSize: headerFontSize,
                    hebrewFontSize: hebrewFontSize, dotSize: dotSize,
                    feastNameFontSize: feastNameFontSize, showFeastName: true
                )
            }

            legendRow(fontSize: isLarge ? 9 : 8, dotSize: isLarge ? 6 : 5)
        }
        .padding(6)
        .widgetURL(URL(string: "biblecompanion://open?route=feast_calendar"))
    }

    @ViewBuilder
    private func legendRow(fontSize: CGFloat, dotSize: CGFloat) -> some View {
        HStack(spacing: 3) {
            if calType != .essene {
                Circle().fill(springColor).frame(width: dotSize, height: dotSize)
                Text(WidgetStrings.shared.calHebrew())
                    .font(.system(size: fontSize))
                    .foregroundColor(.secondary)
            }
            if calType == .both {
                Spacer().frame(width: 6)
            }
            if calType != .hebrew {
                Circle().fill(Color.purple).frame(width: dotSize, height: dotSize)
                Text(WidgetStrings.shared.calEssene())
                    .font(.system(size: fontSize))
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
    }

    /// Locale-aware single-letter day-of-week labels, ordered Sunday → Saturday
    /// to match the Gregorian grid layout used by `monthGrid`.
    private static var dayLetters: [String] {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        return formatter.veryShortStandaloneWeekdaySymbols ?? ["S","M","T","W","T","F","S"]
    }

    private func feastBgColor(_ feasts: [FeastMarkerW]) -> Color {
        let hasH = feasts.contains { $0.calendar == .hebrew }
        let hasE = feasts.contains { $0.calendar == .essene }
        if hasH && hasE { return Color.orange.opacity(0.15) }
        let spring = feasts.contains { $0.isSpring }
        return spring ? springColor.opacity(0.2) : fallColor.opacity(0.2)
    }

    @ViewBuilder
    private func monthGrid(
        data: MonthDataW, showHebrewDay: Bool,
        dayFontSize: CGFloat, headerFontSize: CGFloat,
        hebrewFontSize: CGFloat, dotSize: CGFloat,
        feastNameFontSize: CGFloat = 6, showFeastName: Bool = false
    ) -> some View {
        let totalSlots = data.firstDow + data.daysInMonth
        let rows = (totalSlots + 6) / 7

        // Grid lines: spacing:1 on VStack/HStack with gray background showing through gaps
        VStack(spacing: 1) {
            // Header row
            HStack(spacing: 1) {
                ForEach(0..<7, id: \.self) { i in
                    Text(Self.dayLetters[i])
                        .font(.system(size: headerFontSize, weight: .bold))
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 2)
                        .background(Color(.systemGray5))
                }
            }

            // Day rows
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: 1) {
                    ForEach(0..<7, id: \.self) { col in
                        let slotIndex = row * 7 + col
                        let day = slotIndex - data.firstDow + 1
                        if day >= 1 && day <= data.daysInMonth {
                            dayCellView(
                                data: data, day: day, showHebrewDay: showHebrewDay,
                                dayFontSize: dayFontSize, hebrewFontSize: hebrewFontSize,
                                dotSize: dotSize,
                                feastNameFontSize: feastNameFontSize,
                                showFeastName: showFeastName
                            )
                        } else {
                            // Empty cell: needs background to hold its size
                            Color(.systemGray6)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                }
                .frame(maxHeight: .infinity)
            }
        }
        .background(Color(.systemGray4))
        .cornerRadius(6)
    }

    private func shortFeastName(_ feasts: [FeastMarkerW]) -> String {
        guard let first = feasts.first else { return "" }
        // Keys match the canonical ids from FeastMapBuilder.canonicalId / CalendarMath.kt.
        // We take the first 5 graphemes of the localized full name so it reflects the
        // current UI language; the shared WidgetStrings helper resolves the translation.
        let localized = WidgetStrings.shared.feastName(id: first.id, englishFallback: first.displayName)
        return String(localized.prefix(5))
    }

    @ViewBuilder
    private func dayCellView(
        data: MonthDataW, day: Int, showHebrewDay: Bool,
        dayFontSize: CGFloat, hebrewFontSize: CGFloat, dotSize: CGFloat,
        feastNameFontSize: CGFloat = 6, showFeastName: Bool = false
    ) -> some View {
        let isToday = day == data.todayDay
        let feasts = filteredFeasts(feastMap: data.feastMap, for: day)
        let hasFeast = feasts != nil

        let bgColor: Color = hasFeast ? feastBgColor(feasts!) : Color(.systemBackground)

        VStack(spacing: 0) {
            Text("\(day)")
                .font(.system(size: dayFontSize))
                .fontWeight(isToday ? .bold : .regular)
                .foregroundColor(isToday ? .purple : .primary)

            if showHebrewDay {
                let heb = HebrewFeastCalc.jdnToHebrew(
                    HebrewFeastCalc.gregorianToJDN(year: data.year, month: data.month, day: day)
                )
                Text("\(heb.day)")
                    .font(.system(size: hebrewFontSize))
                    .foregroundColor(.secondary.opacity(0.7))
            }

            if let f = feasts {
                let hasH = f.contains { $0.calendar == .hebrew }
                let hasE = f.contains { $0.calendar == .essene }
                let spring = f.contains { $0.isSpring }
                HStack(spacing: 2) {
                    if hasH {
                        Circle()
                            .fill(spring ? springColor : fallColor)
                            .frame(width: dotSize, height: dotSize)
                    }
                    if hasE {
                        Circle()
                            .fill(Color.purple)
                            .frame(width: dotSize, height: dotSize)
                    }
                }
                if showFeastName {
                    Text(shortFeastName(f))
                        .font(.system(size: feastNameFontSize))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(1)
        .background(bgColor)
        .overlay(
            RoundedRectangle(cornerRadius: 4)
                .stroke(isToday ? Color.purple : Color.clear, lineWidth: 2)
        )
    }

    private func filteredFeasts(feastMap: [Int: [FeastMarkerW]], for day: Int) -> [FeastMarkerW]? {
        guard let feasts = feastMap[day] else { return nil }
        let filtered: [FeastMarkerW]
        switch calType {
        case .hebrew: filtered = feasts.filter { $0.calendar == .hebrew }
        case .essene: filtered = feasts.filter { $0.calendar == .essene }
        case .both: filtered = feasts
        }
        return filtered.isEmpty ? nil : filtered
    }
}

struct CalendarGridWidgetConfig: Widget {
    let kind = "CalendarGridWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CalendarGridProvider()) { entry in
            CalendarGridWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName(WidgetStrings.shared.gridDisplayName())
        .description(WidgetStrings.shared.gridDescription())
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}
