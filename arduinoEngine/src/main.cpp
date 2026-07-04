#include <Arduino.h>

template<typename T> struct TaskResponse;
struct CallStruct;
template <typename T, size_t N, size_t C> struct CodeBlock;
template <typename T, size_t N, size_t C> struct IndentHandler;

class BaseTrait {
public:
    virtual void printSelf() = 0;
    virtual ~BaseTrait() = default;
};

template <typename T> struct FunctionResult;
template <typename R, typename... Args>
struct FunctionResult<R(*)(Args...)> {
    using type = R;
};

template<typename T> struct TaskResponse : public BaseTrait {
    bool arraylike;
    const char* results[30];
    T value;
    using value_type = T;

    void printSelf() override {
        Serial.println(value);
    }
};

typedef int FNint;
typedef FNint (*ArduinoTaskInt)();

struct processWindows {
    BaseTrait* multipleResponses[10];
};

struct CallStruct {
    const char* ref_calls[64];
    const char* index_calls[64];
    int static_index_msg = 0;
    int static_index_idx = 0;
};

template <typename T, size_t N, size_t C> struct CodeBlock {
    bool invoke;
    T task[N];
    TaskResponse<typename FunctionResult<T>::type> taskResponses[N]; // ::type gets us encapsulated value of (int*)()
    bool booleanTasks[C];
    bool falseCalling[C];
};

template <typename T, size_t N, size_t C>
struct IndentHandler {
    unsigned int id[C];
    CallStruct *holdRecord;
    CodeBlock<T, N, C> *holdTask;
};

typedef int (*intFn)();
typedef int (*intFnOne)(int);
typedef int (*intFnTwo)(int, int);

class HandlerProperties {
public:
    template <typename T, size_t N, size_t C>
    void freeHandle(IndentHandler<T, N, C> *handler) {
        //free allocated memory, has to loop also inner structures
        if (handler != nullptr) {
            if (handler->holdRecord != nullptr) {
                for (int i = 0; i < handler->holdRecord->static_index_idx; i++) {
                    free((void*)handler->holdRecord->index_calls[i]);
                }
                free(handler->holdRecord);
            }
            if (handler->holdTask != nullptr) {
                free(handler->holdTask);
            }
            free(handler);
        }
    }

    template <typename T, size_t N, size_t C>
    IndentHandler<T, N, C> *allocateHandle() {
        //allocate handle, (initialized at the start of the program).
        using HandlerType = IndentHandler<T, N, C>;
        HandlerType *handler = (HandlerType *)malloc(sizeof(HandlerType));
        if (handler == nullptr) { return nullptr; }
        handler->holdRecord = (CallStruct*)malloc(sizeof(CallStruct));
        handler->holdTask = (CodeBlock<T, N, C>*)malloc(sizeof(CodeBlock<T, N, C>));

        if (handler->holdTask != nullptr) {
            handler->holdTask->invoke = false;
            for(size_t i = 0; i < C; i++) {
                handler->holdTask->falseCalling[i] = false;
            }
        }
        if (handler->holdRecord != nullptr) {
            handler->holdRecord->static_index_msg = 0;
            handler->holdRecord->static_index_idx = 0;
        }
        return handler;
    }

    template <typename T, size_t N, size_t C>
    void writeFunction(IndentHandler<T, N, C> *handler, CodeBlock<T, N, C> *block) {
        //writes or rewrites call stack
        if (handler == nullptr || handler->holdTask == nullptr || block == nullptr) return;
        for (size_t i = 0; i < N; i++) {
            handler->holdTask->task[i] = block->task[i];
        }
    }

    template <typename T, size_t N, size_t C>
    void saveResult(IndentHandler<T, N, C> *handler, CodeBlock<T, N, C> *block) {
        //saves return values from callee
        if (handler == nullptr || handler->holdTask == nullptr || block == nullptr) return;
        for (size_t i = 0; i < N; i++) {
            handler->holdTask->taskResponses[i] = block->taskResponses[i];
        }
    }

    template <typename FnType, size_t C, size_t T>
    IndentHandler<FnType, T, C>* initializeFunctions(const bool (&booleanFunctions)[C], const FnType (&callbacks)[T]){
        /* initializes callstack functions:
        * booleanFunctions: each of booleanFunction is specifying conditions that must be met for invoking callback
        * callbacks: functions that boolean constraints are tied to and can be killed with setting systemDispatcher to false
        */

        auto *handler = allocateHandle<FnType, T, C>();
        if (handler == nullptr || handler->holdTask == nullptr) { return nullptr; }

        for (size_t i = 0; i < T; i++) {
            handler->holdTask->task[i] = callbacks[i];
        }
        for (size_t i = 0; i < C; i++) {
            handler->id[i] = i;
            handler->holdTask->booleanTasks[i] = booleanFunctions[i];
        }
        return handler;
    }

    template <typename T, size_t N, size_t C>
    void callFunction(IndentHandler<T, N, C> *handler, unsigned int executionId) {
        //if handler was initialized, we can call functions from its callstack by id

        if (handler == nullptr || handler->holdTask == nullptr) return;
            for (size_t i = 0; i < C; i++) { //[missing]: bound safety
                /*falseCalling: introduced for a specific cases on these code handlers
                boolean executors still need tweaks, which means this isn't still very functional */
                if (handler->id[i] == executionId && !handler->holdTask->falseCalling[i]) {
                    handler->holdTask->invoke = true;
                    handler->holdTask->taskResponses[i].value = handler->holdTask->task[0]();
                }
            }
    }

    template <typename T, size_t N, size_t C>
    void printBlock(IndentHandler<T, N, C> *handler) {
        //prints handler's informatoin, values that it's holding and ID associated
        if (handler == nullptr || handler->holdTask == nullptr) return;
        for (size_t i = 0; i < C; i++) {
            Serial.print(F("ID: ")); Serial.print(handler->id[i]);
            Serial.print(F(" | Task Value: ")); Serial.println(handler->holdTask->taskResponses[i].value);
        }
    }

    template <typename T, size_t N, size_t C>
    void writingLog(IndentHandler<T, N, C> *handler, const char* err_ptr, int lineNumber) {
        //writes log to the handler log / recordTable, it generally holds error pointers or messages where program failed

        if (handler == nullptr || handler->holdRecord == nullptr) { return; }
        CallStruct* logger = handler->holdRecord;
        if (logger->static_index_msg >= 64 || logger->static_index_idx >= 64) { return; }

        logger->ref_calls[logger->static_index_msg] = err_ptr;
        char* lineStr = (char*)malloc(8);
        if (lineStr != nullptr) {
            itoa(lineNumber, lineStr, 10);
            logger->index_calls[logger->static_index_idx] = lineStr;
        } else {
            logger->index_calls[logger->static_index_idx] = "???";
        }
        logger->static_index_msg++;
        logger->static_index_idx++;
    }

    template <typename T, size_t N, size_t C>
    void pauseById(IndentHandler<T, N, C> *handler, unsigned int executionId) {
        //falseCaller: introduced to make sure function inside array would never be called
        if (handler == nullptr || handler->holdTask == nullptr) { return; }
        for (size_t i = 0; i < C; i++) {
            if (handler->id[i] == executionId) {
                handler->holdTask->falseCalling[i] = true;
            }
        }
    }
};

HandlerProperties handlerProperties;
#define WRITESTR(handler, err_ptr) handlerProperties.writingLog(handler, err_ptr, __LINE__) //__LINE__ compiler macro

//functions for handler
int rightIfValueX() {
    return 10;
}

int rightIfValueY() {
    return 20;
}

int getNumber() {
    return 30;
}

int getNumberT() {
    return 32;
}


void setup() {
    Serial.begin(9600);
    while (!Serial);
    Serial.println(F("\tConnected\t"));

    const bool boolArr[2] = {true, false};
    const intFn taskArr[2] = {getNumberT,getNumber};

    auto *initializeFunctions = handlerProperties.initializeFunctions<intFn, 2, 2>(boolArr, taskArr);
    //template's instance

    if (initializeFunctions != nullptr) {

        handlerProperties.callFunction(initializeFunctions, 0);
        handlerProperties.callFunction(initializeFunctions, 1);
        handlerProperties.callFunction(initializeFunctions, 2);

        Serial.println("Printing values after all function execution: ");
        handlerProperties.printBlock(initializeFunctions);

        handlerProperties.freeHandle(initializeFunctions);
    }
}

void loop() {
    delay(50000);
}

/*
#define STRINGIFY(x) #x
#define TOSTRING(x) STRINGIFY(x)

#define WRITESTR(logger, err_ptr) \
  logger.ref_calls[logger.static_index_msg] = err_ptr; \
  logger.index_calls[logger.static_index_idx] = TOSTRING(__LINE__); \
  logger.static_index_msg++; \
  logger.static_index_idx++;

#define CALLERFN(isSafe, callback, logger) do { \
  const char *err_ptr = NULL; \
  if (isSafe) { \
    callback(); \
  } else { \
    delay(500); \
    err_ptr = "TRACKBACK -> File: " __FILE__ " | Line: " TOSTRING(__LINE__); \
    WRITESTR(logger, err_ptr); \
    Serial.println(err_ptr); \
  } \
} while(0)

#define TRY_CALL(isSafe, callback, logger) CALLERFN(isSafe, callback, logger)

#define FUNCTION_BLOCK(Invoke, table, logger) do { \
  int i = 0; \
  while(table.task[i] != NULL) { \
    TRY_CALL(Invoke, table.task[i], logger); \
    i++; \
  } \
} while(0)


#define STRINGIFY(x) #x 
#define TOSTRING(x) STRINGIFY(x)

#define WRITESTR(logger, err_ptr) \
  logger.ref_calls[logger.static_index_msg] = err_ptr; \
  logger.index_calls[logger.static_index_idx] = TOSTRING(__LINE__); \
  logger.static_index_msg++; \
  logger.static_index_idx++;
  
  
//dispatcher class fix
#define CALLERFN(isSafe, indentHandler, logger, j_handler_pointer, i_task_pointer, returnValues) do { \
  const char *err_ptr = NULL; \
  instnaceDispatcher GlobalDispatcher; \

  if ( instanceDispatcher.getSystemDispatcher() && indentHandler.id[j_handler_pointer].holdRecord[i_task_pointer].allSatisfied)  { \
    returnValues = indentHandler[j_handler_pointer].holdTask[i_task_pointer].task; \
  } \ 
  
  else { \
    err_ptr = "TRACKBACK -> File: " __FILE__ " | Line: " TOSTRING(__LINE__); \
    WRITESTR(logger, err_ptr); \
    Serial.println(err_ptr); \
  } \
  
} while(0)

#define TRY_CALL(isSafe, callback, logger) CALLERFN(isSafe, callback, logger)
#define FORMAT_STRUCT() \ 

#define COMPARE_FORMAT(i, handlerStruct, toCompare)  \
  if (i != NULL )  { \
    if (strcmp((indentHandler.holdTask.taskResponses[i].type == toCompare) == 0))   {  \ 
      return false;  \ 
    }  else { \ 
      return true; \
    } \
  }


//implement for IndentHanlder (FIX)
//CodeBlock: holdTask

#define RUN_CODE(indentHandler) do { \
  int i = 0; \
  while(indentHandler.holdTask.task[i] != NULL) { \
     
      COMPARE_FORMAT(i, indentHandler, "void") { \
          TRY_CALL(Invoke, table.task[i], logger); \
      } \
      
      COMPARE_FORMAT(i, indentHandler, "int") {
        if(indentHandler.holdTask[i].arraylike) {
          for (size_t i=0; j > indentHandler.holdTask[j].size; j++ ) { 
            TRY_CALL(Invoke, indentHandler.holdTask[i].task, logger, indentHandler.holdTask[i].taskResponse<int>[j]);
          }
        } else {
            TRY_CALL(Invoke, table.task[i], logger, indentHandler.holdTask[i].taskResponse<int>[j]);
      } }
      
      COMPARE_FORMAT(i, indentHandler, "float") { 
        TRY_CALL(Invoke, indentHandler.holdTask[i].task, logger, indentHandler.holdTask[i].taskResponse<float>[j]);
        
        if(indentHandler.holdTask[i].arraylike) {
          for (size_t i=0; j > indentHandler.holdTask[j].size; j++ ) { 
            TRY_CALL(Invoke, indentHandler.holdTask[i].task, logger indentHandler.holdTask[i].taskResponse<float>[j]);
          }
        }
      }
      
      COMPARE_FORMAT(i, indentHandler, "char") { 
      TRY_CALL(Invoke, indentHandler.holdTask[i].task, logger);
        
        if(indentHandler.holdTask[i].arraylike) {
          for (size_t i=0; j > indentHandler.holdTask[j].size; j++ ) { 
            TRY_CALL(Invoke, indentHandler.holdTask[i].task, logger, indentHandler.holdTask[i].taskResponse<char>[j]);
          }
        }
      }
      
      COMPARE_FORMAT(i, indentHandler, "bool") { 
        TRY_CALL(Invoke, table.task[i], logger); \
      }
    
    i++; \
  } \
} while(0)
*/


