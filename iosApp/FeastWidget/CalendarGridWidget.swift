import SwiftUI
import WidgetKit

struct CalendarGridEntry: TimelineEntry {
    let date: Date
    let month: WidgetMonthData
    /// Day of month to highlight, or 0 when the displayed month is not the current month.
    let todayDay: Int
    let monthOffset: Int
    let filter: WidgetCalendarFilter
}

struct CalendarGridProvider: TimelineProvider {
    func placeholder(in context: Context) -> CalendarGridEntry {
        makeEntry()
    }

    func getSnapshot(in context: Context, completion: @escaping (CalendarGridEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CalendarGridEntry>) -> Void) {
        let entry = makeEntry()
        completion(Timeline(entries: [entry], policy: .after(WidgetDates.nextMidnight(after: entry.date))))
    }

    private func makeEntry() -> CalendarGridEntry {
        let now = Date()
        let today = WidgetDates.today(now)
        let offset = WidgetState.gridMonthOffset
        let filter = WidgetState.gridFilter
        let shown = WidgetDates.shiftMonth(year: today.year, month: today.month, by: offset)
        let month = WidgetCalendarEngine.month(year: shown.year, month: shown.month, filter: filter)
        let isCurrentMonth = shown.year == today.year && shown.month == today.month
        return CalendarGridEntry(
            date: now,
            month: month,
            todayDay: isCurrentMonth ? today.day : 0,
            monthOffset: offset,
            filter: filter
        )
    }
}

private struct GridMetrics {
    let dayFont: CGFloat
    let headerFont: CGFloat
    let hebrewFont: CGFloat
    let nameFont: CGFloat
    let titleFont: CGFloat
    let toggleFont: CGFloat
    let arrowFont: CGFloat
    let legendFont: CGFloat
    let dot: CGFloat
    let legendDot: CGFloat
    let titleHeight: CGFloat
    let headerHeight: CGFloat
    let legendHeight: CGFloat
    let outerPadding: CGFloat
    let showHebrewDay: Bool
    let showFeastName: Bool

    static let medium = GridMetrics(
        dayFont: 10, headerFont: 8, hebrewFont: 7, nameFont: 6, titleFont: 12, toggleFont: 10,
        arrowFont: 10, legendFont: 8, dot: 4, legendDot: 5, titleHeight: 20, headerHeight: 12,
        legendHeight: 16, outerPadding: 8, showHebrewDay: false, showFeastName: false
    )

    static let large = GridMetrics(
        dayFont: 13, headerFont: 10, hebrewFont: 8, nameFont: 7, titleFont: 15, toggleFont: 12,
        arrowFont: 12, legendFont: 10, dot: 5, legendDot: 6, titleHeight: 24, headerHeight: 14,
        legendHeight: 20, outerPadding: 10, showHebrewDay: true, showFeastName: true
    )
}

struct CalendarGridWidgetView: View {
    let entry: CalendarGridEntry
    @Environment(\.widgetFamily) private var family

    private var metrics: GridMetrics {
        family == .systemLarge ? GridMetrics.large : GridMetrics.medium
    }

    /// The Hebrew legend swatch follows the displayed month: gold in spring, indigo in fall.
    private var hebrewLegendIsSpring: Bool {
        let hebrew = entry.month.days.flatMap { $0.feasts }.filter { $0.calendar == .hebrew }
        return hebrew.isEmpty || hebrew.contains { $0.isSpring }
    }

    var body: some View {
        let m = metrics
        VStack(alignment: .leading, spacing: 2) {
            header(m)
            weekdayHeader(m)
            monthGrid(m)
            legend(m)
        }
        .padding(m.outerPadding)
        .widgetURL(WidgetLinks.calendar)
    }

    private func header(_ m: GridMetrics) -> some View {
        HStack(spacing: 4) {
            Button(intent: CycleGridCalendarIntent()) {
                WidgetChip(horizontalPadding: 8, verticalPadding: 2) {
                    Text(WidgetText.filterSymbols(entry.filter))
                        .font(.system(size: m.toggleFont, weight: .semibold))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("widget_calendar_filter"))
            .accessibilityValue(Text(WidgetText.calendarLabel(entry.filter)))
            Spacer(minLength: 0)
            Button(intent: PreviousMonthIntent()) {
                WidgetChip(horizontalPadding: 9, verticalPadding: 3) {
                    Image(systemName: "chevron.backward")
                        .font(.system(size: m.arrowFont, weight: .semibold))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("widget_previous_month"))
            Text(WidgetDates.monthTitle(year: entry.month.year, month: entry.month.month))
                .font(.system(size: m.titleFont, weight: .bold))
                .foregroundStyle(WidgetPalette.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.horizontal, 4)
            Button(intent: NextMonthIntent()) {
                WidgetChip(horizontalPadding: 9, verticalPadding: 3) {
                    Image(systemName: "chevron.forward")
                        .font(.system(size: m.arrowFont, weight: .semibold))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("widget_next_month"))
            Spacer(minLength: 0)
        }
        .frame(height: m.titleHeight)
    }

    private func weekdayHeader(_ m: GridMetrics) -> some View {
        let symbols = WidgetDates.narrowWeekdays
        return HStack(spacing: 1) {
            ForEach(0..<7, id: \.self) { index in
                Text(symbols[index])
                    .font(.system(size: m.headerFont, weight: .bold))
                    .foregroundStyle(WidgetPalette.onSurfaceVariant)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
        }
        .frame(height: m.headerHeight)
    }

    private func monthGrid(_ m: GridMetrics) -> some View {
        let data = entry.month
        let rows = (data.firstDow + data.daysInMonth + 6) / 7
        return VStack(spacing: 1) {
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: 1) {
                    ForEach(0..<7, id: \.self) { column in
                        let day = row * 7 + column - data.firstDow + 1
                        if day >= 1 && day <= data.daysInMonth {
                            dayCell(data.days[day - 1], m)
                        } else {
                            Color.clear
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                }
                .frame(maxHeight: .infinity)
            }
        }
        .frame(maxHeight: .infinity)
    }

    private func dayCell(_ day: WidgetMonthDay, _ m: GridMetrics) -> some View {
        let isToday = day.day == entry.todayDay
        let hasFeast = !day.feasts.isEmpty
        let isSpring = day.feasts.contains { $0.isSpring }
        let hasHebrew = day.feasts.contains { $0.calendar == .hebrew }
        let hasEssene = day.feasts.contains { $0.calendar == .essene }
        let hasKaraite = day.feasts.contains { $0.calendar == .karaite }
        let background: Color
        if isToday {
            background = WidgetPalette.primaryContainer
        } else if hasFeast {
            background = WidgetPalette.feastBackground(day.feasts)
        } else {
            background = Color.clear
        }
        return VStack(spacing: 0) {
            Text(verbatim: String(day.day))
                .font(.system(size: m.dayFont, weight: (isToday || hasFeast) ? .bold : .regular))
                .foregroundStyle(isToday ? WidgetPalette.primary : WidgetPalette.onSurface)
            if m.showHebrewDay {
                Text(verbatim: String(day.hebrewDay))
                    .font(.system(size: m.hebrewFont))
                    .foregroundStyle(WidgetPalette.onSurfaceVariant.opacity(0.8))
            }
            if hasFeast {
                HStack(spacing: 2) {
                    if hasHebrew {
                        Circle()
                            .fill(WidgetPalette.marker(.hebrew, isSpring: isSpring))
                            .frame(width: m.dot, height: m.dot)
                    }
                    if hasEssene {
                        Circle()
                            .fill(WidgetPalette.marker(.essene, isSpring: isSpring))
                            .frame(width: m.dot, height: m.dot)
                    }
                    if hasKaraite {
                        Circle()
                            .fill(WidgetPalette.marker(.karaite, isSpring: isSpring))
                            .frame(width: m.dot, height: m.dot)
                    }
                }
                if m.showFeastName, let first = day.feasts.first {
                    Text(String(WidgetText.feastName(first).prefix(5)))
                        .font(.system(size: m.nameFont))
                        .foregroundStyle(WidgetPalette.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(background))
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .stroke(isToday ? WidgetPalette.primary : Color.clear, lineWidth: 1.5)
        )
    }

    private func legend(_ m: GridMetrics) -> some View {
        HStack(spacing: 8) {
            if entry.filter.includes(.hebrew) {
                legendItem(.hebrew, isSpring: hebrewLegendIsSpring, m)
            }
            if entry.filter.includes(.essene) {
                legendItem(.essene, isSpring: true, m)
            }
            if entry.filter.includes(.karaite) {
                legendItem(.karaite, isSpring: true, m)
            }
            Spacer(minLength: 0)
            if entry.monthOffset != 0 {
                Button(intent: ResetMonthIntent()) {
                    WidgetChip(horizontalPadding: 8, verticalPadding: 2) {
                        Text(WidgetText.text("widget_today_button"))
                            .font(.system(size: m.legendFont, weight: .bold))
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: m.legendHeight)
    }

    private func legendItem(_ calendar: WidgetCalendarType, isSpring: Bool, _ m: GridMetrics) -> some View {
        HStack(spacing: 3) {
            Circle()
                .fill(WidgetPalette.marker(calendar, isSpring: isSpring))
                .frame(width: m.legendDot, height: m.legendDot)
            Text(WidgetText.calendarLabel(calendar))
                .font(.system(size: m.legendFont))
                .foregroundStyle(WidgetPalette.onSurfaceVariant)
                .lineLimit(1)
        }
    }
}

struct CalendarGridWidgetConfig: Widget {
    let kind = WidgetState.gridKind

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CalendarGridProvider()) { entry in
            CalendarGridWidgetView(entry: entry)
                .containerBackground(WidgetPalette.background, for: .widget)
        }
        .configurationDisplayName(Text("widget_grid_title"))
        .description(Text("widget_grid_description"))
        .supportedFamilies([.systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}
