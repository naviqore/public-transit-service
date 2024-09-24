# JAVA Foreign Method Interface (FMI)

This document describes a simple setup for Java Foreign Method Interface (FMI) using C++ code.

## Overview

### JVM

Set JVM options to load the native library:
`--enable-preview --enable-native-access=ALL-UNNAMED`


### C++ Code

#### library.cpp

```C++
#include <string>
#include <iostream>
#include <cstring>
#include <memory>


  class MyClass {
    std::string message;

  public:
    MyClass()
      : message("Hello from C++")
    {
      std::cout << "Constructor called" << std::endl;
    }
    ~MyClass()
    {
      std::cout << "Destructor called" << std::endl;
    }

    std::string getString()
    {
      return message;
    }

    size_t messageLength()
    {
      return message.length();
    }
  };

  extern "C" {

      __declspec(dllexport) int addNumbers(int a, int b)
      {
        return a + b;
      }

      __declspec(dllexport) void fillString(char* str, size_t length)
      {
        MyClass myClass;
        std::string message = myClass.getString();
        const char* c_message = message.c_str();
        size_t message_length = strlen(c_message);
        if (message_length < length) {
          std::strcpy(str, c_message);
        }
        else {
          std::strncpy(str, c_message, length - 1);
          str[length - 1] = '\0';
        }
      }

      __declspec(dllexport) size_t getMessageLength()
      {
        MyClass myClass;
        return myClass.messageLength();
      }
  }
```

### compile

Using Visual Studio C++ compiler:

- [vcvarsall.bat] Environment initialized for: 'x64'

```bash
cl /EHsc /Fe:library.dll /LD library.cpp
 ```
Using GCC:

```bash
g++ -shared -o library.dll -fPIC library.cpp
```

### Using multiple DLLs

Compile otherLibrary.cpp into a shared library:

```Bash
 cl /EHsc /LD /Fe:otherLibrary.dll otherLibrary.cpp
```
Compile library.cpp and link it with otherLibrary.dll:

```Bash
 cl /EHsc /Fe:library.dll /LD library.cpp otherLibrary.lib
```

Be aware of loading the dependent DLLs in the correct order.

```Java
System.load(absolutePath +File.separator+"otherLibrary.dll");
System.load(absolutePath +File.separator+"library.dll");
  ```