## About Project Amie

Project Amie was created to simplify Arduino board management and bridge the gap between microcontrollers and cloud computing platforms. 

> **Note on Project Status:** The project is currently in an unfinished, experimental state and lacks several core features. Because no formal tests have been written yet, expect unexpected runtime bugs.

### Architectural Vision
Bare-metal microcontrollers are incredibly affordable solutions that can act as the perfect "middlemen" for harvesting real-world data—such as voice signals, button events, and general IoT telemetry. Through Amie's Android UI, users can seamlessly manage, track, and toggle these connected Arduino boards.

### Planned Features
* **Multi-threaded Execution Engine:** A dedicated engine to continuously track, read from, and respond to multiple Arduino boards simultaneously.
* **Arduino Board Execution Platform:** Outsourcing harvested sensor and peripheral data to high-level cloud computation nodes.

---

## Features & UI Overview

### 1. Device Dashboard & Management
Provides a straightforward interface for creating devices and managing their operational states. It is fully compatible with any Android emulator or physical Android phone.

* **Configure (Beta):** An essential hub for viewing ongoing device processes and debugging (currently a work in progress).
* **Manage:** Dedicated space to configure device names, ports, and endpoints, as well as view live device logs.

<img src="Screenshot%202026-07-04%20at%2020.51.09.png" alt="Main Interface" max-width="500px" width="100%" />

### 2. Packages Overview
Allows you to download online firmware packages directly from a central repository. While custom package uploads are not yet supported, the download and deployment system is fully functional.

<img src="Screenshot%202026-07-04%20at%2020.52.32.png" alt="Task Flow" max-width="500px" width="100%" />

### 3. Terminal Shell (Upcoming)
An integrated runtime terminal where you will be able to view live stream logs, issue interrupt commands, and interact directly with Arduino boards during execution.

<img src="Screenshot%202026-07-04%20at%2020.53.07.png" alt="Logs and Execution" max-width="500px" width="100%" />

---

## Tech Stack & Libraries
* **Backend:** Spring Boot
* **Mobile Frontend:** Android Material UI, Ktor Client
* **Integrations:** Google API, various serialization libraries

---

## Arduino Memory & Execution Layout

Because standard Arduino programs execute sequentially inside a rigid `loop()`, tracking errors and managing conditional execution can get messy. Amie introduces the `IndentHandler` framework to bring structured, organized layout management to microcontroller tasks.

Here is a quick example showing how to schedule a repetitive "Hello World" task with a 1-second delay:

```cpp
// Initialize functions within the framework handle
auto *initializeFunctions = handlerProperties.initializeFunctions<voidFn, 1, 1>(
    [](){ delay(1000); }, 
    [](){ Serial.println("Hello World"); }
);

// Trigger the scheduled function sequence (index 0)
handlerProperties.callFunction(initializeFunctions, 0); 

// Print out the structural block metrics and captured string results
handlerProperties.printBlock(initializeFunctions);
```


A lightweight, template-driven task execution and logging framework designed for Arduino microcontrollers. It allows for conditional invocation of function pointers, safe decoupling of task logic, memory-allocated handle isolation, and macro-based file logging.
```markdown
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
