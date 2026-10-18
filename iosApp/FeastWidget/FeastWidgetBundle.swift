import SwiftUI
import WidgetKit

@main
struct FeastWidgetBundle: WidgetBundle {
    var body: some Widget {
        FeastCalendarWidget()
        CalendarGridWidgetConfig()
    }
}
