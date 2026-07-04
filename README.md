Project Amie, was invented for ease of arduino board management and possibly making them interact with cloud computing platforms.
<br> We wrote no tests as proof, so Im wary that it has many unexpected bugs coming to resolve.
<br> Arduino boards are simply very cheap and anyone can afford them, where these boards could be plugged as some middle mans for processing external world data.
<br> Such as: callcenter's small ai agents for interacting with customers *exceptional latency.
<br> The whole project is in unfinished state and lacks many features that should be introduced to it.
<br> 
<br> Planned: multi threaded execution engine that would keep track of reading and responding to arduino boards
<br> 
<br> I provide a simple interface for creating a device and managing its state. It is fully compantible with any android emulator or just android phones.
<br> 
<br> Main interface is consisting of: 
<br> Manage: where you manage device name, port, endpoint and can see device's logs
<br> Configure (beta): doesn't work much, but is integral for managing interactions such as viewing devices currently ongoing processes or debugging 
![Main Interface](Screenshot%202026-07-04%20at%2020.51.09.png)
<br> 
<br> Packages overview: you can download online package from repository. There is no possiblity to upload your own package, but the download of them is fully working. 
![Task Flow](Screenshot%202026-07-04%20at%2020.52.32.png)
<br> 
<br> Terminal shell: here you would be able to view logs, send interrupt commands and work with arduino boards trhoughout the runtime process (this will be handled in incoming updates).
![Logs and Execution](Screenshot%202026-07-04%20at%2020.53.07.png)

<br>Here is memory layout of the arduino logic, where all of this is now managed through ```IndentHandler``` and has its methods to be called.
<br>Because arduino controllers are simply run in loops, I made a logic that helps you to track of every function whenever to be called or logged from error.
<br>
<br>Here is simple code for displaying "hello world" each 1s delay.
```cpp
auto *initializeFunctions = handlerProperties.initializeFunctions<intFn, 2, 2>(()[]{delay(1000), ()[]{Serial.println("Hello World")});
handlerProperties.callFunction(initializeFunctions, 0); //trigger function with the 1s delay
```
YOU NEED IMAGE HERE:

```markdown
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
