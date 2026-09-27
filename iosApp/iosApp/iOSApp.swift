import SwiftUI
import UserNotifications
import shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
            .onAppear { DailyVerseNotificationManager.shared.resume() }
            .onChange(of: scenePhase) { phase in
                if phase == .active { DailyVerseNotificationManager.shared.resume() }
            }
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
            if route.hasPrefix("book/"), route.contains("storyId="), !route.contains("sourceLang=") {
                return route + (route.contains("?") ? "&" : "?") + "sourceLang=en"
            }
            return route
        }

        let col = components?.queryItems?.first(where: { $0.name == "col" })?.value
        let book = components?.queryItems?.first(where: { $0.name == "book" })?.value
        let story = components?.queryItems?.first(where: { $0.name == "story" })?.value
        let sourceLang = components?.queryItems?.first(where: { $0.name == "sourceLang" })?.value ?? "en"
        let sourceEdition = components?.queryItems?.first(where: { $0.name == "sourceEdition" })?.value
        guard let col = col, let book = book else { return nil }
        let allowed = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=?#/"))
        let encodedCol = col.addingPercentEncoding(withAllowedCharacters: allowed) ?? col
        let encodedBook = book.addingPercentEncoding(withAllowedCharacters: allowed) ?? book
        var route = "book/\(encodedCol)/\(encodedBook)"
        var params: [String] = []
        if let story = story {
            let encodedStory = story.addingPercentEncoding(withAllowedCharacters: allowed) ?? story
            params.append("storyId=\(encodedStory)")
        }
        let encodedLanguage = sourceLang.addingPercentEncoding(withAllowedCharacters: allowed) ?? sourceLang
        params.append("sourceLang=\(encodedLanguage)")
        if let sourceEdition = sourceEdition, !sourceEdition.isEmpty {
            let encodedEdition = sourceEdition.addingPercentEncoding(withAllowedCharacters: allowed) ?? sourceEdition
            params.append("sourceEdition=\(encodedEdition)")
        }
        if let rawVerse = components?.queryItems?.first(where: { $0.name == "verse" })?.value,
           let verse = Int(rawVerse), verse > 0 {
            params.append("verse=\(verse)")
            if let rawEnd = components?.queryItems?.first(where: { $0.name == "verseEnd" })?.value,
               let verseEnd = Int(rawEnd), verseEnd >= verse {
                params.append("verseEnd=\(verseEnd)")
            }
        }
        route += "?" + params.joined(separator: "&")
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
        IosPlatformKt.setIosQueryEncoder(encoder: OnnxEncoder())
        DailyVerseNotificationManager.shared.install()

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

/// A rolling local queue needs no server, account, or push-notification key.
final class DailyVerseNotificationManager: NSObject, DailyVerseNotificationHost, UNUserNotificationCenterDelegate {
    static let shared = DailyVerseNotificationManager()
    private let center = UNUserNotificationCenter.current()
    private let prefix = "daily-verse."
    private let category = "daily-verse-actions"
    private let workQueue = DispatchQueue(label: "com.dividesbyzer0.biblecompanion.daily-verse", qos: .utility)
    private let generationLock = NSLock()
    private var generation = UUID().uuidString
    private var backgroundTasks: [String: UIBackgroundTaskIdentifier] = [:]
    private var pendingAction: (String, [AnyHashable: Any])?

    private func replaceGeneration() -> String {
        generationLock.lock()
        defer { generationLock.unlock() }
        generation = UUID().uuidString
        return generation
    }

    private func isCurrent(_ token: String) -> Bool {
        generationLock.lock()
        defer { generationLock.unlock() }
        return token == generation
    }

    func install() {
        center.delegate = self
        DailyVerseNotificationBridge.shared.install(host: self)
    }

    func requestPermission(result: DailyVerseNotificationPermissionResult) {
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            DispatchQueue.main.async { result.complete(granted: granted) }
        }
    }

    func openSettings() {
        DailyVerseNotificationBridge.shared.refreshAfterResume()
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func checkPermission(result: DailyVerseNotificationPermissionResult) {
        center.getNotificationSettings { settings in
            let allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
            DispatchQueue.main.async { result.complete(granted: allowed) }
        }
    }

    func resume() {
        DailyVerseNotificationBridge.shared.refreshAfterResume()
        // A notification can launch the app before SwiftUI creates its window.
        DispatchQueue.main.async { self.performPendingAction() }
    }

    func synchronize(prefs: PrefsState) {
        DispatchQueue.main.async {
            let token = self.replaceGeneration()
            self.endBackgroundTasks()
            self.center.getNotificationSettings { settings in
                self.center.getPendingNotificationRequests { requests in
                    DispatchQueue.main.async {
                        guard self.isCurrent(token) else { return }
                        self.center.removePendingNotificationRequests(withIdentifiers:
                            requests.filter { $0.identifier.hasPrefix(self.prefix) }.map { $0.identifier })
                        let permitted = settings.authorizationStatus == .authorized ||
                            settings.authorizationStatus == .provisional
                        guard prefs.dailyVerseNotifications && permitted else {
                            self.center.getDeliveredNotifications { delivered in
                                self.center.removeDeliveredNotifications(withIdentifiers:
                                    delivered.filter { $0.request.identifier.hasPrefix(self.prefix) }
                                        .map { $0.request.identifier })
                            }
                            return
                        }
                        let otherCount = requests.filter { !$0.identifier.hasPrefix(self.prefix) }.count
                        let limit = max(0, min(60, 64 - otherCount))
                        self.beginBackgroundTask(token: token)
                        self.prepareSchedule(limit: limit, prefs: prefs, token: token)
                    }
                }
            }
        }
    }

    private func beginBackgroundTask(token: String) {
        guard isCurrent(token) else { return }
        let task = UIApplication.shared.beginBackgroundTask(withName: "Daily verse scheduling") { [weak self] in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if self.isCurrent(token) { _ = self.replaceGeneration() }
                self.endBackgroundTask(token: token)
            }
        }
        backgroundTasks[token] = task
    }

    private func endBackgroundTask(token: String) {
        guard let task = backgroundTasks.removeValue(forKey: token),
              task != .invalid else { return }
        UIApplication.shared.endBackgroundTask(task)
    }

    private func endBackgroundTasks() {
        let tasks = backgroundTasks.values.filter { $0 != .invalid }
        backgroundTasks.removeAll()
        tasks.forEach { UIApplication.shared.endBackgroundTask($0) }
    }

    private func prepareSchedule(limit: Int, prefs: PrefsState, token: String) {
        workQueue.async {
            guard self.isCurrent(token) else { return }
            let context = IosPlatformKt.createPlatformContext()
            let labels = DailyVerseNotifications.shared.labels(context: context, language: prefs.appLanguage)
            let firstDate = self.firstDeliveryDate(prefs: prefs)
            DispatchQueue.main.async {
                guard self.isCurrent(token) else {
                    self.endBackgroundTask(token: token)
                    return
                }
                let actions = [
                    UNNotificationAction(identifier: "daily-copy", title: labels.copy, options: [.foreground]),
                    UNNotificationAction(identifier: "daily-share", title: labels.share, options: [.foreground])
                ]
                self.center.setNotificationCategories([UNNotificationCategory(
                    identifier: self.category, actions: actions, intentIdentifiers: [], options: [])])
                guard limit > 0, let firstDate = firstDate else {
                    self.endBackgroundTask(token: token)
                    return
                }
                self.prepareNext(date: firstDate, remaining: limit, prefs: prefs, token: token)
            }
        }
    }

    private func firstDeliveryDate(prefs: PrefsState) -> Date? {
        let calendar = Calendar(identifier: .gregorian)
        let minute = max(0, min(1439, Int(prefs.dailyVerseNotificationMinuteOfDay)))
        let now = Date()
        guard var date = calendar.date(bySettingHour: minute / 60, minute: minute % 60,
            second: 0, of: now) else { return nil }
        if date <= now {
            guard let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
                  let tomorrowTime = calendar.date(bySettingHour: minute / 60, minute: minute % 60,
                     second: 0, of: tomorrow) else { return nil }
            date = tomorrowTime
        }
        return date
    }

    private func prepareNext(date: Date, remaining: Int, prefs: PrefsState, token: String) {
        guard isCurrent(token), remaining > 0 else {
            endBackgroundTask(token: token)
            return
        }
        workQueue.async {
            guard self.isCurrent(token) else { return }
            let calendar = Calendar(identifier: .gregorian)
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
            let context = IosPlatformKt.createPlatformContext()
            var request: UNNotificationRequest?
            if let year = components.year, let month = components.month, let day = components.day,
               let raw = DailyVerseNotifications.shared.jsonForDate(context: context,
                    prefs: prefs, year: Int32(year), month: Int32(month), day: Int32(day)),
               let data = raw.data(using: .utf8),
               let payload = try? JSONDecoder().decode(DailyVersePayload.self, from: data) {
                let content = UNMutableNotificationContent()
                content.title = payload.title
                content.body = payload.text + "\n" + payload.reference
                content.sound = .default
                content.categoryIdentifier = self.category
                content.userInfo = ["route": payload.route, "text": content.body, "title": payload.title]
                request = UNNotificationRequest(
                    identifier: "\(self.prefix)\(token).\(year)-\(month)-\(day)", content: content,
                    trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
            }
            let minute = max(0, min(1439, Int(prefs.dailyVerseNotificationMinuteOfDay)))
            let tomorrow = calendar.date(byAdding: .day, value: 1, to: date).flatMap {
                calendar.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: $0)
            }
            DispatchQueue.main.async {
                guard self.isCurrent(token) else {
                    self.endBackgroundTask(token: token)
                    return
                }
                self.addPrepared(request, nextDate: tomorrow, remaining: remaining,
                    prefs: prefs, token: token)
            }
        }
    }

    private func addPrepared(_ request: UNNotificationRequest?, nextDate: Date?, remaining: Int,
        prefs: PrefsState, token: String) {
        guard isCurrent(token) else {
            endBackgroundTask(token: token)
            return
        }
        guard let request = request else {
            continueScheduling(nextDate: nextDate, remaining: remaining - 1, prefs: prefs, token: token)
            return
        }
        center.add(request) { error in
            DispatchQueue.main.async {
                guard self.isCurrent(token) else {
                    self.center.removePendingNotificationRequests(withIdentifiers: [request.identifier])
                    self.endBackgroundTask(token: token)
                    return
                }
                if let error = error { NSLog("Daily verse scheduling failed: %@", error.localizedDescription) }
                self.continueScheduling(nextDate: nextDate, remaining: remaining - 1,
                    prefs: prefs, token: token)
            }
        }
    }

    private func continueScheduling(nextDate: Date?, remaining: Int, prefs: PrefsState, token: String) {
        guard remaining > 0, let nextDate = nextDate else {
            endBackgroundTask(token: token)
            return
        }
        prepareNext(date: nextDate, remaining: remaining, prefs: prefs, token: token)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void) {
        guard response.notification.request.identifier.hasPrefix(prefix),
              response.actionIdentifier != UNNotificationDismissActionIdentifier else {
            completionHandler()
            return
        }
        DispatchQueue.main.async {
            self.pendingAction = (response.actionIdentifier, response.notification.request.content.userInfo)
            self.performPendingAction()
            completionHandler()
        }
    }

    private func performPendingAction() {
        guard UIApplication.shared.applicationState == .active,
              let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows }).first(where: { $0.isKeyWindow }),
              var presenter = window.rootViewController,
              let (action, info) = pendingAction else { return }
        pendingAction = nil
        if action == UNNotificationDefaultActionIdentifier,
           let route = info["route"] as? String {
            DeepLinkBridge.shared.pushRoute(route: route)
        }
        guard let text = info["text"] as? String else { return }
        if action == "daily-copy" {
            UIPasteboard.general.string = text
        } else if action == "daily-share" {
            while let presented = presenter.presentedViewController { presenter = presented }
            let sheet = UIActivityViewController(activityItems: [text], applicationActivities: nil)
            if let popover = sheet.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
                popover.permittedArrowDirections = []
            }
            presenter.present(sheet, animated: true)
        }
    }
}

private struct DailyVersePayload: Decodable {
    let title: String
    let text: String
    let reference: String
    let route: String
}
