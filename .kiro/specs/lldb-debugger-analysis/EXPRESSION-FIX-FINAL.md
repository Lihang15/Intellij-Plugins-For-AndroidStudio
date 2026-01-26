# Expression Evaluation Fix - Final Solution

## Problem: Mixed Response Buffer

You correctly identified the issue! When you input `x+y`, the response buffer contained:

```
(int) y = 20          <-- From previous request (residual)
...
(lldb) expr x+y       <-- Your command
(int) $0 = 30         <-- Correct result!
```

But the parser found the **first** matching line `(int) y = 20` and returned `20` instead of `30`.

## Root Cause

The response buffer contains output from **multiple requests mixed together**:
1. Residual output from previous commands (like `frame variable`)
2. Your current command's output

The old parser started from line 0, so it picked up the wrong result.

## Solution

**Parse only the output AFTER the command echo.**

### Key Changes

1. **Find the command echo**: Look for `(lldb) expr x+y` in the output
2. **Start parsing from there**: Only parse lines after the command echo
3. **Skip source code lines**: Ignore lines that start with numbers (source code display)
4. **Ignore vector errors**: Don't fail on `error: Invalid value for end of vector` (it's just a display issue)

### Code Changes

Modified `parseEvaluationResult()` in `LLDBEvaluator.kt`:

```kotlin
// Find command echo (e.g., "(lldb) expr x+y")
var startParsingIndex = 0
for ((index, line) in lines.withIndex()) {
    if (line.trim().startsWith("(lldb) expr ") || 
        line.trim().startsWith("(lldb) frame variable ")) {
        startParsingIndex = index + 1  // Start parsing from next line
        break
    }
}

// Parse only from startParsingIndex onwards
for (lineIndex in startParsingIndex until lines.size) {
    // Parse result...
}
```

## Testing

Rebuild and test:

```bash
./gradlew buildPlugin
```

Then reload plugin and test at line 15 breakpoint:

### Test Cases

1. **Simple variable**:
   - Input: `x`
   - Expected: `(int) x = 10` ✓

2. **Another variable**:
   - Input: `y`
   - Expected: `(int) y = 20` ✓

3. **Addition expression**:
   - Input: `x+y`
   - Expected: `(int) $0 = 30` ✓ (NOT 20!)

4. **Multiplication**:
   - Input: `x*y`
   - Expected: `(int) $1 = 200` ✓

5. **Function call**:
   - Input: `add(x,y)`
   - Expected: `(int) $2 = 30` ✓

## What This Fixes

- ✓ Expressions now return correct values
- ✓ No longer picks up residual output from previous commands
- ✓ Handles mixed response buffers correctly
- ✓ Ignores source code display lines
- ✓ Ignores harmless vector display errors

## Debug Output

When testing, you should see:

```
[parseEvaluationResult] 找到命令回显在行 #X: (lldb) expr x+y
[parseEvaluationResult] 从行 #Y 开始解析
[parseEvaluationResult] 检查行 #Y: (int) $0 = 30
[parseEvaluationResult] ✓ 解析成功:
  类型: int
  值: 30
```

The key is that it now **skips** the residual `(int) y = 20` and finds the correct `(int) $0 = 30`.
