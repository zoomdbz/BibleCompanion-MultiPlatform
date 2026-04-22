import Foundation

struct UpcomingFeastInfo {
    let name: String
    let id: String
    let year: Int
    let month: Int
    let day: Int
    let daysUntil: Int
    let calendarType: CalendarType
    let dayOfFeast: Int
    let totalDays: Int

    init(name: String, id: String? = nil, year: Int, month: Int, day: Int,
         daysUntil: Int, calendarType: CalendarType, dayOfFeast: Int, totalDays: Int) {
        self.name = name
        self.id = id ?? FeastMapBuilder.canonicalId(from: name)
        self.year = year; self.month = month; self.day = day
        self.daysUntil = daysUntil; self.calendarType = calendarType
        self.dayOfFeast = dayOfFeast; self.totalDays = totalDays
    }

    /// English fallback used only when shared framework isn't linked.
    var displayLabel: String {
        totalDays > 1 ? "\(name) (Day \(dayOfFeast))" : name
    }
}

enum CalendarType: String { case hebrew, essene }

struct FeastMarkerW {
    let id: String
    let displayName: String
    let isSpring: Bool
    let calendar: CalendarType
    let dayOfFeast: Int
    let totalDays: Int

    init(id: String, displayName: String, isSpring: Bool, calendar: CalendarType,
         dayOfFeast: Int = 0, totalDays: Int = 1) {
        self.id = id; self.displayName = displayName; self.isSpring = isSpring
        self.calendar = calendar; self.dayOfFeast = dayOfFeast; self.totalDays = totalDays
    }
}

enum EsseneCalc {
    static let monthLengths = [30, 30, 31, 30, 30, 31, 30, 30, 31, 30, 30, 31]

    static func yearStartJDN(_ gregorianYear: Int) -> Int64 {
        let equinoxJDN = HebrewFeastCalc.gregorianToJDN(year: gregorianYear, month: 3, day: 20)
        let dow = HebrewFeastCalc.dayOfWeekFromJDN(equinoxJDN)
        let offset = (3 - dow + 7) % 7
        return equinoxJDN + Int64(offset)
    }

    static func esseneToJDN(gregorianYear: Int, month: Int, day: Int) -> Int64 {
        let start = yearStartJDN(gregorianYear)
        var offset = 0
        for m in 0..<(month - 1) { offset += monthLengths[m] }
        return start + Int64(offset + day - 1)
    }

    static func esseneFeastsForYear(_ gregorianYear: Int) -> [(jdn: Int64, marker: FeastMarkerW)] {
        var result: [(Int64, FeastMarkerW)] = []
        func add(_ month: Int, _ day: Int, _ id: String, _ display: String, _ spring: Bool,
                 _ dayOfFeast: Int = 0, _ totalDays: Int = 1) {
            let jdn = esseneToJDN(gregorianYear: gregorianYear, month: month, day: day)
            result.append((jdn, FeastMarkerW(id: id, displayName: display, isSpring: spring,
                                              calendar: .essene, dayOfFeast: dayOfFeast, totalDays: totalDays)))
        }
        add(1, 14, "passover", "Passover", true)
        for (i, d) in (15...21).enumerated() { add(1, d, "unleavened", "Unleavened Bread", true, i + 1, 7) }
        add(1, 26, "firstfruits", "Firstfruits", true)
        add(3, 15, "weeks", "Feast of Weeks", true)
        add(7, 1, "trumpets", "Trumpets", false)
        add(7, 10, "atonement", "Day of Atonement", false)
        for (i, d) in (15...21).enumerated() { add(7, d, "tabernacles", "Tabernacles", false, i + 1, 7) }
        add(7, 22, "assembly", "Shemini Atzeret", false)
        return result
    }
}

enum FeastMapBuilder {
    /// Maps an English feast display name to the canonical id used by the shared
    /// Kotlin CalendarMath (and by WidgetStrings.feastName). Canonical ids keep
    /// the widget's lookups in sync with the in-app Compose UI.
    static func canonicalId(from englishName: String) -> String {
        switch englishName {
        case "Passover": return "passover"
        case "Unleavened Bread": return "unleavened"
        case "Firstfruits": return "firstfruits"
        case "Second Passover": return "second_passover"
        case "Feast of Weeks", "Pentecost": return "pentecost"
        case "Trumpets": return "trumpets"
        case "Day of Atonement": return "atonement"
        case "Tabernacles": return "tabernacles"
        case "Shemini Atzeret": return "assembly"
        case "10th of Tevet (fast)": return "fast_tevet"
        case "Fast of Esther": return "fast_esther"
        case "Purim", "Shushan Purim": return "purim"
        case "17th of Tammuz (fast)": return "fast_tammuz"
        case "Tisha B\u{2019}Av (fast)", "Tisha B'Av (fast)": return "tisha_bav"
        case "Fast of Gedaliah": return "fast_gedaliah"
        case "Hanukkah": return "hanukkah"
        default: return englishName.lowercased().replacingOccurrences(of: " ", with: "_")
        }
    }

    static func hebrewFeastsWithMarkers(year: Int) -> [(jdn: Int64, marker: FeastMarkerW)] {
        let ordained = HebrewFeastCalc.hebrewFeasts(year: year).map { (jdn, name) in
            let spring = ["Passover", "Unleavened Bread", "Firstfruits", "Feast of Weeks"].contains(name)
            return (jdn, FeastMarkerW(id: canonicalId(from: name),
                                       displayName: name, isSpring: spring, calendar: .hebrew))
        }
        let tradition = HebrewFeastCalc.hebrewTraditionFeasts(year: year).map { (jdn, name) in
            return (jdn, FeastMarkerW(id: canonicalId(from: name),
                                       displayName: name, isSpring: false, calendar: .hebrew))
        }
        return ordained + tradition
    }

    static func buildFeastMap(gYear: Int, gMonth: Int) -> [Int: [FeastMarkerW]] {
        let firstJDN = HebrewFeastCalc.gregorianToJDN(year: gYear, month: gMonth, day: 1)
        let dim = HebrewFeastCalc.daysInGregorianMonth(year: gYear, month: gMonth)
        let lastJDN = firstJDN + Int64(dim) - 1

        let hDate = HebrewFeastCalc.jdnToHebrew(firstJDN)
        var allFeasts = hebrewFeastsWithMarkers(year: hDate.year)
        let hMonthName = HebrewFeastCalc.monthNames(hDate.year)[hDate.monthIndex]
        if hMonthName == "Elul" {
            allFeasts += hebrewFeastsWithMarkers(year: hDate.year + 1)
        }
        allFeasts += EsseneCalc.esseneFeastsForYear(gYear)
        allFeasts += EsseneCalc.esseneFeastsForYear(gYear - 1)

        var map = [Int: [FeastMarkerW]]()
        for (jdn, marker) in allFeasts {
            if jdn >= firstJDN && jdn <= lastJDN {
                let dayOfMonth = Int(jdn - firstJDN) + 1
                map[dayOfMonth, default: []].append(marker)
            }
        }
        return map
    }

    /// Localized Gregorian month name for the user's current locale.
    /// Indexed 1-12 (returns "" for any out-of-range value to keep the call sites simple).
    static func gregorianMonthName(_ month: Int) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        let symbols = formatter.monthSymbols ?? []
        guard month >= 1 && month <= symbols.count else { return "" }
        return symbols[month - 1]
    }
}

enum HebrewFeastCalc {
    private static let EPOCH: Int64 = 347996

    static func isLeapYear(_ year: Int) -> Bool {
        return (7 * year + 1) % 19 < 7
    }

    static func monthsInYear(_ year: Int) -> Int {
        return isLeapYear(year) ? 13 : 12
    }

    private static func elapsedDays(_ year: Int) -> Int64 {
        let y = Int64(year)
        let cycles = (y - 1) / 19
        let rem = (y - 1) % 19
        let monthsElapsed = 235 * cycles + 12 * rem + (7 * rem + 1) / 19
        let partsElapsed = 204 + 793 * (monthsElapsed % 1080)
        let hoursElapsed = 5 + 12 * monthsElapsed + 793 * (monthsElapsed / 1080) + partsElapsed / 1080
        let conjDay = 1 + 29 * monthsElapsed + hoursElapsed / 24
        let conjParts = 1080 * (hoursElapsed % 24) + partsElapsed % 1080

        var d = conjDay
        if conjParts >= 19440 { d += 1 }
        if d % 7 == 2 && conjParts >= 9924 && !isLeapYear(year) { d += 1 }
        if d % 7 == 1 && conjParts >= 16789 && isLeapYear(year - 1) { d += 1 }
        let dow = d % 7
        if dow == 0 || dow == 3 || dow == 5 { d += 1 }
        return d
    }

    static func newYearJDN(_ year: Int) -> Int64 {
        return EPOCH + elapsedDays(year)
    }

    static func yearLength(_ year: Int) -> Int {
        return Int(newYearJDN(year + 1) - newYearJDN(year))
    }

    private static let commonMonths = [
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
    ]
    private static let leapMonths = [
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
    ]

    static func monthNames(_ year: Int) -> [String] {
        return isLeapYear(year) ? leapMonths : commonMonths
    }

    static func daysInMonth(year: Int, monthIndex: Int) -> Int {
        let name = monthNames(year)[monthIndex]
        let yl = yearLength(year)
        switch name {
        case "Tishrei": return 30
        case "Cheshvan": return yl % 10 == 5 ? 30 : 29
        case "Kislev": return yl % 10 == 3 ? 29 : 30
        case "Tevet": return 29
        case "Shevat": return 30
        case "Adar": return 29
        case "Adar I": return 30
        case "Adar II": return 29
        case "Nisan": return 30
        case "Iyar": return 29
        case "Sivan": return 30
        case "Tammuz": return 29
        case "Av": return 30
        case "Elul": return 29
        default: return 30
        }
    }

    static func gregorianToJDN(year: Int, month: Int, day: Int) -> Int64 {
        let a = (14 - month) / 12
        let y = Int64(year) + 4800 - Int64(a)
        let m = month + 12 * a - 3
        return Int64(day) + (153 * Int64(m) + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    static func jdnToGregorian(_ jdn: Int64) -> (year: Int, month: Int, day: Int) {
        let a = jdn + 32044
        let b = (4 * a + 3) / 146097
        let c = a - 146097 * b / 4
        let d = (4 * c + 3) / 1461
        let e = c - 1461 * d / 4
        let m = (5 * e + 2) / 153
        let day = Int(e - (153 * m + 2) / 5 + 1)
        let month = Int(m + 3 - 12 * (m / 10))
        let year = Int(100 * b + d - 4800 + m / 10)
        return (year, month, day)
    }

    static func jdnToHebrew(_ jdn: Int64) -> (year: Int, monthIndex: Int, day: Int) {
        var year = Int(Double(jdn - EPOCH) * 19.0 / 6940.0) + 1
        while newYearJDN(year + 1) <= jdn { year += 1 }
        while newYearJDN(year) > jdn { year -= 1 }

        var dayInYear = Int(jdn - newYearJDN(year))
        let months = monthNames(year)
        var mi = 0
        while mi < months.count - 1 {
            let dm = daysInMonth(year: year, monthIndex: mi)
            if dayInYear < dm { break }
            dayInYear -= dm
            mi += 1
        }
        return (year, mi, dayInYear + 1)
    }

    static func hebrewToJDN(year: Int, monthIndex: Int, day: Int) -> Int64 {
        var jdn = newYearJDN(year)
        for m in 0..<monthIndex {
            jdn += Int64(daysInMonth(year: year, monthIndex: m))
        }
        return jdn + Int64(day) - 1
    }

    private static func monthIndexByName(year: Int, name: String) -> Int? {
        return monthNames(year).firstIndex(of: name)
    }

    static func hebrewFeasts(year: Int) -> [(jdn: Int64, name: String)] {
        var result: [(Int64, String)] = []
        func add(_ monthName: String, _ day: Int, _ display: String) {
            guard let mi = monthIndexByName(year: year, name: monthName) else { return }
            let jdn = hebrewToJDN(year: year, monthIndex: mi, day: day)
            result.append((jdn, display))
        }

        add("Nisan", 14, "Passover")
        for d in 15...21 { add("Nisan", d, "Unleavened Bread") }
        add("Nisan", 16, "Firstfruits")
        add("Sivan", 6, "Feast of Weeks")
        add("Tishrei", 1, "Trumpets")
        add("Tishrei", 2, "Trumpets")
        add("Tishrei", 10, "Day of Atonement")
        for d in 15...21 { add("Tishrei", d, "Tabernacles") }
        add("Tishrei", 22, "Shemini Atzeret")

        return result
    }

    static func hebrewTraditionFeasts(year: Int) -> [(jdn: Int64, name: String)] {
        var result: [(Int64, String)] = []
        func add(_ monthName: String, _ day: Int, _ display: String) {
            guard let mi = monthIndexByName(year: year, name: monthName) else { return }
            let jdn = hebrewToJDN(year: year, monthIndex: mi, day: day)
            result.append((jdn, display))
        }

        add("Tevet", 10, "10th of Tevet")
        let adarName = isLeapYear(year) ? "Adar II" : "Adar"
        func addAdar(_ day: Int, _ display: String) {
            guard let mi = monthIndexByName(year: year, name: adarName) else { return }
            let jdn = hebrewToJDN(year: year, monthIndex: mi, day: day)
            result.append((jdn, display))
        }
        addAdar(13, "Fast of Esther")
        addAdar(14, "Purim")
        addAdar(15, "Shushan Purim")
        add("Tammuz", 17, "17th of Tammuz")
        add("Av", 9, "Tisha B'Av")
        add("Tishrei", 3, "Fast of Gedaliah")
        for d in 25...30 { add("Kislev", d, "Hanukkah") }
        add("Tevet", 1, "Hanukkah")
        add("Tevet", 2, "Hanukkah")

        return result
    }

    static func dayOfWeekFromJDN(_ jdn: Int64) -> Int {
        return Int((jdn + 1) % 7)
    }

    static func daysInGregorianMonth(year: Int, month: Int) -> Int {
        switch month {
        case 1: return 31; case 2: return isGregorianLeap(year) ? 29 : 28; case 3: return 31
        case 4: return 30; case 5: return 31; case 6: return 30; case 7: return 31
        case 8: return 31; case 9: return 30; case 10: return 31; case 11: return 30; case 12: return 31
        default: return 30
        }
    }

    private static func isGregorianLeap(_ year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    static func firstDayOfWeekInMonth(year: Int, month: Int) -> Int {
        dayOfWeekFromJDN(gregorianToJDN(year: year, month: month, day: 1))
    }

    static func nextFeast(gYear: Int, gMonth: Int, gDay: Int) -> UpcomingFeastInfo? {
        return upcomingFeasts(gYear: gYear, gMonth: gMonth, gDay: gDay, count: 1).first
    }

    static func upcomingFeasts(gYear: Int, gMonth: Int, gDay: Int, count: Int = 5) -> [UpcomingFeastInfo] {
        let todayJDN = gregorianToJDN(year: gYear, month: gMonth, day: gDay)
        let hDate = jdnToHebrew(todayJDN)

        var raw: [(jdn: Int64, name: String, cal: CalendarType)] = []
        for y in [hDate.year, hDate.year + 1] {
            for (jdn, name) in hebrewFeasts(year: y) {
                raw.append((jdn, name, .hebrew))
            }
            for (jdn, name) in hebrewTraditionFeasts(year: y) {
                raw.append((jdn, name, .hebrew))
            }
        }
        for y in [gYear, gYear + 1] {
            for (jdn, marker) in EsseneCalc.esseneFeastsForYear(y) {
                raw.append((jdn, marker.displayName, .essene))
            }
        }

        // Compute day-of-feast for multi-day feasts by detecting consecutive JDN runs
        let sorted = raw.sorted { $0.jdn < $1.jdn }
        var dayInfo: [String: (dayNum: Int, total: Int)] = [:]
        var runs: [String: [(jdn: Int64, key: String)]] = [:]
        for c in sorted {
            let groupKey = "\(c.name)_\(c.cal.rawValue)"
            let entryKey = "\(c.name)_\(c.jdn)_\(c.cal.rawValue)"
            runs[groupKey, default: []].append((c.jdn, entryKey))
        }
        for (_, entries) in runs {
            // Split into contiguous runs (consecutive JDNs)
            var currentRun: [(jdn: Int64, key: String)] = []
            var allRuns: [[(jdn: Int64, key: String)]] = []
            for e in entries.sorted(by: { $0.jdn < $1.jdn }) {
                if let last = currentRun.last, e.jdn - last.jdn <= 1 {
                    currentRun.append(e)
                } else {
                    if !currentRun.isEmpty { allRuns.append(currentRun) }
                    currentRun = [e]
                }
            }
            if !currentRun.isEmpty { allRuns.append(currentRun) }
            for run in allRuns {
                let total = run.count
                for (i, e) in run.enumerated() {
                    dayInfo[e.key] = (total > 1 ? i + 1 : 0, total)
                }
            }
        }

        let future = sorted.filter { $0.jdn >= todayJDN }
        var seen = Set<String>()
        var result: [UpcomingFeastInfo] = []
        for c in future {
            if result.count >= count { break }
            let key = "\(c.name)_\(c.jdn)_\(c.cal.rawValue)"
            if seen.contains(key) { continue }
            seen.insert(key)
            let g = jdnToGregorian(c.jdn)
            let daysUntil = Int(c.jdn - todayJDN)
            let info = dayInfo[key] ?? (0, 1)
            result.append(UpcomingFeastInfo(name: c.name, year: g.year, month: g.month, day: g.day,
                                            daysUntil: daysUntil, calendarType: c.cal,
                                            dayOfFeast: info.dayNum, totalDays: info.total))
        }
        return result
    }
}
