package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A min priority queue of distinct elements of type `KeyType` associated with (extrinsic) double
 * priorities, implemented using a binary heap paired with a hash table.
 */
public class MinPQueue<KeyType> {

    /**
     * Pairs an element `key` with its associated priority `priority`.
     */
    private record Entry<KeyType>(KeyType key, double priority) {
        // Note: This is equivalent to declaring a static nested class with fields `key` and
        //  `priority` and a corresponding constructor and observers, overriding `equals()` and
        //  `hashCode()` to depend on the fields, and overriding `toString()` to print their values.
        // https://docs.oracle.com/en/java/javase/17/language/records.html
    }

    /**
     * ArrayList representing a binary min-heap of element-priority pairs.  Satisfies
     * `heap.get(i).priority() >= heap.get((i-1)/2).priority()` for all `i` in `[1..heap.size())`.
     */
    private final ArrayList<Entry<KeyType>> heap;

    /**
     * Associates each element in the queue with its index in `heap`.  Satisfies
     * `heap.get(index.get(e)).key().equals(e)` if `e` is an element in the queue. Only maps
     * elements that are in the queue (`index.size() == heap.size()`).
     */
    private final Map<KeyType, Integer> index;

    // TODO 7: Write an assertInv() method that asserts that all of the class invariants are satisfied.
    /**
     * Asserts that all class invariants are satisfied.
     * @throws AssertionError if any invariant is violated
     */
    private void assertInv() {
        // Check that index size matches heap size
        assert heap.size() == index.size();

        // Check heap and index consistency
        for (int i = 0; i < heap.size(); i++) {
            Entry<KeyType> entry = heap.get(i);
            Integer idx = index.get(entry.key());

            // Check that index contains all heap keys
            assert idx != null;

            // Check that index points back to correct heap position
            assert idx == i;
        }

        // Check heap property (parent <= children)
        for (int i = 1; i < heap.size(); i++) {
            int parent = (i - 1) / 2;
            assert heap.get(parent).priority() <= heap.get(i).priority();
        }
    }

    /**
     * Create an empty queue.
     */
    public MinPQueue() {
        index = new HashMap<>();
        heap = new ArrayList<>();
    }

    /**
     * Return whether this queue contains no elements.
     */
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /**
     * Return the number of elements contained in this queue.
     */
    public int size() {
        return heap.size();
    }

    /**
     * Return an element associated with the smallest priority in this queue.  This is the same
     * element that would be removed by a call to `remove()` (assuming no mutations in between).
     * Throws NoSuchElementException if this queue is empty.
     */
    public KeyType peek() {
        // Propagate exception from `List::getFirst()` if empty.
        return heap.getFirst().key();
    }

    /**
     * Return the minimum priority associated with an element in this queue.  Throws
     * NoSuchElementException if this queue is empty.
     */
    public double minPriority() {
        return heap.getFirst().priority();
    }

    /**
     * Swap the Entries at indices `i` and `j` in `heap`, updating `index` accordingly.  Requires
     * {@code 0 <= i,j < heap.size()}.
     */
    private void swap(int i, int j) {
        // TODO 8a: Implement this method according to its specification
        // Get the entries at positions i and j
        Entry<KeyType> entryI = heap.get(i);
        Entry<KeyType> entryJ = heap.get(j);

        // Swap entries in the heap
        heap.set(i, entryJ);
        heap.set(j, entryI);

        // Update their positions in the index
        index.put(entryI.key(), j);
        index.put(entryJ.key(), i);
    }

    // TODO 8b: Implement private helper methods for bubbling entries up and down in the heap.
    //  Their interfaces are up to you, but you must write precise specifications.

    /**
     * Bubbles up the entry at index `i` to restore the heap property.
     * Requires {@code 0 <= i < heap.size()}.
     */
    private void bubbleUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (heap.get(parent).priority() <= heap.get(i).priority()) {
                break; // Heap property is satisfied
            }
            swap(i, parent);
            i = parent;
        }
    }

    /**
     * Bubbles down the entry at index `i` to restore the heap property.
     * Requires {@code 0 <= i < heap.size()}.
     */
    private void bubbleDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            int smallest = i;

            // Find the smallest among current node and its children
            if (left < heap.size() && heap.get(left).priority() < heap.get(smallest).priority()) {
                smallest = left;
            }
            if (right < heap.size() && heap.get(right).priority() < heap.get(smallest).priority()) {
                smallest = right;
            }

            // If current node is smallest, heap property is satisfied
            if (smallest == i) {
                break;
            }

            // Otherwise, swap with smallest child and continue
            swap(i, smallest);
            i = smallest;
        }
    }

    /**
     * Add element `key` to this queue, associated with priority `priority`.  Requires `key` is not
     * contained in this queue.
     */
    private void add(KeyType key, double priority) {
        // TODO 9a: Implement this method according to its specification
        assert !index.containsKey(key);

        // Create new entry and add to end of heap
        Entry<KeyType> entry = new Entry<>(key, priority);
        heap.add(entry);
        index.put(key, heap.size() - 1);

        // Restore heap property by bubbling up
        bubbleUp(heap.size() - 1);

        assertInv();
    }

    /**
     * Change the priority associated with element `key` to `priority`.  Requires that `key` is
     * contained in this queue.
     */
    private void update(KeyType key, double priority) {
        assert index.containsKey(key);

        // TODO 9b: Implement this method according to its specification
        int i = index.get(key);
        double oldPriority = heap.get(i).priority();
        heap.set(i, new Entry<>(key, priority));

        // Restore heap property based on priority change direction
        if (priority < oldPriority) {
            bubbleUp(i);  // Priority decreased - move up
        } else if (priority > oldPriority) {
            bubbleDown(i); // Priority increased - move down
        }
        // If priority didn't change, no action needed

        assertInv();
    }

    /**
     * If `key` is already contained in this queue, change its associated priority to `priority`.
     * Otherwise, add it to this queue with that priority.
     */
    public void addOrUpdate(KeyType key, double priority) {
        if (!index.containsKey(key)) {
            add(key, priority);
        } else {
            update(key, priority);
        }
    }

    /**
     * Remove and return the element associated with the smallest priority in this queue.  If
     * multiple elements are tied for the smallest priority, an arbitrary one will be removed.
     * Throws NoSuchElementException if this queue is empty.
     */
    public KeyType remove() {
        // TODO 9c: Implement this method according to its specification
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Queue is empty");
        }

        // Save the minimum element (root of heap)
        KeyType minKey = heap.getFirst().key();

        if (heap.size() == 1) {
            // Simple case - just remove the only element
            heap.removeFirst();
            index.remove(minKey);
        } else {
            // Move last element to root and remove last
            Entry<KeyType> last = heap.removeLast();
            heap.set(0, last);
            index.put(last.key(), 0);
            index.remove(minKey);

            // Restore heap property by bubbling down
            bubbleDown(0);
        }

        assertInv();
        return minKey;
    }

}
