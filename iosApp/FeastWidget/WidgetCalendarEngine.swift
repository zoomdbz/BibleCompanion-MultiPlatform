import Foundation

// Keep the widget extension independent of the shared framework and its resources.
// Calendar rules mirror shared CalendarMath.kt; Julian day numbers identify the
// Gregorian daytime date rather than its preceding sunset.
enum WidgetCalendarType: String {
    case hebrew = "H"
    case essene = "E"
    case karaite = "K"
}

enum WidgetCalendarFilter: String, CaseIterable {
    case all = "B"
    case hebrew = "H"
    case essene = "E"
    case karaite = "K"

    func includes(_ calendar: WidgetCalendarType) -> Bool {
        self == .all || rawValue == calendar.rawValue
    }
}

struct WidgetFeastMarker {
    let id: String
    let displayName: String
    let isSpring: Bool
    let calendar: WidgetCalendarType
    let dayOfFeast: Int
    let totalDays: Int
}

struct WidgetMonthDay: Identifiable {
    let day: Int
    let hebrewDay: Int
    let feasts: [WidgetFeastMarker]

    var id: Int { day }
}

struct WidgetMonthData {
    let year: Int
    let month: Int
    let firstDow: Int
    let daysInMonth: Int
    let days: [WidgetMonthDay]
}

struct WidgetUpcomingFeast: Identifiable {
    let marker: WidgetFeastMarker
    let year: Int
    let month: Int
    let day: Int
    let daysUntil: Int

    var id: String {
        "\(year)-\(month)-\(day):\(marker.calendar.rawValue):\(marker.id):\(marker.dayOfFeast)"
    }
}

enum WidgetCalendarEngine {
    static func month(
        year: Int,
        month: Int,
        filter: WidgetCalendarFilter = .all
    ) -> WidgetMonthData {
        let firstJDN = WidgetHebrewCalendar.gregorianToJDN(year, month, 1)
        let dayCount = daysInGregorianMonth(year, month)
        let lastJDN = firstJDN + dayCount - 1
        let hebrewDate = WidgetHebrewCalendar.jdnToHebrew(firstJDN)

        // Preserve CalendarUtils.buildFeastMap's source order and defaults.
        // Tradition appears in the month grid but not in upcoming feasts.
        var feasts = WidgetHebrewCalendar.feastsForYear(hebrewDate.year)
        let startsInElul = WidgetHebrewCalendar.monthNames(hebrewDate.year)[hebrewDate.monthIndex] == "Elul"
        if startsInElul {
            feasts += WidgetHebrewCalendar.feastsForYear(hebrewDate.year + 1)
        }
        feasts += WidgetHebrewCalendar.traditionForYear(hebrewDate.year)
        if startsInElul {
            feasts += WidgetHebrewCalendar.traditionForYear(hebrewDate.year + 1)
        }
        feasts += WidgetEsseneCalendar.feastsForYear(year)
        feasts += WidgetEsseneCalendar.feastsForYear(year - 1)
        feasts += WidgetKaraiteCalendar.feastsForYear(year)
        feasts += WidgetKaraiteCalendar.feastsForYear(year - 1)

        var feastMap: [Int: [WidgetFeastMarker]] = [:]
        for feast in feasts where feast.jdn >= firstJDN && feast.jdn <= lastJDN {
            if filter.includes(feast.marker.calendar) {
                feastMap[feast.jdn - firstJDN + 1, default: []].append(feast.marker)
            }
        }
        let days = (1...dayCount).map { day in
            WidgetMonthDay(
                day: day,
                hebrewDay: WidgetHebrewCalendar.jdnToHebrew(firstJDN + day - 1).day,
                feasts: feastMap[day] ?? []
            )
        }
        return WidgetMonthData(
            year: year,
            month: month,
            firstDow: WidgetHebrewCalendar.dayOfWeek(firstJDN),
            daysInMonth: dayCount,
            days: days
        )
    }

    static func upcoming(
        year: Int,
        month: Int,
        day: Int,
        count: Int = 5,
        filter: WidgetCalendarFilter = .all
    ) -> [WidgetUpcomingFeast] {
        guard count > 0 else { return [] }
        let todayJDN = WidgetHebrewCalendar.gregorianToJDN(year, month, day)
        let hebrewDate = WidgetHebrewCalendar.jdnToHebrew(todayJDN)
        var candidates = WidgetHebrewCalendar.feastsForYear(hebrewDate.year)
        candidates += WidgetHebrewCalendar.feastsForYear(hebrewDate.year + 1)
        candidates += WidgetEsseneCalendar.feastsForYear(year)
        candidates += WidgetEsseneCalendar.feastsForYear(year + 1)
        candidates += WidgetKaraiteCalendar.feastsForYear(year)
        candidates += WidgetKaraiteCalendar.feastsForYear(year + 1)

        // Kotlin sortedBy is stable. Explicit source indices also preserve its
        // ordering for equal dates on Swift versions without stable-sort guarantees.
        let future = candidates.enumerated().filter {
            $0.element.jdn >= todayJDN && filter.includes($0.element.marker.calendar)
        }.sorted {
            if $0.element.jdn == $1.element.jdn { return $0.offset < $1.offset }
            return $0.element.jdn < $1.element.jdn
        }
        var seen = Set<String>()
        var result: [WidgetUpcomingFeast] = []
        for candidate in future {
            if result.count >= count { break }
            let feast = candidate.element
            let key = "\(feast.marker.id)_\(feast.marker.calendar.rawValue)_\(feast.jdn)"
            guard seen.insert(key).inserted else { continue }
            let gregorian = WidgetHebrewCalendar.jdnToGregorian(feast.jdn)
            result.append(WidgetUpcomingFeast(
                marker: feast.marker,
                year: gregorian.year,
                month: gregorian.month,
                day: gregorian.day,
                daysUntil: feast.jdn - todayJDN
            ))
        }
        return result
    }

    private static func daysInGregorianMonth(_ year: Int, _ month: Int) -> Int {
        switch month {
        case 1, 3, 5, 7, 8, 10, 12: return 31
        case 2: return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 ? 29 : 28
        default: return 30
        }
    }
}

private typealias WidgetDatedFeast = (jdn: Int, marker: WidgetFeastMarker)

private struct WidgetHebrewDate {
    let year: Int
    let monthIndex: Int
    let day: Int
}

private enum WidgetHebrewCalendar {
    private static let epoch = 347997
    private static let commonMonths = [
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
    ]
    private static let leapMonths = [
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
    ]

    static func isLeapYear(_ year: Int) -> Bool {
        (7 * year + 1) % 19 < 7
    }

    private static func elapsedDays(_ year: Int) -> Int {
        let cycles = (year - 1) / 19
        let remainder = (year - 1) % 19
        let monthsElapsed = 235 * cycles + 12 * remainder + (7 * remainder + 1) / 19
        let partsElapsed = 204 + 793 * (monthsElapsed % 1080)
        let hoursElapsed = 5 + 12 * monthsElapsed + 793 * (monthsElapsed / 1080) + partsElapsed / 1080
        let conjunctionDay = 1 + 29 * monthsElapsed + hoursElapsed / 24
        let conjunctionParts = 1080 * (hoursElapsed % 24) + partsElapsed % 1080

        // All molad rules inspect the original conjunction and postpone once.
        var day = conjunctionDay
        if conjunctionParts >= 19440 ||
            (conjunctionDay % 7 == 2 && conjunctionParts >= 9924 && !isLeapYear(year)) ||
            (conjunctionDay % 7 == 1 && conjunctionParts >= 16789 && isLeapYear(year - 1)) {
            day += 1
        }
        let dow = day % 7
        if dow == 0 || dow == 3 || dow == 5 { day += 1 }
        return day
    }

    private static func newYearJDN(_ year: Int) -> Int {
        epoch + elapsedDays(year)
    }

    private static func yearLength(_ year: Int) -> Int {
        newYearJDN(year + 1) - newYearJDN(year)
    }

    static func monthNames(_ year: Int) -> [String] {
        isLeapYear(year) ? leapMonths : commonMonths
    }

    private static func daysInMonth(_ year: Int, _ monthIndex: Int) -> Int {
        let name = monthNames(year)[monthIndex]
        let length = yearLength(year)
        switch name {
        case "Tishrei", "Shevat", "Adar I", "Nisan", "Sivan", "Av": return 30
        case "Cheshvan": return length % 10 == 5 ? 30 : 29
        case "Kislev": return length % 10 == 3 ? 29 : 30
        case "Tevet", "Adar", "Adar II", "Iyar", "Tammuz", "Elul": return 29
        default: return 30
        }
    }

    static func gregorianToJDN(_ year: Int, _ month: Int, _ day: Int) -> Int {
        let a = (14 - month) / 12
        let y = year + 4800 - a
        let m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    static func jdnToGregorian(_ jdn: Int) -> (year: Int, month: Int, day: Int) {
        let a = jdn + 32044
        let b = (4 * a + 3) / 146097
        let c = a - 146097 * b / 4
        let d = (4 * c + 3) / 1461
        let e = c - 1461 * d / 4
        let m = (5 * e + 2) / 153
        let day = e - (153 * m + 2) / 5 + 1
        let month = m + 3 - 12 * (m / 10)
        let year = 100 * b + d - 4800 + m / 10
        return (year, month, day)
    }

    static func jdnToHebrew(_ jdn: Int) -> WidgetHebrewDate {
        var year = (jdn - epoch) * 19 / 6940 + 1
        while newYearJDN(year + 1) <= jdn { year += 1 }
        while newYearJDN(year) > jdn { year -= 1 }
        var dayInYear = jdn - newYearJDN(year)
        let months = monthNames(year)
        var monthIndex = 0
        while monthIndex < months.count - 1 {
            let monthDays = daysInMonth(year, monthIndex)
            if dayInYear < monthDays { break }
            dayInYear -= monthDays
            monthIndex += 1
        }
        return WidgetHebrewDate(year: year, monthIndex: monthIndex, day: dayInYear + 1)
    }

    private static func hebrewToJDN(_ year: Int, _ monthIndex: Int, _ day: Int) -> Int {
        var jdn = newYearJDN(year)
        for month in 0..<monthIndex { jdn += daysInMonth(year, month) }
        return jdn + day - 1
    }

    // Sunday = 0, Saturday = 6.
    static func dayOfWeek(_ jdn: Int) -> Int {
        (jdn + 1) % 7
    }

    static func feastsForYear(_ year: Int) -> [WidgetDatedFeast] {
        var result: [WidgetDatedFeast] = []
        func add(
            _ monthName: String, _ day: Int, _ id: String, _ display: String,
            _ spring: Bool, dayOfFeast: Int = 0, totalDays: Int = 1
        ) {
            guard let month = monthNames(year).firstIndex(of: monthName) else { return }
            result.append((hebrewToJDN(year, month, day), WidgetFeastMarker(
                id: id, displayName: display, isSpring: spring, calendar: .hebrew,
                dayOfFeast: dayOfFeast, totalDays: totalDays
            )))
        }
        add("Nisan", 14, "passover", "Passover", true)
        for day in 15...21 {
            add("Nisan", day, "unleavened", "Unleavened Bread", true, dayOfFeast: day - 14, totalDays: 7)
        }
        add("Nisan", 16, "firstfruits", "Firstfruits", true)
        add("Iyar", 14, "second_passover", "Second Passover", true)
        add("Sivan", 6, "pentecost", "Pentecost", true)
        add("Tishrei", 1, "trumpets", "Trumpets", false, dayOfFeast: 1, totalDays: 2)
        add("Tishrei", 2, "trumpets", "Trumpets", false, dayOfFeast: 2, totalDays: 2)
        add("Tishrei", 10, "atonement", "Day of Atonement", false)
        for day in 15...21 {
            add("Tishrei", day, "tabernacles", "Tabernacles", false, dayOfFeast: day - 14, totalDays: 7)
        }
        add("Tishrei", 22, "assembly", "Shemini Atzeret", false)
        return result
    }

    static func traditionForYear(_ year: Int) -> [WidgetDatedFeast] {
        var result: [WidgetDatedFeast] = []
        func add(
            _ monthName: String, _ day: Int, _ id: String, _ display: String,
            dayOfFeast: Int = 0, totalDays: Int = 1
        ) {
            guard let month = monthNames(year).firstIndex(of: monthName) else { return }
            result.append((hebrewToJDN(year, month, day), WidgetFeastMarker(
                id: id, displayName: display, isSpring: false, calendar: .hebrew,
                dayOfFeast: dayOfFeast, totalDays: totalDays
            )))
        }
        let adar = isLeapYear(year) ? "Adar II" : "Adar"
        add("Tevet", 10, "fast_tevet", "10th of Tevet (fast)")
        add(adar, 13, "fast_esther", "Fast of Esther")
        add(adar, 14, "purim", "Purim")
        add(adar, 15, "purim", "Shushan Purim")
        add("Tammuz", 17, "fast_tammuz", "17th of Tammuz (fast)")
        add("Av", 9, "tisha_bav", "Tisha B\u{2019}Av (fast)")
        add("Tishrei", 3, "fast_gedaliah", "Fast of Gedaliah")
        // Preserve the shared generator's explicit Kislev/Tevet dates, including
        // Kislev 30 in short years; do not silently change shared calendar rules.
        add("Kislev", 25, "hanukkah", "Hanukkah", dayOfFeast: 1, totalDays: 8)
        for day in 26...30 {
            add("Kislev", day, "hanukkah", "Hanukkah", dayOfFeast: day - 24, totalDays: 8)
        }
        add("Tevet", 1, "hanukkah", "Hanukkah", dayOfFeast: 7, totalDays: 8)
        add("Tevet", 2, "hanukkah", "Hanukkah", dayOfFeast: 8, totalDays: 8)
        return result
    }
}

private enum WidgetEsseneCalendar {
    private static let monthLengths = [30, 30, 31, 30, 30, 31, 30, 30, 31, 30, 30, 31]

    private static func yearStartJDN(_ year: Int) -> Int {
        // Same annual reanchor and March 20 equinox approximation as shared.
        let equinoxJDN = WidgetHebrewCalendar.gregorianToJDN(year, 3, 20)
        let dow = WidgetHebrewCalendar.dayOfWeek(equinoxJDN)
        return equinoxJDN + (3 - dow + 7) % 7
    }

    private static func esseneToJDN(_ year: Int, _ month: Int, _ day: Int) -> Int {
        var offset = 0
        for index in 0..<(month - 1) { offset += monthLengths[index] }
        return yearStartJDN(year) + offset + day - 1
    }

    static func feastsForYear(_ year: Int) -> [WidgetDatedFeast] {
        var result: [WidgetDatedFeast] = []
        func add(
            _ month: Int, _ day: Int, _ id: String, _ display: String,
            _ spring: Bool, dayOfFeast: Int = 0, totalDays: Int = 1
        ) {
            result.append((esseneToJDN(year, month, day), WidgetFeastMarker(
                id: id, displayName: display, isSpring: spring, calendar: .essene,
                dayOfFeast: dayOfFeast, totalDays: totalDays
            )))
        }
        add(1, 14, "passover", "Passover", true)
        for day in 15...21 {
            add(1, day, "unleavened", "Unleavened Bread", true, dayOfFeast: day - 14, totalDays: 7)
        }
        // Coupled Sadducean count: Firstfruits 1/26; Pentecost 3/15 (+49 days).
        add(1, 26, "firstfruits", "Firstfruits", true)
        add(2, 14, "second_passover", "Second Passover", true)
        add(3, 15, "pentecost", "Pentecost", true)
        add(5, 3, "new_wine", "Feast of New Wine", false)
        add(6, 22, "new_oil", "Feast of New Oil", false)
        add(7, 1, "trumpets", "Trumpets", false)
        add(7, 10, "atonement", "Day of Atonement", false)
        for day in 15...21 {
            add(7, day, "tabernacles", "Tabernacles", false, dayOfFeast: day - 14, totalDays: 7)
        }
        add(7, 22, "assembly", "Shemini Atzeret", false)
        add(4, 1, "memorial_q2", "Memorial Day Q2", false)
        add(10, 1, "memorial_q4", "Memorial Day Q4", false)
        return result
    }
}

private enum WidgetKaraiteCalendar {
    private static let pi = 3.141592653589793

    private static func sinApprox(_ x: Double) -> Double {
        // Int conversion truncates toward zero, matching Kotlin Double.toInt.
        let t = x - Double(Int(x / (2 * pi))) * 2 * pi
        var accumulator = 0.0
        var term = t
        var n = 1
        while abs(term) > 1e-10 && n < 30 {
            accumulator += term
            term *= -t * t / Double((2 * n) * (2 * n + 1))
            n += 1
        }
        return accumulator
    }

    private static func newMoonJDN(_ k: Double) -> Double {
        let centuries = k / 1236.85
        let centuries2 = centuries * centuries
        let centuries3 = centuries2 * centuries
        let centuries4 = centuries3 * centuries
        var jde = 2451550.09766 + 29.530588861 * k +
            0.00015437 * centuries2 - 0.000000150 * centuries3 + 0.00000000073 * centuries4
        let eccentricity = 1 - 0.002516 * centuries - 0.0000074 * centuries2
        let solarAnomaly = (2.5534 + 29.1053567 * k - 0.0000014 * centuries2 - 0.00000011 * centuries3) * pi / 180.0
        let lunarAnomaly = (201.5643 + 385.81693528 * k + 0.0107582 * centuries2 +
            0.00001238 * centuries3 - 0.000000058 * centuries4) * pi / 180.0
        let latitudeArgument = (160.7108 + 390.67050284 * k - 0.0016118 * centuries2 -
            0.00000227 * centuries3 + 0.000000011 * centuries4) * pi / 180.0
        let ascendingNode = (124.7746 - 1.56375588 * k + 0.0020672 * centuries2 + 0.00000215 * centuries3) * pi / 180.0
        jde += -0.40720 * sinApprox(lunarAnomaly)
        jde += 0.17241 * eccentricity * sinApprox(solarAnomaly)
        jde += 0.01608 * sinApprox(2 * lunarAnomaly)
        jde += 0.01039 * sinApprox(2 * latitudeArgument)
        jde += 0.00739 * eccentricity * sinApprox(lunarAnomaly - solarAnomaly)
        jde += -0.00514 * eccentricity * sinApprox(lunarAnomaly + solarAnomaly)
        jde += 0.00208 * eccentricity * eccentricity * sinApprox(2 * solarAnomaly)
        jde += -0.00111 * sinApprox(lunarAnomaly - 2 * latitudeArgument)
        jde += -0.00057 * sinApprox(lunarAnomaly + 2 * latitudeArgument)
        jde += 0.00056 * eccentricity * sinApprox(2 * lunarAnomaly + solarAnomaly)
        jde += -0.00042 * sinApprox(3 * lunarAnomaly)
        jde += 0.00042 * eccentricity * sinApprox(solarAnomaly + 2 * latitudeArgument)
        jde += 0.00038 * eccentricity * sinApprox(solarAnomaly - 2 * latitudeArgument)
        jde += -0.00024 * eccentricity * sinApprox(2 * lunarAnomaly - solarAnomaly)
        jde += -0.00017 * sinApprox(ascendingNode)
        return jde
    }

    private static func nisanOneJDN(_ year: Int) -> Int {
        let equinoxJDN = Double(WidgetHebrewCalendar.gregorianToJDN(year, 3, 20)) + 0.5
        let approxK = Double(year - 2000) * 12.3685 + 3.0
        // Kotlin math.round uses ties-to-even; Swift's default round does not.
        var k = approxK.rounded(.toNearestOrEven)
        while newMoonJDN(k) > equinoxJDN + 35 { k -= 1 }
        while newMoonJDN(k) < equinoxJDN - 35 { k += 1 }
        var bestK = k
        var bestDistance = abs(newMoonJDN(k) - equinoxJDN)
        for offset in -2...2 {
            let candidate = k + Double(offset)
            let distance = abs(newMoonJDN(candidate) - equinoxJDN)
            if distance < bestDistance - 0.5 {
                bestDistance = distance
                bestK = candidate
            }
        }
        return Int(newMoonJDN(bestK) + 1.5)
    }

    private static func monthStartJDN(_ year: Int, _ month: Int) -> Int {
        let nisan1 = Double(nisanOneJDN(year)) - 0.5
        let nisanNewMoon = nisan1 - 1.0
        let approxK = (nisanNewMoon - 2451550.09766) / 29.530588861
        let kNisan = approxK.rounded(.toNearestOrEven)
        let targetK = kNisan + Double(month - 1)
        return Int(newMoonJDN(targetK) + 1.5)
    }

    private static func firstfruitsJDN(_ year: Int) -> Int {
        let nisan15 = nisanOneJDN(year) + 14
        for jdn in nisan15...(nisan15 + 6) {
            if WidgetHebrewCalendar.dayOfWeek(jdn) == 6 { return jdn + 1 }
        }
        return nisan15
    }

    static func feastsForYear(_ year: Int) -> [WidgetDatedFeast] {
        var result: [WidgetDatedFeast] = []
        func add(
            _ jdn: Int, _ id: String, _ display: String,
            _ spring: Bool, dayOfFeast: Int = 0, totalDays: Int = 1
        ) {
            result.append((jdn, WidgetFeastMarker(
                id: id, displayName: display, isSpring: spring, calendar: .karaite,
                dayOfFeast: dayOfFeast, totalDays: totalDays
            )))
        }
        let nisan1 = nisanOneJDN(year)
        add(nisan1 + 13, "passover", "Passover", true)
        for offset in 0...6 {
            add(nisan1 + 14 + offset, "unleavened", "Unleavened Bread", true, dayOfFeast: offset + 1, totalDays: 7)
        }
        add(firstfruitsJDN(year), "firstfruits", "Firstfruits", true)
        add(monthStartJDN(year, 2) + 13, "second_passover", "Second Passover", true)
        add(firstfruitsJDN(year) + 49, "pentecost", "Pentecost", true)
        let tishri1 = monthStartJDN(year, 7)
        add(tishri1, "trumpets", "Trumpets", false)
        add(tishri1 + 9, "atonement", "Day of Atonement", false)
        for offset in 0...6 {
            add(tishri1 + 14 + offset, "tabernacles", "Tabernacles", false, dayOfFeast: offset + 1, totalDays: 7)
        }
        add(tishri1 + 21, "assembly", "Shemini Atzeret", false)
        return result
    }
}
