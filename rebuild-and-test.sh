#!/bin/bash

echo "========================================="
echo "强制重新编译插件"
echo "========================================="

# 1. 清理所有构建文件
echo "1. 清理构建文件..."
./gradlew clean

# 2. 删除 build 目录
echo "2. 删除 build 目录..."
rm -rf build/

# 3. 重新编译
echo "3. 重新编译..."
./gradlew build -x test -x buildSearchableOptions

# 4. 检查编译结果
if [ $? -eq 0 ]; then
    echo "========================================="
    echo "✅ 编译成功！"
    echo "========================================="
    echo ""
    echo "下一步："
    echo "1. 完全关闭 Android Studio"
    echo "2. 重新打开 Android Studio"
    echo "3. 运行插件 (Run Plugin)"
    echo "4. 在新窗口中查看控制台输出"
    echo ""
    echo "应该看到的日志："
    echo "========================================"
    echo "=== TestToolbarAction.update() CALLED ==="
    echo "========================================"
    echo ""
    echo "或者："
    echo "========================================"
    echo "=== UnifiedDeviceSelectorAction.createCustomComponent() ==="
    echo "========================================"
else
    echo "========================================="
    echo "❌ 编译失败！"
    echo "========================================="
    exit 1
fi
