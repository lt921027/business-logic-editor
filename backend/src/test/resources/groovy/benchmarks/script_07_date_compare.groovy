// 脚本 7：日期函数调用与比较
def date1 = "2023-01-15"
def date2 = "2025-06-20"
def date3 = "2024-03-10"

def months = GroovyDateFunctions.diffMonths(date1, date2)
def days = GroovyDateFunctions.diffDays(date1, date2)
def years = GroovyDateFunctions.diffYears(date1, date2)
def before = GroovyDateFunctions.before(date1, date2)
def after = GroovyDateFunctions.after(date3, date1)
def within = GroovyDateFunctions.withinLast12Months("2026-01-01")
def formatted = GroovyDateFunctions.format("2026-08-14")

return [
    months: months,
    days: days,
    years: years,
    before: before,
    after: after,
    within: within,
    formatted: formatted
]
