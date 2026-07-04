## Screenshots

![Main Interface](Screenshot%202026-07-04%20at%2020.51.09.png)

![Task Flow](Screenshot%202026-07-04%20at%2020.52.32.png)

![Logs and Execution](Screenshot%202026-07-04%20at%2020.53.07.png)

Here is the exact layout preserved inside the downloadable file, properly wrapped inside a markdown code block so it will render correctly with fixed-width typography on GitHub, GitLab, or any local editor:

```markdown
# Arduino IndentHandler Framework

A lightweight, template-driven task execution and logging framework designed for Arduino microcontrollers. It allows for conditional invocation of function pointers, safe decoupling of task logic, memory-allocated handle isolation, and macro-based file logging.

## Architectural Structure

The ASCII diagram below illustrates the structural alignment, memory allocation boundaries, and compile-time trait routing managed by `IndentHandler`.

```text
               +=================================================+
               |               HandlerProperties                 |
               +=================================================+
                                        |
               Instantiates / Allocates / Manages Memory
                                        v
+=================================================================================+
|                     IndentHandler<T, N, C> [Root Handle]                       |
+=================================================================================+
|  - id[C] : unsigned int (Array mapping Execution IDs)                           |
|                                                                                 |
|  *holdRecord -------------> +------------------------------------------------+  |
|                             |                  CallStruct                    |  |
|                             +------------------------------------------------+  |
|                             | - ref_calls[64]   : const char* (Error strings)|  |
|                             | - index_calls[64] : const char* (Line numbers) |  |
|                             | - static_index_msg: int                        |  |
|                             | - static_index_idx: int                        |  |
|                             +------------------------------------------------+  |
|                                                                                 |
|  *holdTask ---------------> +------------------------------------------------+  |
|                             |              CodeBlock<T, N, C>                |  |
|                             +------------------------------------------------+  |
|                             | - invoke          : bool                       |  |
|                             | - booleanTasks[C] : bool                       |  |
|                             | - falseCalling[C] : bool (Killswitches)        |  |
|                             |                                                |  |
|                             | - task[N]         : T (Function Pointers)      |  |
|                             |     |--> [ rightIfValueX(), getNumber(), ... ] |  |
|                             |                                                |  |
|                             | - taskResponses[N] : TaskResponse<R>           |  |
|                             +------------------------------------------------+  |
+=============================================|===================================+
                                              |
                                     Inherits / Implements
                                              v
                              +-------------------------------+
                              |           BaseTrait           |
                              +-------------------------------+
                              | + virtual printSelf() = 0     |
                              | + virtual ~BaseTrait()        |
                              +---------------+---------------+
                                              ^
                                              |
                                              |
                              +---------------+---------------+
                              |       TaskResponse<T>         |
                              +-------------------------------+
                              | - value_type    : T           |
                              | - value         : T (Result)  |
                              | - arraylike     : bool        |
                              | - results[30]   : const char* |
                              | + printSelf()   : override    |
                              +-------------------------------+
