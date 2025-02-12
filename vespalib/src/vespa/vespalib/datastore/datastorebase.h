// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bufferstate.h"
#include "free_list.h"
#include "memory_stats.h"
#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/util/generationholder.h>
#include <vespa/vespalib/util/generation_hold_list.h>
#include <vespa/vespalib/util/stash.h>
#include <atomic>
#include <vector>

namespace vespalib::datastore {

class CompactingBuffers;
class CompactionSpec;
class CompactionStrategy;

/**
 * Abstract class used to store data of potential different types in underlying memory buffers.
 *
 * Reference to stored data is via a 32-bit handle (EntryRef).
 */
class DataStoreBase
{
public:
    using generation_t = vespalib::GenerationHandler::generation_t;

    DataStoreBase(const DataStoreBase &) = delete;
    DataStoreBase &operator=(const DataStoreBase &) = delete;

    uint32_t addType(BufferTypeBase *typeHandler);
    void init_primary_buffers();

    /**
     * Ensure that the primary buffer for the given type has a given number of elements free at end.
     * Switch to new buffer if current buffer is too full.
     *
     * @param typeId      Registered data type for buffer.
     * @param elemsNeeded Number of elements needed to be free.
     */
    void ensureBufferCapacity(uint32_t typeId, size_t elemsNeeded) {
        if (elemsNeeded > getBufferState(primary_buffer_id(typeId)).remaining()) [[unlikely]] {
            switch_or_grow_primary_buffer(typeId, elemsNeeded);
        }
    }

    /**
     * Put buffer on hold list, as part of compaction.
     *
     * @param bufferId      Id of buffer to be held.
     */
    void holdBuffer(uint32_t bufferId);

    /**
     * Switch to a new primary buffer, typically in preparation for compaction
     * or when the current primary buffer no longer has free space.
     *
     * @param typeId      Registered data type for buffer.
     * @param elemsNeeded Number of elements needed to be free.
     */
    void switch_primary_buffer(uint32_t typeId, size_t elemsNeeded);

    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage getDynamicMemoryUsage() const;

    vespalib::AddressSpace getAddressSpaceUsage() const;

    /**
     * Get the primary buffer id for the given type id.
     */
    uint32_t primary_buffer_id(uint32_t typeId) const { return _primary_buffer_ids[typeId]; }
    BufferState &getBufferState(uint32_t buffer_id) noexcept;
    const BufferAndMeta & getBufferMeta(uint32_t buffer_id) const { return _buffers[buffer_id]; }
    uint32_t getMaxNumBuffers() const noexcept { return _buffers.size(); }
    uint32_t get_bufferid_limit_acquire() const noexcept { return _bufferIdLimit.load(std::memory_order_acquire); }
    uint32_t get_bufferid_limit_relaxed() noexcept { return _bufferIdLimit.load(std::memory_order_relaxed); }

    template<typename FuncType>
    void for_each_active_buffer(FuncType func) {
        uint32_t buffer_id_limit = get_bufferid_limit_relaxed();
        for (uint32_t i = 0; i < buffer_id_limit; i++) {
            const BufferState * state = _buffers[i].get_state_relaxed();
            if (state && state->isActive()) {
                func(i, *state);
            }
        }
    }

    /**
     * Assign generation on data elements on hold lists added since the last time this function was called.
     */
    void assign_generation(generation_t current_gen);

    /**
     * Reclaim memory from hold lists, freeing buffers and entry refs that no longer needs to be held.
     *
     * @param oldest_used_gen oldest generation that is still used.
     */
    void reclaim_memory(generation_t oldest_used_gen);

    void reclaim_all_memory();

    template <typename EntryType, typename RefType>
    EntryType *getEntry(RefType ref) {
        return static_cast<EntryType *>(_buffers[ref.bufferId()].get_buffer_relaxed()) + ref.offset();
    }

    template <typename EntryType, typename RefType>
    const EntryType *getEntry(RefType ref) const {
        return static_cast<const EntryType *>(_buffers[ref.bufferId()].get_buffer_acquire()) + ref.offset();
    }

    template <typename EntryType, typename RefType>
    EntryType *getEntryArray(RefType ref, size_t arraySize) {
        return static_cast<EntryType *>(_buffers[ref.bufferId()].get_buffer_relaxed()) + (ref.offset() * arraySize);
    }

    template <typename EntryType, typename RefType>
    const EntryType *getEntryArray(RefType ref, size_t arraySize) const {
        return static_cast<const EntryType *>(_buffers[ref.bufferId()].get_buffer_acquire()) + (ref.offset() * arraySize);
    }

    void dropBuffers();

    /**
     * Enable free list management.
     * This only works for fixed size elements.
     */
    void enableFreeLists();

    /**
     * Disable free list management.
     */
    void disableFreeLists();
    void disableElemHoldList();

    bool has_free_lists_enabled() const { return _freeListsEnabled; }

    /**
     * Returns the free list for the given type id.
     */
    FreeList &getFreeList(uint32_t typeId) {
        return _free_lists[typeId];
    }

    /**
     * Returns aggregated memory statistics for all buffers in this data store.
     */
    MemoryStats getMemStats() const;

    /**
     * Assume that no readers are present while data structure is being initialized.
     */
    void setInitializing(bool initializing) { _initializing = initializing; }

    uint32_t getTypeId(uint32_t bufferId) const {
        return _buffers[bufferId].getTypeId();
    }

    void finishCompact(const std::vector<uint32_t> &toHold);

    vespalib::GenerationHolder &getGenerationHolder() {
        return _genHolder;
    }

    // need object location before construction
    static vespalib::GenerationHolder &getGenerationHolderLocation(DataStoreBase &self) {
        return self._genHolder;
    }

    std::unique_ptr<CompactingBuffers> start_compact_worst_buffers(CompactionSpec compaction_spec, const CompactionStrategy &compaction_strategy);
    uint64_t get_compaction_count() const { return _compaction_count.load(std::memory_order_relaxed); }
    void inc_compaction_count() const { ++_compaction_count; }
    bool has_held_buffers() const noexcept { return _hold_buffer_count != 0u; }

    /**
     * Trim elem hold list, freeing elements that no longer needs to be held.
     *
     * @param oldest_used_gen the oldest generation that is still used.
     */
    virtual void reclaim_entry_refs(generation_t oldest_used_gen) = 0;

protected:
    DataStoreBase(uint32_t numBuffers, uint32_t offset_bits, size_t maxArrays);
    virtual ~DataStoreBase();

    void* getBuffer(uint32_t bufferId) { return _buffers[bufferId].get_buffer_relaxed(); }

    struct EntryRefHoldElem {
        EntryRef ref;
        size_t   num_elems;

        EntryRefHoldElem(EntryRef ref_in, size_t num_elems_in)
            : ref(ref_in),
              num_elems(num_elems_in)
        {}
    };

    using EntryRefHoldList = GenerationHoldList<EntryRefHoldElem, false, true>;

    EntryRefHoldList              _entry_ref_hold_list;
public:
    // Static size of dequeue in _entry_ref_hold_list._phase_2_list
    // might depend on std::deque implementation
    static constexpr size_t sizeof_entry_ref_hold_list_deque = EntryRefHoldList::sizeof_phase_2_list;
private:

    /**
     * Class used to hold the entire old buffer as part of fallbackResize().
     */
    class FallbackHold : public vespalib::GenerationHeldBase
    {
    public:
        BufferState::Alloc _buffer;
        size_t             _usedElems;
        BufferTypeBase    *_typeHandler;
        uint32_t           _typeId;

        FallbackHold(size_t bytesSize, BufferState::Alloc &&buffer, size_t usedElems,
                     BufferTypeBase *typeHandler, uint32_t typeId);

        ~FallbackHold() override;
    };

    class BufferHold;

    bool consider_grow_active_buffer(uint32_t type_id, size_t elems_needed);
    void switch_or_grow_primary_buffer(uint32_t typeId, size_t elemsNeeded);
    void markCompacting(uint32_t bufferId);
    /**
     * Hold of buffer has ended.
     */
    void doneHoldBuffer(uint32_t bufferId);

    /**
     * Switch buffer state to active for the given buffer.
     *
     * @param bufferId    Id of buffer to be active.
     * @param typeId      Registered data type for buffer.
     * @param elemsNeeded Number of elements needed to be free.
     */
    void onActive(uint32_t bufferId, uint32_t typeId, size_t elemsNeeded);

    void inc_hold_buffer_count();
    void fallbackResize(uint32_t bufferId, size_t elementsNeeded);
    uint32_t getFirstFreeBufferId();

    template<typename FuncType>
    void for_each_buffer(FuncType func) {
        uint32_t buffer_id_limit = get_bufferid_limit_relaxed();
        for (uint32_t i = 0; i < buffer_id_limit; i++) {
            func(*(_buffers[i].get_state_relaxed()));
        }
    }

    virtual void reclaim_all_entry_refs() = 0;

    std::vector<BufferAndMeta>    _buffers; // For fast mapping with known types

    // Provides a mapping from typeId -> primary buffer for that type.
    // The primary buffer is used for allocations of new element(s) if no available slots are found in free lists.
    std::vector<uint32_t>         _primary_buffer_ids;

    Stash                         _stash;
    std::vector<BufferTypeBase *> _typeHandlers; // TypeId -> handler
    std::vector<FreeList>         _free_lists;
    mutable std::atomic<uint64_t> _compaction_count;
    vespalib::GenerationHolder    _genHolder;
    const uint32_t                _maxArrays;
    std::atomic<uint32_t>         _bufferIdLimit;
    uint32_t                      _hold_buffer_count;
    const uint8_t                 _offset_bits;
    bool                          _freeListsEnabled;
    bool                          _disableElemHoldList;
    bool                          _initializing;
};

}

namespace vespalib {
extern template class GenerationHoldList<datastore::DataStoreBase::EntryRefHoldElem, false, true>;
}
