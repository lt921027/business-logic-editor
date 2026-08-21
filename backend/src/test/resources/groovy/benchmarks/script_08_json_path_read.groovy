// 脚本 8：JSONPath 数据读取与计算
def json = '''
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

def prices = JsonPathUtil.read(json, '$.order.items[*].price')
def total = 0

for (p in prices) {
    total = total + p
}

def discount = JsonPathUtil.readInt(json, '$.order.discount')
def orderId = JsonPathUtil.readString(json, '$.order.id')

return [
    total: total,
    discount: discount,
    orderId: orderId
]
