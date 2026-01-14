package org.jetbrains.plugins.template.debuger

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logSeparator
import com.google.gson.JsonObject

/**
 * DAP 表达式求值器
 * 
 * 支持的功能：
 * 1. 变量名查询（如：x, sum, numbers）
 * 2. 简单表达式（如：x + 1）
 * 3. LLDB 命令（如：p x, expr x + y）
 */
class DapEvaluator(
    private val dapSession: DapDebugSession,
    private val threadId: Int,
    private val frameId: Int
) : XDebuggerEvaluator() {
    
    init {
        log("DapEvaluator.init", "创建求值器: threadId=$threadId, frameId=$frameId")
    }
    
    /**
     * 计算表达式
     * 
     * @param expression 用户输入的表达式
     * @param callback 回调函数，返回计算结果
     * @param expressionPosition 表达式所在位置（可选）
     */
    override fun evaluate(
        expression: String,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?
    ) {
        logSeparator("evaluate", "表达式求值")
        log("evaluate", "表达式: \"$expression\"")
        log("evaluate", "threadId=$threadId, frameId=$frameId")
        
        if (expression.isBlank()) {
            log("evaluate", "表达式为空", "WARN")
            callback.errorOccurred("表达式不能为空")
            return
        }
        
        // 清理表达式（去除首尾空格）
        val cleanExpression = expression.trim()
        
        // 构建 LLDB 表达式命令
        // 关键修复：必须先选择正确的 frame，再执行表达式
        // 支持三种格式：
        // 1. 直接变量名: x → frame select frameId; frame variable x
        // 2. 表达式: x + 1 → frame select frameId; expr x + 1
        // 3. 已包含命令: p x → frame select frameId; p x
        val lldbCommand = when {
            cleanExpression.startsWith("p ") || 
            cleanExpression.startsWith("print ") ||
            cleanExpression.startsWith("expr ") ||
            cleanExpression.startsWith("expression ") -> {
                log("evaluate", "检测到 LLDB 命令格式")
                // 先选择正确的 frame
                "frame select $frameId\n$cleanExpression"
            }
            // 简单变量名（只包含字母、数字、下划线）
            cleanExpression.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")) -> {
                log("evaluate", "检测到简单变量名")
                // 先选择正确的 frame，再查询变量
                "frame select $frameId\nframe variable $cleanExpression"
            }
            // 其他情况当作表达式处理
            else -> {
                log("evaluate", "检测到表达式")
                // 先选择正确的 frame，再求值表达式
                "frame select $frameId\nexpr $cleanExpression"
            }
        }
        
        log("evaluate", "LLDB 命令: $lldbCommand")
        
        // 发送命令到 LLDB
        try {
            dapSession.evaluateExpression(lldbCommand) { response ->
                log("evaluate", "LLDB 响应:\n$response")
                
                // 解析响应
                parseEvaluationResult(cleanExpression, response, callback)
            }
        } catch (e: Exception) {
            log("evaluate", "求值异常: ${e.message}", "ERROR")
            e.printStackTrace()
            callback.errorOccurred("求值失败: ${e.message}")
        }
    }
    
    /**
     * 解析 LLDB 求值结果
     */
    private fun parseEvaluationResult(
        expression: String,
        output: String,
        callback: XEvaluationCallback
    ) {
        log("parseEvaluationResult", "=== 解析求值结果 ===")
        log("parseEvaluationResult", "表达式: $expression")
        log("parseEvaluationResult", "输出:\n$output")
        
        // 检查是否有错误
        if (output.contains("error:") || output.contains("no variable")) {
            val errorMsg = extractErrorMessage(output)
            log("parseEvaluationResult", "求值失败: $errorMsg", "ERROR")
            callback.errorOccurred(errorMsg)
            return
        }
        
        // 解析变量格式: (type) name = value
        // 或表达式格式: (type) $0 = value
        val varPattern = """^\(([^)]+)\)\s+(?:\$\d+|[\w]+)\s*=\s*(.+)$""".toRegex()
        
        for (line in output.split("\n")) {
            val trimmed = line.trim()
            
            // 跳过空行和提示符
            if (trimmed.isEmpty() || 
                trimmed.startsWith("(lldb)") ||
                trimmed.startsWith("Process") ||
                trimmed.startsWith("thread #") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("frame #")) {
                continue
            }
            
            val match = varPattern.find(trimmed)
            if (match != null) {
                val varType = match.groupValues[1]
                val varValue = match.groupValues[2]
                
                log("parseEvaluationResult", "解析成功: type=$varType, value=$varValue")
                
                // 创建 DapValue
                val resultJson = JsonObject()
                resultJson.addProperty("name", expression)
                resultJson.addProperty("value", varValue)
                resultJson.addProperty("type", varType)
                resultJson.addProperty("variablesReference", 0)
                
                val dapValue = DapValue(dapSession, resultJson)
                callback.evaluated(dapValue)
                return
            }
        }
        
        // 如果没有匹配到标准格式，尝试提取任何看起来像结果的内容
        val cleanOutput = output.lines()
            .filter { line -> 
                val t = line.trim()
                t.isNotEmpty() && 
                !t.startsWith("(lldb)") && 
                !t.startsWith("Process") &&
                !t.startsWith("thread #") &&
                !t.startsWith("*") &&
                !t.startsWith("frame #")
            }
            .joinToString("\n")
        
        if (cleanOutput.isNotEmpty()) {
            log("parseEvaluationResult", "使用简化格式返回结果")
            
            val resultJson = JsonObject()
            resultJson.addProperty("name", expression)
            resultJson.addProperty("value", cleanOutput)
            resultJson.addProperty("type", "unknown")
            resultJson.addProperty("variablesReference", 0)
            
            val dapValue = DapValue(dapSession, resultJson)
            callback.evaluated(dapValue)
        } else {
            log("parseEvaluationResult", "无法解析结果", "WARN")
            callback.errorOccurred("无法解析求值结果")
        }
    }
    
    /**
     * 从错误输出中提取错误消息
     */
    private fun extractErrorMessage(output: String): String {
        // 提取 "error:" 后面的内容
        val errorLine = output.lines().find { it.contains("error:") }
        if (errorLine != null) {
            val errorIndex = errorLine.indexOf("error:")
            return errorLine.substring(errorIndex).trim()
        }
        
        // 提取 "no variable" 等常见错误
        val noVarLine = output.lines().find { it.contains("no variable") }
        if (noVarLine != null) {
            return noVarLine.trim()
        }
        
        return "求值失败，请检查表达式"
    }
}
