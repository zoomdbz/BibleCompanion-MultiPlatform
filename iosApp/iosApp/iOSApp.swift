import SwiftUI
import shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
            .onOpenURL { url in
                if let route = Self.parseDeepLink(url) {
                    DeepLinkBridge.shared.pushRoute(route: route)
                }
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

    func application(
        _ application: UIApplication,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        if let action = mapShortcut(shortcutItem.type) {
            ShortcutBridge.shared.pushAction(action: action)
        }
        completionHandler(true)
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        MainViewControllerKt.installCrashHook()

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        UserDefaults.standard.set(now, forKey: "ios_last_launch_ms")

        if let shortcutItem = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem,
           let action = mapShortcut(shortcutItem.type) {
            ShortcutBridge.shared.pushAction(action: action)
        }

        registerShortcuts()
        return true
    }

    private func registerShortcuts() {
        UIApplication.shared.shortcutItems = [
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.search",
                localizedTitle: NSLocalizedString("shortcut_search", comment: ""),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .search),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.bookmarks",
                localizedTitle: NSLocalizedString("shortcut_bookmarks", comment: ""),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .bookmark),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.continue",
                localizedTitle: NSLocalizedString("shortcut_continue_reading", comment: ""),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(type: .play),
                userInfo: nil
            ),
            UIApplicationShortcutItem(
                type: "com.dividesbyzer0.biblecompanion.feast_calendar",
                localizedTitle: NSLocalizedString("shortcut_feast_calendar", comment: ""),
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
