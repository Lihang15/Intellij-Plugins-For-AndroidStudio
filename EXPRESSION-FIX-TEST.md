# Expression Evaluation Fix - Test Guide

## What Was Fixed (Round 2)

**Previous Problem**: Multi-line commands were not being handled correctly
- Sent: `frame select 0\nexpr x+y`
- LLDB only executed `frame select 0`, then somehow executed `frame variable` instead
- The `expr x+y` command was lost or mixed with the next request

**Root Cause**: Response buffering issue with multi-line commands
- When sending 2 lines, the first line's response triggered the callback too early
- The second line's response was either lost or mixed into the next request
- This caused `expr x+y` to never execute, and `frame variable` to run instead

**Solution**: Simplified command strategy
- **Removed `frame select` prefix** - LLDB `expr` automatically runs in the current frame
- Now sends single-line commands: just `expr x+y` instead of `frame select 0\nexpr x+y`
- This avoids the multi-line response buffering issue entirely
- For simple variables: uses `frame variable x` (single line)
- For expressions: uses `expr x+y` (single line)

## How to Test

### 1. Rebuild and Reload Plugin
```bash
./gradlew buildPlugin
```
Then reload the plugin in IntelliJ (see PLUGIN-RELOAD-INSTRUCTIONS.md)

### 2. Set Breakpoint
- Open `my_main.cpp`
- Set breakpoint at line 15: `int sum = add(x, y);`

### 3. Start Debug Session
- Run the debug configuration
- Program should stop at line 15

### 4. Test Expressions in Debug Console

Try these expressions in order:

#### Test 1: Simple Variable
```
x
```
**Expected**: `(int) x = 10`

#### Test 2: Another Variable
```
y
```
**Expected**: `(int) y = 20`

#### Test 3: Expression (Addition)
```
x+y
```
**Expected**: `(int) $0 = 30`

#### Test 4: Expression (Multiplication)
```
x*y
```
**Expected**: `(int) $1 = 200`

#### Test 5: Complex Expression
```
(x+y)*2
```
**Expected**: `(int) $2 = 60`

### 5. Step Over and Test Again
- Click "Step Over" to execute line 15
- Now `sum` should be initialized
- Test:
```
sum
```
**Expected**: `(int) sum = 30`

## What to Look For

### Success Indicators ✓
- Each expression returns the correct value
- Complex expressions like `x+y` work correctly
- Variables show their actual values (not garbage)
- Debug logs show commands being sent line-by-line

### Debug Logs to Check
Look for these in the console:
```
[sendCommand] 命令行数: 2
[sendCommand]   发送行 #0: frame select 0
[sendCommand]   发送行 #1: expr x+y
[sendCommand] ✓ 所有命令行已发送
```

## If It Still Doesn't Work

Check the debug output for:
1. Are commands being sent correctly? (look for `[sendCommand]` logs)
2. What is LLDB responding? (look for `[evaluateExpression]` response)
3. Is the parsing working? (look for `[parseEvaluationResult]` logs)

The extensive logging should help identify where the issue is.
