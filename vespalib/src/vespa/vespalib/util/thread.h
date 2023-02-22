// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "runnable.h"
#include <thread>
#include <concepts>

namespace vespalib {

namespace thread {
[[nodiscard]] std::thread start(Runnable &runnable, Runnable::init_fun_t init_fun);
}

/**
 * Keeps track of multiple running threads. Calling join will join all
 * currently running threads. All threads must be joined before
 * destructing the pool itself. This class is not thread safe.
 **/
class ThreadPool {
private:
    std::vector<std::thread> _threads;
public:
    ThreadPool() noexcept : _threads() {}
    void start(Runnable &runnable, Runnable::init_fun_t init_fun) {
        _threads.reserve(_threads.size() + 1);
        _threads.push_back(thread::start(runnable, std::move(init_fun)));
    }
    template<typename F, typename... Args>
    requires std::invocable<F,Args...>
    void start(F &&f, Args && ... args) {
        _threads.reserve(_threads.size() + 1);
        _threads.emplace_back(std::forward<F>(f), std::forward<Args>(args)...);
    };
    size_t size() const { return _threads.size(); }
    void join() {
        for (auto &thread: _threads) {
            thread.join();
        }
        _threads.clear();
    }
};

}
