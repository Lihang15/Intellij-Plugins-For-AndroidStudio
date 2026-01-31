```mermaid
sequenceDiagram
    participant IDE as IntelliJ IDE
    participant Runner as MyMainCppDebugRunner
    participant Proc as LLDBDebugProcess
    participant Service as LLDBServiceWrapper
    participant BP as LLDBBreakpointHandler
    participant LLDB as LLDB Process (External)

    Note over IDE, LLDB: 1. 启动阶段
    IDE->>Runner: doExecute()
    Runner->>Runner: compile (clang++)
    Runner->>IDE: startSession()
    IDE->>Proc: sessionInitialized()
    Proc->>Service: start()
    Service->>LLDB: Launch lldb process
    Service-->>Proc: onConnected()

    Note over IDE, LLDB: 2. 初始化目标与断点
    Proc->>Service: loadTarget(exePath)
    Service->>LLDB: "target create ..."
    Proc->>BP: syncAllBreakpoints()
    BP->>Service: setBreakpoint(file, line)
    Service->>LLDB: "breakpoint set --file ... --line ..."

    Note over IDE, LLDB: 3. 运行程序
    Proc->>Service: run()
    Service->>LLDB: "process launch --stop-at-entry"
    Proc->>Service: resumeThread()
    Service->>LLDB: "continue"

    Note over IDE, LLDB: 4. 事件响应 (命中断点)
    LLDB-->>Service: "* thread #1, stop reason = breakpoint..."
    Service->>Proc: handleStopped(threadId)
    Proc->>Service: getStackTrace(threadId)
    Service->>LLDB: "thread backtrace"
    LLDB-->>Service: Frame information
    Service-->>Proc: List<StackFrame>
    Proc->>IDE: session.positionReached(SuspendContext)
    Note right of IDE: IDE 界面显示当前行、堆栈和变量