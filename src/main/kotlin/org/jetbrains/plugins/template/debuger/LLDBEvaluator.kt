package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator

/**
 * LLDB 表达式求值器 - 重构版
 * 参考 Flutter 的 DartVmServiceEvaluator 设计
 * 
 * 关键改进：
 * 1. 使用 LLDBServiceWrapper 发送命令
 * 2. 移除对 LLDBDebugSession 的依赖
 * 3. 简化表达式解析逻辑
 * 
 * 支持的功能：
 * 1. 变量名查询（如：x, sum, numbers）
 * 2. 简单表达式（如：x + 1）
 * 3. LLDB 命令（如：p x, expr x + y）
 */
class LLDBEvaluator(
    private val process: LLDBDebugProcess,
    private val threadId: Int,
    private val frameIndex: Int
) : XDebuggerEvaluator() {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBEvaluator::class.java)
    }
    
    init {
        LOG.info("创建求值器: threadId=$threadId, frameIndex=$frameIndex")
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
        println("\n========== [LLDBEvaluator.evaluate] 开始 ==========")
        LOG.info("=== 表达式求值 ===")
        LOG.info("表达式: \"$expression\"")
        LOG.info("threadId=$threadId, frameIndex=$frameIndex")
        
        println("[LLDBEvaluator] 表达式: \"$expression\"")
        println("[LLDBEvaluator] threadId=$threadId, frameIndex=$frameIndex")
        
        if (expression.isBlank()) {
            LOG.warn("表达式为空")
            println("[LLDBEvaluator] ✗ 表达式为空")
            println("========== [LLDBEvaluator.evaluate] 结束 ==========\n")
            callback.errorOccurred("表达式不能为空")
            return
        }
        
        // 清理表达式（去除首尾空格）
        val cleanExpression = expression.trim()
        
        // 构建 LLDB 表达式命令
        // 关键修复：不使用多行命令，直接用 expr 在当前帧执行
        val lldbCommand = when {
            cleanExpression.startsWith("p ") || 
            cleanExpression.startsWith("print ") -> {
                LOG.info("检测到 print 命令格式")
                println("[LLDBEvaluator] 检测到 print 命令格式")
                cleanExpression
            }
            cleanExpression.startsWith("expr ") ||
            cleanExpression.startsWith("expression ") -> {
                LOG.info("检测到 expr 命令格式")
                println("[LLDBEvaluator] 检测到 expr 命令格式")
                cleanExpression
            }
            cleanExpression.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")) -> {
                LOG.info("检测到简单变量名")
                println("[LLDBEvaluator] 检测到简单变量名")
                "frame variable $cleanExpression"
            }
            else -> {
                LOG.info("检测到表达式")
                println("[LLDBEvaluator] 检测到表达式")
                "expr $cleanExpression"
            }
        }
        
        LOG.info("LLDB 命令: $lldbCommand")
        println("[LLDBEvaluator] LLDB 命令:")
        println("--- 开始 ---")
        println(lldbCommand)
        println("--- 结束 ---")
        
        // 发送命令到 LLDB
        try {
            val serviceWrapper = process.getServiceWrapper()
            serviceWrapper.evaluateExpression(lldbCommand) { response ->
                LOG.info("LLDB 响应:\n$response")
                println("[LLDBEvaluator] 收到 LLDB 响应")
                
                // 解析响应
                parseEvaluationResult(cleanExpression, response, callback)
                println("========== [LLDBEvaluator.evaluate] 结束 ==========\n")
            }
        } catch (e: Exception) {
            LOG.error("求值异常: ${e.message}", e)
            println("[LLDBEvaluator] ✗ 求值异常: ${e.message}")
            e.printStackTrace()
            println("========== [LLDBEvaluator.evaluate] 结束 ==========\n")
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
        println("\n========== [LLDBEvaluator.parseEvaluationResult] 开始 ==========")
        LOG.info("=== 解析求值结果 ===")
        LOG.info("表达式: $expression")
        LOG.info("输出:\n$output")
        
        println("[parseEvaluationResult] 表达式: $expression")
        println("[parseEvaluationResult] 输出:")
        println("--- 开始 ---")
        println(output)
        println("--- 结束 ---")
        
        // 检查是否有错误
        if (output.contains("error:") && !output.contains("error: Invalid value for end of vector")) {
            val errorMsg = extractErrorMessage(output)
            LOG.error("求值失败: $errorMsg")
            println("[parseEvaluationResult] ✗ 求值失败: $errorMsg")
            println("========== [LLDBEvaluator.parseEvaluationResult] 结束 ==========\n")
            callback.errorOccurred(errorMsg)
            return
        }
        
        // 关键修复：只解析我们发送的命令之后的输出
        // 找到命令回显的位置，从那之后开始解析
        val lines = output.split("\n")
        var startParsingIndex = 0
        
        // 查找命令回显（例如：(lldb) expr x+y 或 (lldb) frame variable x）
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("(lldb) expr ") || 
                trimmed.startsWith("(lldb) frame variable ") ||
                trimmed.startsWith("(lldb) p ") ||
                trimmed.startsWith("(lldb) print ")) {
                startParsingIndex = index + 1  // 从命令回显的下一行开始解析
                println("[parseEvaluationResult] 找到命令回显在行 #$index: $trimmed")
                println("[parseEvaluationResult] 从行 #$startParsingIndex 开始解析")
                break
            }
        }
        
        // 解析变量格式: (type) name = value
        // 或表达式格式: (type) $0 = value
        val varPattern = """^\(([^)]+)\)\s+(?:\$\d+|[\w]+)\s*=\s*(.+)$""".toRegex()
        
        println("[parseEvaluationResult] 尝试匹配模式: (type) name = value")
        
        for (lineIndex in startParsingIndex until lines.size) {
            val trimmed = lines[lineIndex].trim()
            
            // 跳过空行和提示符
            if (trimmed.isEmpty() || 
                trimmed.startsWith("(lldb)") ||
                trimmed.startsWith("Process") ||
                trimmed.startsWith("thread #") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("frame #") ||
                trimmed.matches(Regex("^\\d+\\s+.*"))) {  // 跳过源代码行（以数字开头）
                continue
            }
            
            println("[parseEvaluationResult] 检查行 #$lineIndex: $trimmed")
            
            val match = varPattern.find(trimmed)
            if (match != null) {
                val varType = match.groupValues[1]
                val varValue = match.groupValues[2]
                
                LOG.info("解析成功: type=$varType, value=$varValue")
                println("[parseEvaluationResult] ✓ 解析成功:")
                println("  类型: $varType")
                println("  值: $varValue")
                
                // 创建 LLDBValue
                val variable = Variable(expression, varValue, varType)
                val lldbValue = LLDBValue(process, variable)
                println("========== [LLDBEvaluator.parseEvaluationResult] 结束 ==========\n")
                callback.evaluated(lldbValue)
                return
            } else {
                println("[parseEvaluationResult]   未匹配")
            }
        }
        
        // 如果没有匹配到标准格式，尝试提取任何看起来像结果的内容
        println("[parseEvaluationResult] 标准格式未匹配，尝试简化格式")
        
        val cleanOutput = lines
            .drop(startParsingIndex)  // 从命令回显之后开始
            .filter { line -> 
                val t = line.trim()
                t.isNotEmpty() && 
                !t.startsWith("(lldb)") && 
                !t.startsWith("Process") &&
                !t.startsWith("thread #") &&
                !t.startsWith("*") &&
                !t.startsWith("frame #") &&
                !t.matches(Regex("^\\d+\\s+.*"))  // 跳过源代码行
            }
            .joinToString("\n")
        
        if (cleanOutput.isNotEmpty()) {
            LOG.info("使用简化格式返回结果")
            println("[parseEvaluationResult] ✓ 使用简化格式:")
            println("  内容: $cleanOutput")
            
            val variable = Variable(expression, cleanOutput, "unknown")
            val lldbValue = LLDBValue(process, variable)
            println("========== [LLDBEvaluator.parseEvaluationResult] 结束 ==========\n")
            callback.evaluated(lldbValue)
        } else {
            LOG.warn("无法解析结果")
            println("[parseEvaluationResult] ✗ 无法解析结果")
            println("========== [LLDBEvaluator.parseEvaluationResult] 结束 ==========\n")
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
