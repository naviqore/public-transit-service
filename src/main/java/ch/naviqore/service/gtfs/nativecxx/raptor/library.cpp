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