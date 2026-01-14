package lsp.tim

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class TimLexer : LexerBase() {

    private lateinit var buffer: CharSequence
    private var start = 0
    private var end = 0
    private var tokenType: IElementType? = null

    private val keywords = setOf(
        // 顶层关键字
        "Timings",
        "WaveformTables",
        "WaveformTable",
        "EquationSets",
        "EquationSet",
        "SpecificationSets",
        "SpecificationSet",
        "TimingSets",
        "TimingSet",
        "SpecVars",
        "Ports",
        // 内部关键字
        "Signal",
        "Double",
        "Time",
        "Used",
        "Unused",
        "Map",
        "Break",
        // 保留字
        "default"
    )

    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int
    ) {
        this.buffer = buffer
        this.start = startOffset
        this.end = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = start

    override fun getTokenEnd(): Int = end

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = buffer.length

    override fun advance() {
        if (end >= buffer.length) {
            tokenType = null
            return
        }

        start = end
        val c = buffer[start]

        // 空白
        if (c.isWhitespace()) {
            while (end < buffer.length && buffer[end].isWhitespace()) end++
            tokenType = TimTokenTypes.WHITESPACE
            return
        }

        // 注释 //
        if (c == '/' && end + 1 < buffer.length && buffer[end + 1] == '/') {
            while (end < buffer.length && buffer[end] != '\n') end++
            tokenType = TimTokenTypes.COMMENT
            return
        }

        // 大括号 {}
        if (c == '{' || c == '}') {
            end++
            tokenType = TimTokenTypes.BRACE
            return
        }

        // 中括号注解 [x1]
        if (c == '[') {
            while (end < buffer.length && buffer[end] != ']') end++
            if (end < buffer.length) end++
            tokenType = TimTokenTypes.ANNOTATION
            return
        }

        // 冒号
        if (c == ':') {
            end++
            tokenType = TimTokenTypes.COLON
            return
        }

        // 分号
        if (c == ';') {
            end++
            tokenType = TimTokenTypes.SEMICOLON
            return
        }

        // 数字
        if (c.isDigit()) {
            while (end < buffer.length && (buffer[end].isDigit() || buffer[end] == '.')) end++
            // 处理单位 ns, ms 等
            if (end < buffer.length && buffer[end].isLetter()) {
                while (end < buffer.length && buffer[end].isLetter()) end++
            }
            tokenType = TimTokenTypes.NUMBER
            return
        }

        // 标识符 / 关键字
        if (c.isLetter() || c == '_') {
            while (end < buffer.length && (buffer[end].isLetterOrDigit() || buffer[end] == '_')) end++
            val word = buffer.subSequence(start, end).toString()
            tokenType =
                if (word in keywords) TimTokenTypes.KEYWORD
                else TimTokenTypes.IDENTIFIER
            return
        }

        // 兜底：单字符
        end++
        tokenType = TimTokenTypes.IDENTIFIER
    }
}
