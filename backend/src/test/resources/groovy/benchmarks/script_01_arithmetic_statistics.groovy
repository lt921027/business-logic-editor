// 脚本 1：基础算术与统计计算
def n = 8000
def sum = 0
def squareSum = 0
def minVal = 1000000000
def maxVal = -1000000000

for (i in 1..n) {
    sum = sum + i
    squareSum = squareSum + i * i

    if (i < minVal) {
        minVal = i
    }
    if (i > maxVal) {
        maxVal = i
    }
}

return [
    count: n,
    sum: sum,
    squareSum: squareSum,
    average: sum / n,
    min: minVal,
    max: maxVal
]
