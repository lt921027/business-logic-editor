// 脚本 2：字符串拼接与工具函数
def n = 5000
def parts = new ArrayList()
def builder = ""

for (i in 1..n) {
    parts << "item-" + i
}

for (part in parts) {
    builder = builder + part + ","
}

def cleaned = StringUtil.trim(builder)
def length = StringUtil.length(cleaned)
def containsMiddle = StringUtil.contains(cleaned, "item-2500")

return [
    size: parts.size(),
    length: length,
    containsMiddle: containsMiddle
]
