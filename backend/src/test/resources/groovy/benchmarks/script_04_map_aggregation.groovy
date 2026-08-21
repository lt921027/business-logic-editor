// 脚本 4：Map 聚合统计
def words = [
    "apple", "banana", "apple", "cherry",
    "banana", "apple", "date", "cherry",
    "banana", "banana", "cherry", "apple"
]
def counts = new HashMap()
def total = 0

for (w in words) {
    def old = counts.get(w)
    def next = old == null ? 1 : old + 1
    counts.put(w, next)
}

counts.each { k, v ->
    total = total + v
}

return [
    unique: counts.size(),
    total: total,
    apple: counts.get("apple")
]
