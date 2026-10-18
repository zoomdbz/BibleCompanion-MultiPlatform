import SwiftUI
import WidgetKit

struct FeastEntry: TimelineEntry {
    let date: Date
    let filter: WidgetCalendarFilter
    let feasts: [WidgetUpcomingFeast]
}

struct FeastProvider: TimelineProvider {
    func placeholder(in context: Context) -> FeastEntry {
        makeEntry()
    }

    func getSnapshot(in context: Context, completion: @escaping (FeastEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FeastEntry>) -> Void) {
        let entry = makeEntry()
        completion(Timeline(entries: [entry], policy: .after(WidgetDates.nextMidnight(after: entry.date))))
    }

    private func makeEntry() -> FeastEntry {
        let now = Date()
        let today = WidgetDates.today(now)
        let filter = WidgetState.feastFilter
        let feasts = WidgetCalendarEngine.upcoming(
            year: today.year, month: today.month, day: today.day, count: 10, filter: filter
        )
        return FeastEntry(date: now, filter: filter, feasts: feasts)
    }
}

struct FeastWidgetView: View {
    let entry: FeastEntry
    @Environment(\.widgetFamily) private var family

    private var isSmall: Bool { family == .systemSmall }

    /// Rows that fit without clipping: a hero card plus single-line rows inside
    /// the widget's content margins (about 123pt small, 126pt medium, 292pt large).
    private var maxItems: Int {
        switch family {
        case .systemLarge: return 8
        default: return 3
        }
    }

    var body: some View {
        let items = Array(entry.feasts.prefix(maxItems))
        let showMarker = entry.filter == .all
        VStack(alignment: .leading, spacing: isSmall ? 3 : 5) {
            HStack(alignment: .center, spacing: 4) {
                Text("\u{2721} " + WidgetText.text("widget_feasts_title"))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(WidgetPalette.onSurfaceVariant)
                    .lineLimit(1)
                Spacer(minLength: 4)
                Button(intent: CycleFeastCalendarIntent()) {
                    WidgetChip {
                        Text(WidgetText.filterBadge(entry.filter, showName: !isSmall))
                            .font(.caption2.weight(.semibold))
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("widget_calendar_filter"))
                .accessibilityValue(Text(WidgetText.calendarLabel(entry.filter)))
            }
            if items.isEmpty {
                Spacer(minLength: 0)
                Text(WidgetText.text("widget_no_feasts"))
                    .font(.footnote)
                    .foregroundStyle(WidgetPalette.onSurfaceVariant)
                Spacer(minLength: 0)
            } else {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, feast in
                    if index == 0 {
                        FeastHeroCard(feast: feast, showMarker: showMarker, compact: isSmall)
                    } else {
                        if index > 1 {
                            Divider().overlay(WidgetPalette.outlineVariant)
                        }
                        FeastRowView(feast: feast, showMarker: showMarker, compact: isSmall)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .widgetURL(WidgetLinks.calendar)
    }
}

private func feastLabel(_ feast: WidgetUpcomingFeast, showMarker: Bool) -> String {
    let base = WidgetText.feastLabel(feast.marker)
    return showMarker ? "\(base) \(WidgetText.marker(feast.marker.calendar))" : base
}

/// The next feast, raised on a primary container card like the app's highlight cards.
private struct FeastHeroCard: View {
    let feast: WidgetUpcomingFeast
    let showMarker: Bool
    let compact: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(feastLabel(feast, showMarker: showMarker))
                    .font((compact ? Font.caption : Font.subheadline).weight(.bold))
                    .foregroundStyle(WidgetPalette.onPrimaryContainer)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(WidgetDates.shortDate(year: feast.year, month: feast.month, day: feast.day))
                    .font(.caption2)
                    .foregroundStyle(WidgetPalette.onPrimaryContainer.opacity(0.8))
                    .lineLimit(1)
            }
            Spacer(minLength: 2)
            Text(WidgetText.countdown(feast.daysUntil))
                .font((compact ? Font.caption : Font.subheadline).weight(.bold))
                .foregroundStyle(WidgetPalette.onPrimaryContainer)
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, compact ? 6 : 8)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(WidgetPalette.primaryContainer)
        )
    }
}

/// One line per feast: name on the left, date and countdown on the right.
private struct FeastRowView: View {
    let feast: WidgetUpcomingFeast
    let showMarker: Bool
    let compact: Bool

    private var soon: Bool { feast.daysUntil <= 1 }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(feastLabel(feast, showMarker: showMarker))
                .font(compact ? .caption2 : .caption)
                .foregroundStyle(WidgetPalette.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Spacer(minLength: 4)
            Text(WidgetDates.shortDate(year: feast.year, month: feast.month, day: feast.day))
                .font(.caption2)
                .foregroundStyle(WidgetPalette.onSurfaceVariant)
                .lineLimit(1)
            Text(WidgetText.countdown(feast.daysUntil))
                .font(.caption2)
                .fontWeight(soon ? .bold : .regular)
                .foregroundStyle(soon ? WidgetPalette.primary : WidgetPalette.onSurfaceVariant)
                .lineLimit(1)
        }
    }
}

struct FeastCalendarWidget: Widget {
    let kind = WidgetState.feastKind

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: FeastProvider()) { entry in
            FeastWidgetView(entry: entry)
                .containerBackground(WidgetPalette.background, for: .widget)
        }
        .configurationDisplayName(Text("widget_feasts_title"))
        .description(Text("widget_feasts_description"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
