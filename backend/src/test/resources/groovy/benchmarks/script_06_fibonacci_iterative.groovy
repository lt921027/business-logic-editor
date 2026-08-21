// 脚本 6：迭代计算斐波那契数列
def n = 30
def a = 0
def b = 1
def fib = new ArrayList()

for (i in 0..<n) {
    def next = a + b
    fib << next
    a = b
    b = next
}

def sum = 0
for (v in fib) {
    sum = sum + v
}

return [
    fibN: fib.get(n - 1),
    sum: sum,
    count: fib.size()
]
