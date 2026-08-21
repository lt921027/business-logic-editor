// 脚本 5：排序、反转与 Top N
def n = 5000
def numbers = new ArrayList()

for (i in 1..n) {
    numbers << ((i * 37) % 1000)
}

numbers.sort()
numbers.reverse()

def top = numbers.subList(0, 10)
def bottom = numbers.subList(n - 10, n)
def topSum = 0

for (v in top) {
    topSum = topSum + v
}

return [
    top: top,
    topSum: topSum,
    bottomSize: bottom.size()
]
