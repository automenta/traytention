package spimedb.index;

import com.google.common.base.Joiner;
import jcog.data.sorted.SortedArray;
import jcog.table.SortedListTable;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * priority queue
 */

public class PriBag<V> extends SortedListTable<V, Budget<V>> implements BiFunction<Budget<V>, Budget<V>, Budget<V>> {

    public final BudgetMerge mergeFunction;


    /**
     * inbound pressure sum since last commit
     */
    public volatile float pressure = 0;



    public PriBag(int cap, BudgetMerge mergeFunction, @NotNull Map<V, Budget<V>> map) {
        super(Budget[]::new, map);

        this.mergeFunction = mergeFunction;
        this.capacity = cap;
    }

    /**
     * returns whether the capacity has changed
     */
    //@Override
    public final boolean setCapacity(int newCapacity) {
        if (newCapacity != this.capacity) {
            synchronized (_items()) {
                this.capacity = newCapacity;
                if (this.size() > newCapacity)
                    commit(null);
            }
            return true;
        }
        return false;
    }

    //@Override
    public final boolean isEmpty() {
        return size() == 0;
    }


    /**
     * returns true unless failed to add during 'add' operation
     */
    //@Override
    protected boolean updateItems(@Nullable Budget<V> toAdd) {


        SortedArray<Budget<V>> items = this.items;

        //List<BLink<V>> pendingRemoval;
        List<Budget<V>> pendingRemoval;
        boolean result;
        synchronized (items) {
            int additional = (toAdd != null) ? 1 : 0;
            int c = capacity();

            int s = size();

            int nextSize = s + additional;
            if (nextSize > c) {
                pendingRemoval = new FastList(nextSize - c);
                s = clean(toAdd, s, nextSize - c, pendingRemoval);
                if (s + additional > c) {
                    clean2(pendingRemoval);
                    return false; //throw new RuntimeException("overflow");
                }
            } else {
                pendingRemoval = null;
            }


            if (toAdd != null) {

                //append somewhere in the items; will get sorted to appropriate location during next commit
                //TODO update range

//                Object[] a = items.array();

//                //scan for an empty slot at or after index 's'
//                for (int k = s; k < a.length; k++) {
//                    if ((a[k] == null) /*|| (((BLink)a[k]).isDeleted())*/) {
//                        a[k] = toAdd;
//                        items._setSize(s+1);
//                        return;
//                    }
//                }

                int ss = size();
                if (ss < c) {
                    items.add(toAdd, this);
                    result = true;
                    //items.addInternal(toAdd); //grows the list if necessary
                } else {
                    //throw new RuntimeException("list became full during insert");
                    map.remove(toAdd.id);
                    result = false;
                }

//                float p = toAdd.pri;
//                if (minPri < p && capacity()<=size()) {
//                    this.minPri = p;
//                }


            } else {
                result = size() > 0;
            }

        }

        if (pendingRemoval != null)
            clean2(pendingRemoval);

        return result;

//        if (toAdd != null) {
//            synchronized (items) {
//                //the item key,value should already be in the map before reaching here
//                items.add(toAdd, this);
//            }
//            modified = true;
//        }
//
//        if (modified)
//            updateRange(); //regardless, this also handles case when policy changed and allowed more capacity which should cause minPri to go to -1

    }

    private int clean(@Nullable Budget<V> toAdd, int s, int minRemoved, List<Budget<V>> trash) {

        final int s0 = s;

        if (cleanDeletedEntries()) {
            //first step: remove any nulls and deleted values
            s -= removeDeleted(trash, minRemoved);

            if (s0 - s >= minRemoved)
                return s;
        }

        //second step: if still not enough, do a hardcore removal of the lowest ranked items until quota is met
        s = removeWeakestUntilUnderCapacity(s, trash, toAdd != null);

        return s;
    }


    /**
     * return whether to clean deleted entries prior to removing any lowest ranked items
     */
    protected boolean cleanDeletedEntries() {
        return false;
    }

    private void clean2(List<Budget<V>> trash) {
        int toRemoveSize = trash.size();
        if (toRemoveSize > 0) {

            for (int i = 0; i < toRemoveSize; i++) {
                Budget<V> w = trash.get(i);

                V k = w.id;


                map.remove(k);

//                    if (k2 != w && k2 != null) {
//                        //throw new RuntimeException(
//                        logger.error("bag inconsistency: " + w + " removed but " + k2 + " may still be in the items list");
//                        //reinsert it because it must have been added in the mean-time:
//                        map.putIfAbsent(k, k2);
//                    }

                //pressure -= w.priIfFiniteElseZero(); //release pressure
                onRemoved(w);
                w.delete();

            }

        }


    }

    /** called on eviction */
    protected void onRemoved(Budget<V> w) {

    }

    private int removeWeakestUntilUnderCapacity(int s, @NotNull List<Budget<V>> toRemove, boolean pendingAddition) {
        SortedArray<Budget<V>> items = this.items;
        final int c = capacity;
        while (!isEmpty() && ((s - c) + (pendingAddition ? 1 : 0)) > 0) {
            Budget<V> w = items.remove(s - 1);
            if (w != null) //skip over nulls
                toRemove.add(w);
            s--;
        }
        return s;
    }

    @Nullable
    //@Override
    public V activate(Object key, float toAdd) {
        Budget<V> c = map.get(key);
        if (c != null && !c.isDeleted()) {
            //float dur = c.dur();
            float pBefore = c.pri;
            c.priAdd(toAdd);
            float delta = c.pri - pBefore;
            pressure += delta;// * dur;
            return c.id;
        }
        return null;
    }

    //@Override
    public V mul(Object key, float factor) {
        Budget<V> c = map.get(key);
        if (c != null) {
            float pBefore = c.pri;
            if (pBefore != pBefore)
                return null; //already deleted

            c.priMult(factor);
            float delta = c.pri - pBefore;
            pressure += delta;// * dur;
            return c.id;
        }
        return null;

    }

    //@Override
    public final float rank(Budget x) {
        return -pCmp(x);
    }

    //    //@Override
//    public final int compare(@Nullable BLink o1, @Nullable BLink o2) {
//        float f1 = cmp(o1);
//        float f2 = cmp(o2);
//
//        if (f1 < f2)
//            return 1;           // Neither val is NaN, thisVal is smaller
//        if (f1 > f2)
//            return -1;            // Neither val is NaN, thisVal is larger
//        return 0;
//    }


    /**
     * true iff o1 > o2
     */
    static final boolean cmpGT(@Nullable Budget o1, @Nullable Budget o2) {
        return cmpGT(o1, pCmp(o2));
    }

    static final boolean cmpGT(@Nullable Budget o1, float o2) {
        return (pCmp(o1) < o2);
    }

    /**
     * true iff o1 > o2
     */
    static final boolean cmpGT(float o1, @Nullable Budget o2) {
        return (o1 < pCmp(o2));
    }


    /**
     * true iff o1 < o2
     */
    static final boolean cmpLT(@Nullable Budget o1, @Nullable Budget o2) {
        return cmpLT(o1, pCmp(o2));
    }

    static final boolean cmpLT(@Nullable Budget o1, float o2) {
        return (pCmp(o1) > o2);
    }

    /**
     * gets the scalar float value used in a comparison of BLink's
     * essentially the same as b.priIfFiniteElseNeg1 except it also includes a null test. otherwise they are interchangeable
     */
    static float pCmp(@Nullable Budget b) {
        return (b == null) ? -2f : b.pri; //sort nulls beneath

//        float p = b.pri;
//        return p == p ? p : -1f;
        //return (b!=null) ? b.priIfFiniteElseNeg1() : -1f;
        //return b.priIfFiniteElseNeg1();
    }


    //@Override
    public final V key(@NotNull Budget<V> l) {
        return l.id;
    }


    @Nullable
    @Override
    public Budget<V> put(@NotNull V v, @NotNull Budget<V> b) {
        if (!b.id.equals(v))
            throw new RuntimeException("mismatch");
        return put(v, b.pri, null);
    }

    public final Budget<V> put(@NotNull V key, float pri) {
        return put(key, pri, null);
    }

    //@Override
    public final Budget<V> put(@NotNull V key, float pri, @Nullable MutableFloat overflow) {


        if (pri < 0) { //already deleted
            return null;
        }

        pressure += pri;

        Insertion ii = new Insertion(pri);

        Budget<V> v = map.compute(key, ii);

        Budget<V> w = v;
        int r = ii.result;
        switch (r) {
            case 0:
                Budget vv = v.clone();
                if (vv == null) {
                    //it has been deleted.. TODO reinsert?
                    map.remove(key);
                    pressure -= pri;
                    return null;
                }

                float pBefore = vv.pri;

                //re-rank
                float o = mergeFunction.merge(vv, pri);

                float pAfter = vv.pri;
                int direction;
                float pDelta = pAfter - pBefore;
//                if (pDelta > Param.BUDGET_EPSILON)
//                    direction = +1;
//                else if (pDelta < -Param.BUDGET_EPSILON)
//                    direction = -1;
//                else
//                    direction = 0;
//
//                if (direction != 0) {
//                    synchronized (items) {
//
////                        int p = items.indexOf(v, this);
////                        if (p == -1) {
////                            //removed before this completed
////                            pressure -= bp;
////                            return null;
////                        }
//
////                        int s = items.size();
////                        if (s > 1) {
////                            BLink<V>[] x = items.array();
////
////
////                            boolean rerank;
////
////                            if (direction > 0) {
////                                float pAbove = p == 0 ? Float.POSITIVE_INFINITY : x[p - 1].priIfFiniteElseNeg1();
////                                rerank = (pAbove < pAfter);
////                            } else /*if (direction < 0)*/ {
////                                float pBelow = p == (s - 1) ? Float.NEGATIVE_INFINITY : x[p + 1].priIfFiniteElseNeg1();
////                                rerank = (pBelow > pAfter);
////                            }
////
////                            if (rerank) {
////
////                                if (!items.remove(v, this)) {
////                                    pressure -= bp;
////                                    return null;
////                                }
////
////                                v.setPriority(pAfter); //the remainder of the budget will be set below
//                        if (!update(v)) {
//                            //removed before this completed
//                            if (overflow != null)
//                                overflow.add(bp);
//                            pressure -= bp;
//                            return null;
//                            //throw new RuntimeException("update fault");
//                        }
////                            }
////                        }
//                    }
//                }

                v.pri(vv); //update in-place

                //release some pressure of how much priority existed already
                //pressure-=pBefore;

                if (o > 0) {
                    if (overflow != null)
                        overflow.add(o);
                    pressure -= o;
                }

//                float pAfter = v.pri;
//
//                //technically this should be in a synchronized block but ...
//                if (Util.equals(minPri, pBefore, Param.BUDGET_EPSILON)) {
//                    //in case the merged item determined the min priority
//                    this.minPri = pAfter;
//                }
                break;

            case +1:

                v.pri(pri);

                synchronized (items) {
                    if (updateItems(v)) {
                        //updateRange();
                        w = v;
                    } else {
                        w = null;
                    }
                }

                if (w != null)
                    onAdded(w); //success

                break;

            case -1:
                //reject due to insufficient budget
                if (overflow != null) {
                    overflow.add(pri);
                }
                pressure -= pri;
                w = null;

                break;
        }


        return w;
    }

    protected void onAdded(Budget<V> w) {

    }

//    /**
//     * the applied budget will not become effective until commit()
//     */
//    @NotNull
//    protected final void putExists(@NotNull Budgeted b, float scale, @NotNull BLink<V> existing, @Nullable MutableFloat overflow) {
//
//
//
//    }

//    @NotNull
//    protected final BLink<V> newLink(@NotNull V i, @NotNull Budgeted b) {
//        return newLink(i, b, 1f);
//    }

//    @NotNull
//    protected final BLink<V> newLink(@NotNull V i, @NotNull Budgeted b, float scale) {
//        return newLink(i, scale * b.pri, b.dur(), b.qua());
//    }


    @Nullable
    //@Override
    protected Budget<V> addItem(Budget<V> i) {
        throw new UnsupportedOperationException();
    }


//    @NotNull
//    public final PriBag<V> commit() {
//        return commit(null);
//    }

    @NotNull
    public final PriBag<V> commit(@Nullable Function<PriBag, Consumer<Budget>> update) {

        synchronized (items) {

            update(update != null ? update.apply(this) : null);

        }

        return this;
    }

    public float mass() {
        float mass = 0;
        synchronized (items) {
            int iii = size();
            for (int i = 0; i < iii; i++) {
                Budget x = get(i);
                if (x != null)
                    mass += x.priSafe(0);
            }
        }
        return mass;
    }


    /**
     * applies the 'each' consumer and commit simultaneously, noting the range of items that will need sorted
     */
    @NotNull
    protected PriBag<V> update(@Nullable Consumer<Budget> each) {

        if (each != null)
            this.pressure = 0; //reset pressure accumulator

        synchronized (items) {

            if (size() > 0) {
                if (updateItems(null)) {

                    int lowestUnsorted = updateBudget(each);

                    if (lowestUnsorted != -1) {
                        sort(); //if not perfectly sorted already
                    }
                }
            } else {
                minPri = -1;
            }

        }


        return this;
    }

    public void sort() {
        int s = size();

        qsort(new short[16 /* estimate */], items.array(), (short) 0 /*dirtyStart - 1*/, (short) (s - 1));
        //Arrays.sort(items.array(), 0, s-1);

        this.minPri = (s > 0 && s >= capacity()) ? get(s - 1).priSafe(-1) : -1;
    }


    public float minPri = -1;

//    private final float minPriIfFull() {
//        BLink<V>[] ii = items.last();
//        BLink<V> b = ii[ii.length - 1];
//        if (b!=null) {
//            return b.priIfFiniteElseNeg1();
//        }
//        return -1f;
//
//        //int s = size();
//        //return (s == capacity()) ? itempriMin() : -1f;
//    }

    /**
     * returns the index of the lowest unsorted item
     */
    private int updateBudget(@Nullable Consumer<Budget> each) {
//        int dirtyStart = -1;
        int lowestUnsorted = -1;


        int s = size();
        Budget<V>[] l = items.array();
        int i = s - 1;
        //@NotNull BLink<V> beneath = l[i]; //compares with self below to avoid a null check in subsequent iterations
        float beneath = Float.POSITIVE_INFINITY;
        for (; i >= 0; ) {
            Budget<V> b = l[i];

            float bCmp;
            bCmp = b != null ? b.priSafe(-1) : -2; //sort nulls to the end of the end

            if (bCmp > 0) {
                if (each != null)
                    each.accept(b);
            }


            if (lowestUnsorted == -1 && bCmp < beneath) {
                lowestUnsorted = i + 1;
            }

            beneath = bCmp;
            i--;
        }


        return lowestUnsorted;
    }


    private int removeDeleted(@NotNull List<Budget<V>> removed, int minRemoved) {

        SortedArray<Budget<V>> items = this.items;
        final Object[] l = items.array();
        int removedFromMap = 0;

        //iterate in reverse since null entries should be more likely to gather at the end
        for (int s = size() - 1; removedFromMap < minRemoved && s >= 0; s--) {
            Budget<V> x = (Budget<V>) l[s];
            if (x == null || x.isDeleted()) {
                items.removeFast(s);
                if (x != null)
                    removed.add(x);
                removedFromMap++;
            }
        }

        return removedFromMap;
    }

    //@Override
    public void clear() {
        synchronized (items) {
            //map is possibly shared with another bag. only remove the items from it which are present in items
            items.forEach(x -> map.remove(x.id));
            items.clear();
            minPri = -1;
        }
    }


    @Nullable
    @Override
    public Budget apply(@Nullable Budget bExisting, Budget bNext) {
        if (bExisting != null) {
            mergeFunction.merge(bExisting, bNext.pri);
            return bExisting;
        } else {
            return bNext;
        }
    }

    //@Override
    public void forEach(Consumer<? super Budget<V>> action) {
        Object[] x = items.array();
        if (x.length > 0) {
            for (Budget a : ((Budget[]) x)) {
                if (a != null) {
                    Budget<V> b = a;
                    if (!b.isDeleted())
                        action.accept(b);
                }
            }
        }
    }


    /**
     * http://kosbie.net/cmu/summer-08/15-100/handouts/IterativeQuickSort.java
     */

    public static void qsort(short[] stack, Budget[] c, short left, short right) {
        int stack_pointer = -1;
        int cLenMin1 = c.length - 1;
        while (true) {
            short i, j;
            if (right - left <= 7) {
                Budget swap;
                //bubble sort on a region of right less than 8?
                for (j = (short) (left + 1); j <= right; j++) {
                    swap = c[j];
                    i = (short) (j - 1);
                    float swapV = pCmp(swap);
                    while (i >= left && cmpGT(c[i], swapV)) {
                        swap(c, (short) (i + 1), i);
                        i--;
                    }
                    c[i + 1] = swap;
                }
                if (stack_pointer != -1) {
                    right = stack[stack_pointer--];
                    left = stack[stack_pointer--];
                } else {
                    break;
                }
            } else {
                Budget swap;

                short median = (short) ((left + right) / 2);
                i = (short) (left + 1);
                j = right;

                swap(c, i, median);

                if (cmpGT(c[left], c[right])) {
                    swap(c, right, left);
                }
                if (cmpGT(c[i], c[right])) {
                    swap(c, right, i);
                }
                if (cmpGT(c[left], c[i])) {
                    swap(c, i, left);
                }

                {
                    Budget temp = c[i];
                    float tempV = pCmp(temp);

                    while (true) {
                        while (i < cLenMin1 && cmpLT(c[++i], tempV)) ;
                        while (cmpGT(c[--j], tempV)) ;
                        if (j < i) {
                            break;
                        }
                        swap(c, j, i);
                    }

                    c[left + 1] = c[j];
                    c[j] = temp;
                }

                short a, b;
                if ((right - i + 1) >= (j - left)) {
                    a = i;
                    b = right;
                    right = (short) (j - 1);
                } else {
                    a = left;
                    b = (short) (j - 1);
                    left = i;
                }

                stack[++stack_pointer] = a;
                stack[++stack_pointer] = b;
            }
        }
    }

    public static void swap(Budget[] c, short x, short y) {
        Budget swap;
        swap = c[y];
        c[y] = c[x];
        c[x] = swap;
    }

    //    final Comparator<? super BLink<V>> comparator = (a, b) -> {
//        return Float.compare(items.score(b), items.score(a));
//    };


//        if (!v.hasDelta()) {
//            return;
//        }
//
////
////        int size = ii.size();
////        if (size == 1) {
////            //its the only item
////            v.commit();
////            return;
////        }
//
//        SortedIndex ii = this.items;
//
//        int currentIndex = ii.locate(v);
//
//        v.commit(); //after finding where it was, apply its updates to find where it will be next
//
//        if (currentIndex == -1) {
//            //an update for an item which has been removed already. must be re-inserted
//            put(v.id, v);
//        } else if (ii.scoreBetween(currentIndex, ii.size(), v)) { //has position changed?
//            ii.reinsert(currentIndex, v);
//        }
//        /*} else {
//            //otherwise, it remains in the same position and a move is unnecessary
//        }*/
//    }


    @NotNull
    //@Override
    public String toString() {
        return Joiner.on(", ").join(items);// + '{' + items.getClass().getSimpleName() + '}';
    }


    //@Override
    public float priMax() {
        Budget<V> x = items.first();
        return x != null ? x.pri : 0f;
    }

    //@Override
    public float priMin() {
        Budget<V> x = items.last();
        return x != null ? x.pri : 0f;
    }


//    public final void popAll(@NotNull Consumer<BLink<V>> receiver) {
//        forEach(receiver);
//        clear();
//    }

//    public void pop(@NotNull Consumer<BLink<V>> receiver, int n) {
//        if (n == size()) {
//            //special case where size <= inputPerCycle, the entire bag can be flushed in one operation
//            popAll(receiver);
//        } else {
//            for (int i = 0; i < n; i++) {
//                receiver.accept(pop());
//            }
//        }
//    }

//    public final float priAt(int cap) {
//        return size() <= cap ? 1f : item(cap).pri;
//    }
//

//    public final static class BudgetedArraySortedIndex<X extends Budgeted> extends ArraySortedIndex<X> {
//        public BudgetedArraySortedIndex(int capacity) {
//            super(1, capacity);
//        }
//
//
//        //@Override
//        public float score(@NotNull X v) {
//            return v.pri;
//        }
//    }

    /**
     * Created by me on 8/15/16.
     */
    final class Insertion<V> implements BiFunction<V, Budget, Budget> {


        private final float pri;

        /**
         * TODO this field can be re-used for 'activated' return value
         * -1 = deactivated, +1 = activated, 0 = no change
         */
        int result = 0;

        public Insertion(float pri) {
            this.pri = pri;
        }


        @Nullable
        //@Override
        public Budget apply(@NotNull Object key, @Nullable Budget existing) {


            if (existing != null) {
                //result=0
                return existing;
            } else {
                if (pri < minPri /* accept if pri == minPri  */) {
                    this.result = -1;
                    return null;
                } else {
                    //accepted for insert
                    this.result = +1;
                    return new Budget(key);
                }
            }
        }
    }
}


//        if (dirtyStart != -1) {
//            //Needs sorted
//
//            int dirtyRange = 1 + dirtyEnd - dirtyStart;
//
//            if (dirtyRange == 1) {
//                //Special case: only one unordered item; remove and reinsert
//                BLink<V> x = items.remove(dirtyStart); //remove directly from the decorated list
//                items.add(x); //add using the sorted list
//
//            } else if ( dirtyRange < Math.max(1, reinsertionThreshold * s) ) {
//                //Special case: a limited number of unordered items
//                BLink<V>[] tmp = new BLink[dirtyRange];
//
//                for (int k = 0; k < dirtyRange; k++) {
//                    tmp[k] = items.remove( dirtyStart /* removal position remains at the same index as items get removed */);
//                }
//
//                //TODO items.get(i) and
//                //   ((FasterList) items.list).removeRange(dirtyStart+1, dirtyEnd);
//
//                for (BLink i : tmp) {
//                    if (i.isDeleted()) {
//                        removeKeyForValue(i);
//                    } else {
//                        items.add(i);
//                    }
//                }
//
//            } else {
//            }
//        }
