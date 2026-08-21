// 脚本 10：Groovy 闭包与集合操作
def n = 5000
def numbers = new ArrayList()

for (i in 1..n) {
    numbers << i
}

def evens = numbers.findAll { it % 2 == 0 }
def doubled = evens.collect { it * 2 }
def sum = doubled.sum()
def capped = doubled.take(10)
def last = doubled.last()

return [
    evensCount: evens.size(),
    sum: sum,
    first: capped.get(0),
    last: last
]
