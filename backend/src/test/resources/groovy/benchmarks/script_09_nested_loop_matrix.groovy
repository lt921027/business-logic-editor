// 脚本 9：二维矩阵运算（嵌套循环）
def size = 120
def matrix = new ArrayList()
def rowSum = new ArrayList()
def total = 0

for (i in 0..<size) {
    def row = new ArrayList()
    def rs = 0

    for (j in 0..<size) {
        def value = (i + 1) * (j + 1)
        row << value
        rs = rs + value
    }

    matrix << row
    rowSum << rs
    total = total + rs
}

return [
    size: matrix.size(),
    rowCount: rowSum.size(),
    total: total
]
