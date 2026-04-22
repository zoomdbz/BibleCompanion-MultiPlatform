import WidgetKit
import SwiftUI

@main
struct FeastWidgetBundle: WidgetBundle {
    var body: some Widget {
        FeastCalendarWidget()
        CalendarGridWidgetConfig()
    }
}
