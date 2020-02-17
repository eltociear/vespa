// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "context.h"

namespace storage::spi {

Context::Context(const LoadType& loadType, Priority pri, int maxTraceLevel)
    : _loadType(&loadType),
      _priority(pri),
      _trace(maxTraceLevel),
      _readConsistency(ReadConsistency::STRONG)
{ }

Context::~Context() = default;

}
