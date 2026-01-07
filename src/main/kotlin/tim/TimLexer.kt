package tim

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class TimLexer : LexerBase() {

    private lateinit var buffer: CharSequence
    private var start = 0
    private var end = 0
    private var tokenType: IElementType? = null

    private val keywords = setOf(
        "Timings",
        "WaveformTables",
        "WaveformTable",
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

        // {}
        if (c == '{' || c == '}') {
            end++
            tokenType = TimTokenTypes.BRACE
            return
        }

        // [x1]
        if (c == '[') {
            while (end < buffer.length && buffer[end] != ']') end++
            if (end < buffer.length) end++
            tokenType = TimTokenTypes.ANNOTATION
            return
        }

        // 标识符 / 关键字
        if (c.isLetter()) {
            while (end < buffer.length && buffer[end].isLetter()) end++
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
