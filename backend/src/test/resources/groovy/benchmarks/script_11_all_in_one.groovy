// 合并脚本：在同一个脚本文件中执行全部 10 个基准脚本的逻辑
def results = new LinkedHashMap()

// 1. 基础算术与统计
def n1 = 8000
def sum1 = 0
def squareSum1 = 0
def min1 = 1000000000
def max1 = -1000000000
for (i1 in 1..n1) {
    sum1 = sum1 + i1
    squareSum1 = squareSum1 + i1 * i1
    if (i1 < min1) { min1 = i1 }
    if (i1 > max1) { max1 = i1 }
}
results["script01"] = [sum: sum1, squareSum: squareSum1, min: min1, max: max1]

// 2. 字符串拼接
def n2 = 5000
def parts2 = new ArrayList()
def builder2 = ""
for (i2 in 1..n2) { parts2 << "item-" + i2 }
for (part2 in parts2) { builder2 = builder2 + part2 + "," }
def cleaned2 = StringUtil.trim(builder2)
results["script02"] = [size: parts2.size(), length: StringUtil.length(cleaned2), containsMiddle: StringUtil.contains(cleaned2, "item-2500")]

// 3. 列表筛选转换
def n3 = 6000
def values3 = new ArrayList()
def filtered3 = new ArrayList()
def mapped3 = new ArrayList()
def total3 = 0
for (i3 in 1..n3) { values3 << i3 }
for (v3 in values3) {
    if (v3 % 2 == 0) {
        filtered3 << v3
        mapped3 << v3 * 3
        total3 = total3 + v3
    }
}
results["script03"] = [filteredCount: filtered3.size(), mappedFirst: mapped3.get(0), total: total3]

// 4. Map 聚合
def words4 = ["apple", "banana", "apple", "cherry", "banana", "apple", "date", "cherry", "banana", "banana", "cherry", "apple"]
def counts4 = new HashMap()
def total4 = 0
for (w4 in words4) {
    def old4 = counts4.get(w4)
    def next4 = old4 == null ? 1 : old4 + 1
    counts4.put(w4, next4)
}
counts4.each { k4, v4 -> total4 = total4 + v4 }
results["script04"] = [unique: counts4.size(), total: total4, apple: counts4.get("apple")]

// 5. 排序与 TopN
def n5 = 5000
def numbers5 = new ArrayList()
for (i5 in 1..n5) { numbers5 << ((i5 * 37) % 1000) }
numbers5.sort()
numbers5.reverse()
def top5 = numbers5.subList(0, 10)
def bottom5 = numbers5.subList(n5 - 10, n5)
def topSum5 = 0
for (v5 in top5) { topSum5 = topSum5 + v5 }
results["script05"] = [top: top5, topSum: topSum5, bottomSize: bottom5.size()]

// 6. 斐波那契
def n6 = 30
def a6 = 0
def b6 = 1
def fib6 = new ArrayList()
for (i6 in 0..<n6) {
    def next6 = a6 + b6
    fib6 << next6
    a6 = b6
    b6 = next6
}
def sum6 = 0
for (v6 in fib6) { sum6 = sum6 + v6 }
results["script06"] = [fibN: fib6.get(n6 - 1), sum: sum6, count: fib6.size()]

// 7. 日期比较
def date1 = "2023-01-15"
def date2 = "2025-06-20"
def date3 = "2024-03-10"
def months7 = GroovyDateFunctions.diffMonths(date1, date2)
def days7 = GroovyDateFunctions.diffDays(date1, date2)
def years7 = GroovyDateFunctions.diffYears(date1, date2)
def before7 = GroovyDateFunctions.before(date1, date2)
def after7 = GroovyDateFunctions.after(date3, date1)
def within7 = GroovyDateFunctions.withinLast12Months("2026-01-01")
def formatted7 = GroovyDateFunctions.format("2026-08-14")
results["script07"] = [months: months7, days: days7, years: years7, before: before7, after: after7, within: within7, formatted: formatted7]

// 8. JSONPath 读取
def json8 = '''
{
  "order": {
    "items": [
      {"price": 100, "qty": 2},
      {"price": 250, "qty": 1},
      {"price": 80, "qty": 5}
    ],
    "discount": 50
  }
}
'''
def prices8 = JsonPathUtil.read(json8, '$.order.items[*].price')
def total8 = 0
for (p8 in prices8) { total8 = total8 + p8 }
def discount8 = JsonPathUtil.readInt(json8, '$.order.discount')
def orderId8 = JsonPathUtil.readString(json8, '$.order.id')
results["script08"] = [total: total8, discount: discount8, orderId: orderId8]

// 9. 嵌套循环矩阵
def size9 = 120
def matrix9 = new ArrayList()
def rowSum9 = new ArrayList()
def total9 = 0
for (i9 in 0..<size9) {
    def row9 = new ArrayList()
    def rs9 = 0
    for (j9 in 0..<size9) {
        def value9 = (i9 + 1) * (j9 + 1)
        row9 << value9
        rs9 = rs9 + value9
    }
    matrix9 << row9
    rowSum9 << rs9
    total9 = total9 + rs9
}
results["script09"] = [size: matrix9.size(), rowCount: rowSum9.size(), total: total9]

// 10. 闭包与集合操作
def n10 = 5000
def numbers10 = new ArrayList()
for (i10 in 1..n10) { numbers10 << i10 }
def evens10 = numbers10.findAll { it % 2 == 0 }
def doubled10 = evens10.collect { it * 2 }
def sum10 = doubled10.sum()
def capped10 = doubled10.take(10)
def last10 = doubled10.last()
results["script10"] = [evensCount: evens10.size(), sum: sum10, first: capped10.get(0), last: last10]

return results
