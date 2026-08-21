// 脚本 3：列表筛选、转换与求和
def n = 6000
def values = new ArrayList()
def filtered = new ArrayList()
def mapped = new ArrayList()
def total = 0

for (i in 1..n) {
    values << i
}

for (v in values) {
    if (v % 2 == 0) {
        filtered << v
        mapped << v * 3
        total = total + v
    }
}

return [
    filteredCount: filtered.size(),
    mappedFirst: mapped.get(0),
    total: total
]
