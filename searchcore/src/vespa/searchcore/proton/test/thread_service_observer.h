// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/i_thread_service.h>

namespace proton::test {

class ThreadServiceObserver : public searchcorespi::index::IThreadService
{
private:
    searchcorespi::index::IThreadService &_service;
    uint32_t _executeCnt;

public:
    ThreadServiceObserver(searchcorespi::index::IThreadService &service)
        : _service(service),
          _executeCnt(0)
    {
    }

    uint32_t getExecuteCnt() const { return _executeCnt; }

    /**
     * Implements IThreadService
     */
    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        ++_executeCnt;
        return _service.execute(std::move(task));
    }
    void run(vespalib::Runnable &runnable) override {
        _service.run(runnable);
    }
    vespalib::Syncable &sync() override {
        _service.sync();
        return *this;
    }
    bool isCurrentThread() const override {
        return _service.isCurrentThread();
    }
    size_t getNumThreads() const override { return _service.getNumThreads(); }

};

}
