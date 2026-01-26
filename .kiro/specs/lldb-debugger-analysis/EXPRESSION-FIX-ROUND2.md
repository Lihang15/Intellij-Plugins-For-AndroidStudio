# Expression Evaluation Fix - Round 2

## Problem Diagnosis

From the debug logs, I identified the exact issue:

### What Was Happening

1. **User inputs**: `x+y`
2. **Code generates**: `frame select 0\nexpr x+y` (2 lines)
3. **sendCommand sends**:
   - Line 0: `frame select 0`
   - Line 1: `expr x+y`
4. **LLDB executes**: Only `frame select 0`
5. **Response received**: Contains output from `frame variable` (NOT `expr x+y`)
6. **Result**: Shows `x = 10` instead of `30`

### Evidence from Logs

```
[sendCommand]   发送行 #0: frame select 0
[evaluateExpression] 响应内容:
...
(lldb) frame variable    <-- Wrong command executed!
(int) x = 10
(int) y = 20
...
```

Then later:
```
[sendCommand]   发送行 #1: expr x+y
[sendCommand] ✓ 所有命令行已发送
[sendCommand] SEQ=14, 命令:    <-- New request started!
frame select 0
frame variable
```

**The `expr x+y` command was never executed!** Instead, it got mixed up with the next request.

## Root Cause

The multi-line command response buffering has a race condition:

1. We send `frame select 0` (line 0)
2. LLDB starts responding
3. Before the response is complete, we send `expr x+y` (line 1)
4. The response buffer triggers callback too early (after line 0's response)
5. Line 1's response is either:
   - Lost completely
   - Mixed into the next request's response
   - Causes the next request to execute instead

This is why we saw `frame variable` output instead of `expr x+y` output.

## Solution

**Stop using multi-line commands for expression evaluation.**

LLDB's `expr` command automatically runs in the current frame context, so we don't need `frame select` at all!

### Changes Made

**Before**:
```kotlin
"frame select $frameIndex\nexpr $cleanExpression"  // 2 lines
```

**After**:
```kotlin
"expr $cleanExpression"  // 1 line - simpler and works!
```

### Why This Works

- LLDB `expr` command runs in the **current stopped frame** by default
- We don't need to explicitly select the frame
- Single-line commands avoid the response buffering race condition
- Much simpler and more reliable

## Testing

Rebuild and test:

```bash
./gradlew buildPlugin
```

Then reload plugin and test these expressions at line 15 breakpoint:

1. `x` → Should show `(int) x = 10`
2. `y` → Should show `(int) y = 20`
3. `x+y` → Should show `(int) $0 = 30` ✓
4. `x*y` → Should show `(int) $1 = 200` ✓
5. `add(x,y)` → Should show `(int) $2 = 30` ✓

All expressions should now work correctly!
