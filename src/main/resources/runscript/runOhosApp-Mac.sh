#!/bin/bash
set -e

# ====================== 【1. 默认配置与参数解析】 ======================
DEFAULT_PLATFORM="ohosArm64"
DEFAULT_TARGET_ID="127.0.0.1:5555"
DEFAULT_BUNDLE_NAME="com.example.harmonyapp"
DEFAULT_ABILITY_NAME="EntryAbility"

usage() {
    echo "用法: $0 [选项] [PLATFORM] [TARGET_ID]"
    echo ""
    echo "参数:"
    echo "  PLATFORM      构建平台 (默认: $DEFAULT_PLATFORM)"
    echo "  TARGET_ID     设备 ID (默认: $DEFAULT_TARGET_ID)"
    echo ""
    echo "选项:"
    echo "  -b BUNDLE     设置包名 (当前: $DEFAULT_BUNDLE_NAME)"
    echo "  -a ABILITY    设置 Ability 名 (当前: $DEFAULT_ABILITY_NAME)"
    echo "  -h            显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 ohosArm64 127.0.0.1:5555"
    echo "  $0 -b com.test.app -a MainAbility"
    exit 0
}

# 预设变量
BUNDLE_NAME=$DEFAULT_BUNDLE_NAME
ABILITY_NAME=$DEFAULT_ABILITY_NAME

# 解析选项
while getopts "b:a:h" opt; do
    case $opt in
        b) BUNDLE_NAME=$OPTARG ;;
        a) ABILITY_NAME=$OPTARG ;;
        h) usage ;;
        ?) usage ;;
    esac
done

# 移除已解析的选项
shift $((OPTIND-1))

# 获取位置参数
PLATFORM=${1:-$DEFAULT_PLATFORM}
TARGET_ID=${2:-$DEFAULT_TARGET_ID}

echo -e "\033[32m▶ 运行环境配置:\033[0m"
echo "  - 平台: $PLATFORM"
echo "  - 设备: $TARGET_ID"
echo "  - 包名: $BUNDLE_NAME"
echo "  - Ability: $ABILITY_NAME"
echo "------------------------------------------------------------"

# ====================== 【2. 执行 Gradle 构建】 ======================
echo "Working path: $(pwd)"
echo "📦 正在构建 OpenHarmony ARM64 版本..."
if [ "$PLATFORM" = "ohosArm64" ]; then
    # 在项目根目录执行 Gradle 构建
    ./gradlew :composeApp:publishDebugBinariesToHarmonyApp
elif [ "$PLATFORM" = "iosSimulatorArm64" ]; then
    ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
else
    echo -e "\033[31m❌ 错误: 不支持的平台 '$PLATFORM'\033[0m"
    exit 4
fi

# 切换到 harmonyApp 目录执行后续的 OHOS 命令
if [ ! -d "harmonyApp" ]; then
    echo -e "\033[31m❌ 错误: 找不到 harmonyApp 目录\033[0m"
    exit 4
fi
cd harmonyApp
echo "切换到 harmonyApp 目录: $(pwd)"

# ====================== 【3. 环境路径与 SDK 配置】 ======================
SDK_HOME=/Applications/DevEco-Studio.app/Contents
HDC_BIN=$SDK_HOME/sdk/default/openharmony/toolchains/hdc
export DEVECO_SDK_HOME=$SDK_HOME/sdk
export PATH=$DEVECO_SDK_HOME:$SDK_HOME/jbr/Contents/Home/bin:$SDK_HOME/tools/node/bin:$SDK_HOME/tools/ohpm/bin:$SDK_HOME/tools/hvigor/bin:$PATH

echo "⚙️  正在进行 Hvigor 同步与 HAP 打包..."
ohpm install --all
node $SDK_HOME/tools/hvigor/bin/hvigorw.js --sync -p product=default --analyze=normal --parallel
node $SDK_HOME/tools/hvigor/bin/hvigorw.js --mode module -p module=entry@default -p product=default -p requiredDeviceType=phone assembleHap --analyze=normal --parallel  -p buildM

# ====================== 【4. 安装与推送调试组件】 ======================
AVAILABLE_TARGETS=$($HDC_BIN list targets)
HAP_FILE="./entry/build/default/outputs/default/entry-default-unsigned.hap"

if ! echo "$AVAILABLE_TARGETS" | grep -q "$TARGET_ID"; then
    echo -e "\033[31m❌ 错误: 设备 $TARGET_ID 不在线！\033[0m"
    exit 5
fi

echo "🚚 推送调试组件与安装 HAP..."
# 推送 lldb-server
$HDC_BIN -t $TARGET_ID shell mkdir -p /data/local/tmp/debugserver
$HDC_BIN -t $TARGET_ID file send $SDK_HOME/sdk/default/hms/native/lldb/aarch64-linux-ohos/lldb-server /data/local/tmp/debugserver/
$HDC_BIN -t $TARGET_ID shell chmod 755 /data/local/tmp/debugserver/lldb-server

# 安装 HAP (使用临时目录)
REMOTE_HAP_DIR="/data/local/tmp/debug_install"
$HDC_BIN -t $TARGET_ID shell mkdir -p $REMOTE_HAP_DIR
$HDC_BIN -t $TARGET_ID file send $HAP_FILE $REMOTE_HAP_DIR/
$HDC_BIN -t $TARGET_ID shell bm install -p $REMOTE_HAP_DIR/

# ====================== 【5. 核心启动与调试挂载】 ======================
echo -e "\033[33m🚀 正在拉起应用并启动调试监听...\033[0m"

# 获取系统版本
SYSTEM_VERSION=$($HDC_BIN -t $TARGET_ID shell param get const.ohos.apiversion 2>/dev/null || echo "unknown")
echo "检测到系统版本: $SYSTEM_VERSION"

# 第一步：启动应用（不使用 -D 调试模式，兼容 5.1）
echo "  -> 执行 aa start (启动应用)..."
$HDC_BIN -t $TARGET_ID shell aa start -a $ABILITY_NAME -b $BUNDLE_NAME

# 等待应用启动
sleep 2

# 第二步：获取应用 PID
get_pid_func() {
    $HDC_BIN -t $TARGET_ID shell "pidof $BUNDLE_NAME" 2>/dev/null | tr -d '\r' | tr -d '\n' | awk '{print $1}'
}

echo -n "⏳ 正在等待应用启动"
MAX_WAIT=12
COUNT=0
APP_PID=""
while [ $COUNT -lt $MAX_WAIT ]; do
    APP_PID=$(get_pid_func)
    if [[ "$APP_PID" =~ ^[0-9]+$ ]]; then
        echo -e "\n✅ 应用已启动 (PID: $APP_PID)"
        break
    fi
    echo -n "."
    sleep 1
    let COUNT=COUNT+1
done

if [ -z "$APP_PID" ]; then
    echo -e "\n\033[31m❌ 失败: 应用未能在预期内启动！\033[0m"
    exit 1
fi

# 第三步：启动 lldb-server 并附加到进程
echo "  -> 启动 lldb-server 并附加到进程 (PID: $APP_PID)..."
$HDC_BIN -t $TARGET_ID shell "/data/local/tmp/debugserver/lldb-server platform --listen unix-abstract:///lldb-server/platform.sock --server" &

# 等待 lldb-server 启动
sleep 2

echo "------------------------------------------------------------"
echo -e "\033[32m🎉 构建、安装与应用启动已完成！\033[0m"
echo -e "应用信息:"
echo -e "  - 包名: $BUNDLE_NAME"
echo -e "  - PID: $APP_PID"
echo -e "  - 设备: $TARGET_ID"
echo -e "\033[36m提示: 现在可以通过 LLDB 连接到设备进行调试\033[0m"
echo "------------------------------------------------------------"