// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multibitvectoriterator.h"
#include "andsearch.h"
#include "andnotsearch.h"
#include "sourceblendersearch.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/util/optimized.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace search::queryeval {

using vespalib::Trinary;
using vespalib::hwaccelrated::IAccelrated;

namespace {

template<typename Update>
class MultiBitVectorIterator : public MultiBitVectorIteratorBase
{
public:
    explicit MultiBitVectorIterator(const Children & children)
        : MultiBitVectorIteratorBase(children),
          _update(),
          _lastWords(),
          _accel(IAccelrated::getAccelrator())
    {
        memset(&_lastWords, 0, sizeof(_lastWords));
    }
protected:
    void updateLastValue(uint32_t docId);
    void strictSeek(uint32_t docId);
private:
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::False; }
    bool acceptExtraFilter() const override { return Update::isAnd(); }
    Update              _update;
    Word                _lastWords[8] __attribute__((aligned(32)));
    const IAccelrated & _accel;
};

template<typename Update>
class MultiBitVectorIteratorStrict : public MultiBitVectorIterator<Update>
{
public:
    explicit MultiBitVectorIteratorStrict(const MultiSearch::Children & children)
        : MultiBitVectorIterator<Update>(children)
    { }
private:
    void doSeek(uint32_t docId) override { this->strictSeek(docId); }
    Trinary is_strict() const override { return Trinary::True; }
};

struct And {
    using Word = BitWord::Word;
    void operator () (const IAccelrated & accel, uint32_t offset, const std::vector<std::pair<const Word *, bool>> & src, Word *dest) {
        accel.and64(offset, src, dest);
    }
    static bool isAnd() { return true; }
};

struct Or {
    using Word = BitWord::Word;
    void operator () (const IAccelrated & accel, uint32_t offset, const std::vector<std::pair<const Word *, bool>> & src, Word *dest) {
        accel.or64(offset, src, dest);
    }
    static bool isAnd() { return false; }
};

template<typename Update>
void MultiBitVectorIterator<Update>::updateLastValue(uint32_t docId)
{
    if (docId >= _lastMaxDocIdLimit) {
        if (__builtin_expect(docId >= _numDocs, false)) {
            setAtEnd();
            return;
        }
        const uint32_t index(wordNum(docId));
        if (docId >= _lastMaxDocIdLimitRequireFetch) {
            uint32_t baseIndex = index & ~(sizeof(_lastWords)/sizeof(Word) - 1);
            _update(_accel, baseIndex, _bvs, _lastWords);
            _lastMaxDocIdLimitRequireFetch = (baseIndex + (sizeof(_lastWords)/sizeof(Word))) * WordLen;
        }
        _lastValue = _lastWords[index % (sizeof(_lastWords)/sizeof(Word))];
        _lastMaxDocIdLimit = (index + 1) * WordLen;
    }
}

template<typename Update>
void
MultiBitVectorIterator<Update>::doSeek(uint32_t docId)
{
    updateLastValue(docId);
    if (__builtin_expect( ! isAtEnd(), true)) {
        if (_lastValue & mask(docId)) {
            setDocId(docId);
        }
    }
}

template<typename Update>
void
MultiBitVectorIterator<Update>::strictSeek(uint32_t docId)
{
    for (updateLastValue(docId), _lastValue = _lastValue & checkTab(docId);
         (_lastValue == 0) && __builtin_expect(! isAtEnd(), true);
         updateLastValue(_lastMaxDocIdLimit));
    if (__builtin_expect(!isAtEnd(), true)) {
        docId = _lastMaxDocIdLimit - WordLen + vespalib::Optimized::lsbIdx(_lastValue);
        if (__builtin_expect(docId >= _numDocs, false)) {
            setAtEnd();
        } else {
            setDocId(docId);
        }
    }
}


typedef MultiBitVectorIterator<And> AndBVIterator;
typedef MultiBitVectorIteratorStrict<And> AndBVIteratorStrict;
typedef MultiBitVectorIterator<Or> OrBVIterator;
typedef MultiBitVectorIteratorStrict<Or> OrBVIteratorStrict;

bool hasAtLeast2Bitvectors(const MultiSearch::Children & children)
{
    size_t count(0);
    for (const SearchIterator * search : children) {
        if (search->isBitVector()) {
            count++;
        }
    }
    return count >= 2;
}

size_t firstStealable(const MultiSearch & s)
{
    return s.isAndNot() ? 1 : 0;
}

bool canOptimize(const MultiSearch & s) {
    return (s.getChildren().size() >= 2)
           && (s.isAnd() || s.isOr() || s.isAndNot())
           && hasAtLeast2Bitvectors(s.getChildren());
}

}

MultiBitVectorIteratorBase::MultiBitVectorIteratorBase(const Children & children) :
    MultiSearch(children),
    _numDocs(std::numeric_limits<unsigned int>::max()),
    _lastMaxDocIdLimit(0),
    _lastMaxDocIdLimitRequireFetch(0),
    _lastValue(0),
    _bvs()
{
    _bvs.reserve(children.size());
    for (size_t i(0); i < children.size(); i++) {
        const auto * bv = static_cast<const BitVectorIterator *>(children[i]);
        _bvs.emplace_back(reinterpret_cast<const Word *>(bv->getBitValues()), bv->isInverted());
        _numDocs = std::min(_numDocs, bv->getDocIdLimit());
    }
}

MultiBitVectorIteratorBase::~MultiBitVectorIteratorBase() = default;

void
MultiBitVectorIteratorBase::initRange(uint32_t beginId, uint32_t endId)
{
    MultiSearch::initRange(beginId, endId);
    _lastMaxDocIdLimit = 0;
    _lastMaxDocIdLimitRequireFetch = 0;
}

SearchIterator::UP
MultiBitVectorIteratorBase::andWith(UP filter, uint32_t estimate)
{
    (void) estimate;
    if (filter->isBitVector() && acceptExtraFilter()) {
        const auto & bv = static_cast<const BitVectorIterator &>(*filter);
        _bvs.emplace_back(reinterpret_cast<const Word *>(bv.getBitValues()), bv.isInverted());
        insert(getChildren().size(), std::move(filter));
        _lastMaxDocIdLimit = 0;  // force reload
        _lastMaxDocIdLimitRequireFetch = 0;
    }
    return filter;
}

void
MultiBitVectorIteratorBase::doUnpack(uint32_t docid)
{
    if (_unpackInfo.unpackAll()) {
        MultiSearch::doUnpack(docid);
    } else {
        auto &children = getChildren();
        _unpackInfo.each([&children,docid](size_t i) {
                             static_cast<BitVectorIterator *>(children[i])->unpack(docid);
                         }, children.size());
    }
}

SearchIterator::UP
MultiBitVectorIteratorBase::optimize(SearchIterator::UP parentIt)
{
    if (parentIt->isSourceBlender()) {
        auto & parent(static_cast<SourceBlenderSearch &>(*parentIt));
        for (size_t i(0); i < parent.getNumChildren(); i++) {
            parent.setChild(i, optimize(parent.steal(i)));
        }
    } else if (parentIt->isMultiSearch()) {
        parentIt = optimizeMultiSearch(std::move(parentIt));
    }
    return parentIt;
}

SearchIterator::UP
MultiBitVectorIteratorBase::optimizeMultiSearch(SearchIterator::UP parentIt)
{
    auto & parent(static_cast<MultiSearch &>(*parentIt));
    if (canOptimize(parent)) {
        MultiSearch::Children stolen;
        std::vector<size_t> _unpackIndex;
        bool strict(false);
        size_t insertPosition(0);
        for (size_t it(firstStealable(parent)); it != parent.getChildren().size(); ) {
            if (parent.getChildren()[it]->isBitVector()) {
                if (stolen.empty()) {
                    insertPosition = it;
                }
                if (parent.needUnpack(it)) {
                    _unpackIndex.push_back(stolen.size());
                }
                SearchIterator::UP bit = parent.remove(it);
                if ( ! strict && (bit->is_strict() == Trinary::True)) {
                    strict = true;
                }
                stolen.push_back(bit.release());
            } else {
                it++;
            }
        }
        SearchIterator::UP next;
        if (parent.isAnd()) {
            if (strict) {
                next = std::make_unique<AndBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<AndBVIterator>(stolen);
            }
        } else if (parent.isOr()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<OrBVIterator>(stolen);
            }
        } else if (parent.isAndNot()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<OrBVIterator>(stolen);
            }
        }
        auto & nextM(static_cast<MultiBitVectorIteratorBase &>(*next));
        for (size_t index : _unpackIndex) {
            nextM.addUnpackIndex(index);
        }
        if (parent.getChildren().empty()) {
            return next;
        } else {
            parent.insert(insertPosition, std::move(next));
        }
    }
    auto & toOptimize(const_cast<MultiSearch::Children &>(parent.getChildren()));
    for (SearchIterator * & search : toOptimize) {
        search = optimize(MultiSearch::UP(search)).release();
    }

    return parentIt;
}

}
