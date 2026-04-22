import SwiftUI
import shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView(
                shortcutAction: appDelegate.shortcutAction,
                deepLinkRoute: appDelegate.deepLinkRoute
            )
            .onOpenURL { url in
                appDelegate.deepLinkRoute = Self.parseDeepLink(url)
            }
        }
    }

    static func parseDeepLink(_ url: URL) -> String? {
        guard url.scheme == "biblecompanion", url.host == "open" else { return nil }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)

        if let route = components?.queryItems?.first(where: { $0.name == "route" })?.value {
            return route
        }

        let col = components?.queryItems?.first(where: { $0.name == "col" })?.value
        let book = components?.queryItems?.first(where: { $0.name == "book" })?.value
        let story = components?.queryItems?.first(where: { $0.name == "story" })?.value
        guard let col = col, let book = book else { return nil }
        var route = "book/\(col)/\(book)"
        if let story = story { route += "?storyId=\(story)" }
        return route
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    var shortcutAction: String?
    var deepLinkRoute: String?

    func application(
        _ application: UIApplication,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        shortcutAction = mapShortcut(shortcutItem.type)
        completionHandler(true)
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if let shortcutItem = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem {
            shortcutAction = mapShortcut(shortcutItem.type)
        }
        registerLocalizedShortcuts()
        return true
    }

    private func registerLocalizedShortcuts() {
        UIApplication.shared.shortcutItems = [
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.search",
                localizedTitle: WidgetStrings.shared.shortcutSearch(),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .search),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.bookmarks",
                localizedTitle: WidgetStrings.shared.shortcutBookmarks(),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .bookmark),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.continue",
                localizedTitle: WidgetStrings.shared.shortcutContinue(),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .play),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.feast_calendar",
                localizedTitle: WidgetStrings.shared.shortcutFeastCalendar(),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .date),
                userInfo: nil
            )
        ]
    }

    private func mapShortcut(_ type: String) -> String? {
        switch type {
        case "com.dividesbyzer0.biblecompanion.search": return "search"
        case "com.dividesbyzer0.biblecompanion.bookmarks": return "bookmarks"
        case "com.dividesbyzer0.biblecompanion.continue": return "continue"
        case "com.dividesbyzer0.biblecompanion.feast_calendar": return "feast_calendar"
        default: return nil
        }
    }
}
