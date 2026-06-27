struct processWindows {
  TaskResponse multipleReponses[10];
}

struct BaseTrait {
  virtual void printSelf() = 0;
}; 

template<typename T> struct TaskResponse : public BaseTrait {
  bool arraylike;
  size_t size; 
  T value;
  const char *type;

  void printSelf() override {
    Serial.println(value);
  }
};

typedef TaskResponse<int> ResponseSignInt;

//////////////////////////////
//////////////////////////////
//////////////////////////////

ResponseSignInt function1(ResponseSignInt arguments){
  if ( arguments.arraylike == true) {}
  for (int i = 0; i > arguments.size; i++) {}
  arguments.printSelf();
  
  if (strcmp(arguments.type, "int") == 0) { 
    Serial.println("everything matches!");
  }

  return arguments;
}

//////////////////////////////
//////////////////////////////
//////////////////////////////



typedef int FNint;
typedef FNint (*ArduinoTaskInt)();

struct IndentHandler { 
 const unsigned int id;
 callStruct holdRecord; 
 codeBlock holdTask;
};

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
  
  
//(n): param count
//isSafe = dispatcher + event (1)
//callback = indentHandler.holdTask.task[i]  (1)
//propagate from RUN_CODE, would be messy (1)
//returnValues = indentHandler.holdTask[i].taskResponse<T>[j] (2)
//logger: (2)  -> hold for constraints if they were satisfied
//indentHnalder[i].holdRecord //pro jaky indentHnalder je hold record
//ADD: returnValues

class globalDispatcher { 
  private: 
    static bool dispatcher;
  
  public: 
    bool getSystemDispatcher() {
      return dispatcher; 
    } 
    
    //doesn't work with contraints, it validates. If contraints are met, but not the dispatcher / was shut down / terminated (don't run) 
    void setSystemDispatcher() {
      dispatcher = false; 
    }
};


class taskManager : public globalDispatcher { 
  public: 
    void terminateProcess() { 
      //find proccess and then terminate,
      // [[WORK HERE!!!!]]
    }
};

//dispatcher class fix
#define CALLERFN(isSafe, indentHandler, logger, j_handler_pointer, i_task_pointer, returnValues) do { \
  const char *err_ptr = NULL; \
  
  if ( globalDispatcher.getSystemDispatcher() && indentHandler[j_handler_pointer].holdRecord[i_task_pointer].allSatisfied)  { \ 
    returnValues = indentHandler[j_handler_pointer].holdTask[i_task_pointer].task \
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
    }
  }

//implement for IndentHanlder (FIX)
//codeBlock: holdTask

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


void terminateById(int id, IndentHandler dataBlock, unsigned int allSystemPid) {
  if (id != NULL ) {
    dataBlock.id = NULL;
    dataBlock.holdRecord = NULL;
    dataBlock.holdTask = NULL;
  }
  Serial.println("Can't terminate ID: ", id);
}









struct IndentHandler { 
 const unsigned int id;
 callStruct holdRecord; 
 codeBlock holdTask;
};

struct callStruct {
  const char* ref_calls[64]; 
  const char* index_calls[64];  
  int static_index_msg = 0; 
  int static_index_idx = 0; 
};

template <typename T, size_t N> struct codeBlock {
  bool invoke;
  T task[N];
  TaskResponse<T> taskResponses[N];
};



class poolDynamics {
  private: 
    struct indentPool {
      const unsigned int endpointNumber;
      unsigned int id;
      IndentHandler *poolHandlers;
    }
  
  public: 
    static int endpointCnt; //have some aware of endpoint count. (system might ask how many endpoints are there)
    static char **endpointStat[endpointCnt + 1] = {"Not connected"}; //write endpoint stuatses by endpoint
    static indentPool pool;
    
    void createPool() {
      pool.id = pool.id++;
      pool.poolHandlers[pool.id] = CreateExeHandle(); 
    }
    
    void writePool(IndentHandler context, unsigned int i_pool_pointer) {
      pool[i_pool_pointer].poolHandlers = NULL;
      pool[i_pool_pointer].poolHandlers = context;
    }
    
    IndentHandler readPool(unsigned int i_pool_pointer) {
      if ( i_pool_pointer > pool.id)  { return IndentHandler(" Error ") }
      return pool[i_pool_pointer].poolHandlers;
    }
     
    void destroyPool(unsigned int poolId) {
      if (readPool(poolId) != NULL ) { 
        pool[poolId].id = NULL;
        pool[poolId].poolHandlers[poolId] = NULL;
      }
    }
     
  void getPoolIds(indentPool pool) {
    int i = 0; 
    
    while (pool.poolHandlers) { 
      Serial.println(pool.poolHandlers[i].id);
      i++;
    }
  }
  
  void poolIdFunctions(indentPool pool) {
    getPoolIds(pool);
  }
  
  void poolIdEndpoints() {
    for ( int i = 0 ; i > endpointCnt; i++ ) {
      while ( endpointStat[i] != NULL ) { 
        Serial.println(endpointStat[i]);
      }
    }
  }
  
  bool validateEndpoint(unsigned int requestedEndpoint) {
    if ( requestedEndpoint == NULL || requestedEndpoint != indentPool.endpointNumber) { return false; }
    else { 
      return true; 
    }
  }
  
  void poolIdDestroyer(unsigned int requestOnDestroy) {
    indentPool.id = NULL;
    indentPool.poolHandlers[requestOnDestroy] = NULL;
  }
  
};

//declare the pool (main)
poolDynamics::pool = {
  
}


template <typename T>
IndentHandler CreateExeHandle(callStruct call, (T (*ptr)()) block, IndentHandler idlist, unsigned int struct_N){
  //this puts them into IndentHandler structure
  
  //MAKE SURE FOR THE LAST TIME IT'S FORMATTED CORRECTLY!
  
  //callStruct void (*ptr)() = ...
  
  //CodeBlock: IndentHandler
  
   
  if (call != NULL && block != NULL && idlist != NULL ) {
    static CallStruct blockLog;
    struct_N = (sizeof(block)  / sizeof(block[0])) + 1;
    
    static codeBlock<struct_N + 1> functionBlock = {
          defaultSystemDispatcher,  
          { block, NULL },
          TaskResponse<int> responseBlock[struct_N],
    };
  
    
    indentPool.indentList[indent_index].id = idGetter().getId();
    indentPool.indentList[indent_index].holdRecord = logging;
    indentPool.indentList[indent_index].holdTask = functionBlock;
    indentPool.taskResponse[indent_index].size = struct_N;
    
  }
  return NULL;  
}










class interfaceProcessTerminal {
public:
  virtual bool getSystemDispatcher() { return false; }
};

struct processTerminal() : public interfaceProcessTerminal{
  static bool systemDispatcher; 
   
  bool getSystemDispatcher() override { return systemDispatcher; }
};

class getterKeeper {
public: 
  virtual unsigned int getId() { return 0; } 
};

struct idKeeper() : public getterKeeper {
  static unsigned int highestId; 
  
  int getId() override { return highestId; }
};

//later in program:
bool processTerminal::systemDispatcher = false;
unsigned int idKeeper::highestId = 0;









//[7]
void terminateById(int id, IndentHandler dataBlock, unsigned int allSystemPid) {
  if (id != NULL ) {
    dataBlock.id = NULL;
    dataBlock.holdRecord = NULL;
    dataBlock.holdTask = NULL;
  }
  Serial.println("Can't terminate ID: ", id);
}


//interface:
FNconstraint ringIfValueX(int x) {
  if (x != 0 || x!=NULL) {
    return true;
  }
  return false;
}

FNconstraint ringIfValueY(int y) {
  if (y != 0 || x!=NULL) {
    return true;
  }
  return false;
}

ResponseSignInt getNumber() {
  Serial.println("10");
  return 10;
}

class ffiInterface { 
  //... something here to call.
};

//setup + loop:
void setup() {
  Serial.begin(9600);
  while (!Serial);
  Serial.println("\tConnected\t");
}

void loop() {
  static IndentHandler c1 = CreateExeHandle({ringIfValueX,ringIfValueY},{getNumber});
  RUN_CODE(c1);
  
  delay(50000);
}
