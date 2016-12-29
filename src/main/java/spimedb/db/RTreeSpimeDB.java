package spimedb.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.bytebuddy.ByteBuddy;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.api.tuple.Twin;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.tuple.Tuples;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimedb.NObject;
import spimedb.SpimeDB;
import spimedb.index.oct.OctBox;
import spimedb.index.rtree.LockingRTree;
import spimedb.index.rtree.RTree;
import spimedb.index.rtree.RectND;
import spimedb.index.rtree.SpatialSearch;
import spimedb.util.geom.Vec3D;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import static spimedb.index.rtree.SpatialSearch.DEFAULT_SPLIT_TYPE;


public class RTreeSpimeDB implements SpimeDB {

    final static Logger logger = LoggerFactory.getLogger(RTreeSpimeDB.class);

    public final MutableGraph<String> tag = GraphBuilder.directed().allowsSelfLoops(false).expectedNodeCount(512).nodeOrder(ElementOrder.unordered()).build();

    @JsonIgnore public final Map<String,SpatialSearch<NObject>> spacetime = new ConcurrentHashMap<>();

    public final Map<String, NObject> obj;

    /** in-memory, map-based */
    public RTreeSpimeDB() {
        this(new ConcurrentHashMap());
    }

    public RTreeSpimeDB(Map<String, NObject> g) {

        this.obj = g;

        /*this.oct = new MyOctBox(
                new Vec3D(-180f, -90f, -1),
                new Vec3D(360f, 180f, 2),
                new Vec3D(0.05f, 0.05f, 0.05f));*/

    }

    public SpatialSearch<NObject> space(String tag) {
        return spacetime.computeIfAbsent(tag, (t) -> {
            return new LockingRTree<NObject>(new RTree<NObject>(new RectND.Builder(),
                    2, 8, DEFAULT_SPLIT_TYPE),
                    new ReentrantReadWriteLock());
        });
    }

    protected final Map<String,Class> tagClasses = new ConcurrentHashMap<>();
    protected final ClassLoader cl = ClassLoader.getSystemClassLoader();
    final ByteBuddy tagProxyBuilder = new ByteBuddy();

    Class[] resolve(String... tags) {
        Class[] c = new Class[tags.length];
        int i = 0;
        for (String s : tags) {
            Class x = tagClasses.get(s);
            if (x == null)
                throw new NullPointerException("missing class: " + s);
            c[i++] = x;
        }
        return c;
    }

    @Override public Class the(String tagID, String... supertags) {

        synchronized (tagClasses) {
            if (tagClasses.containsKey(tagID))
                throw new RuntimeException(tagID + " class already defined");

            Class[] s = resolve(supertags);
            Class proxy = tagProxyBuilder.makeInterface(s).name("_" + tagID).make().load(cl).getLoaded();
            tagClasses.put(tagID, proxy);
            return proxy;
        }

        //.subclass(NObject.class).implement(s)
                    /*.method(any())
                    .intercept(MethodDelegation.to(MyInterceptor.class)
                            .andThen(SuperMethodCall.INSTANCE)
                            .defineField("myCustomField", Object.class, Visibility.PUBLIC)*/
                    /*.make()
                    .load(cl)
                    .getLoaded();*/
    }

//    @Override
//    public NObject a(String id, String... tags) {
//        return null;
//    }

    @JsonProperty("status") /*@JsonSerialize(as = RawSerializer.class)*/ @Override
    public String toString() {
        return "{\"" + getClass().getSimpleName() + "\":{\"size\":" + size() +
                ",\"spacetime\":\"" + spacetime + "\"}}";
    }

    @Override
    public void close() {

    }

    @Override
    public NObject put(NObject d) {
        //final String id = d.getId();

        //TODO use 'obj.merge' for correct un-indexing of prevoius value
        String id = d.getId();

        NObject previous = obj.put(id, d);

        /*if (tag.nodes().contains(id)) {
            //TODO re-tag
        }*/

        String[] tags = d.tag;
        if (tags!=null) {
            for (String t : tags) {
                if (this.tag.addNode(t)) {
                    //index the tag if it doesnt exist in the graph
                    NObject tagJect = get(t);
                    if (tagJect!=null) {
                        String[] parents = tagJect.tag;
                        if (parents != null)
                            tag(t, parents);
                    }
                }
            }
            tag(id, tags);

            if (d.bounded()) {
                for (String t : tags)
                    space(t).add(d);
            }
        }

        //Object tags = obj.get(">");

        return null;
    }

    private void tag(String id, String[] parents) {
        for (String parentTag : parents) {
            tag.putEdge(parentTag, id);
        }
    }


    public static <E> Pair<E, Twin<String>> edge(E e, String from, String to) {
        return Tuples.pair(e, Tuples.twin(from, to));
    }


    @Override
    public Iterator<NObject> iterator() {
        return obj.values().iterator();
    }

    @JsonIgnore @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @JsonIgnore @Override
    public int size() {
        return obj.size();
    }

    @Override
    public NObject get(String nobjectID) {
        return obj.get(nobjectID);
    }

    @Override
    public List<NObject> intersecting(double lon, double lat, double radMeters, int maxResults) {

        List<NObject> l = new FastList() {

            int count = 0;

            @Override
            public boolean add(Object newItem) {

                if (super.add(newItem)) {
                    return (++count != maxResults);
                }

                return false;
            }
        };

        intersecting((float) lon, (float) lat, (float) radMeters, l::add);
        return l;
    }

    @Override @NotNull
    public void intersecting(float lon, float lat, float radMeters, Predicate<NObject> l, String... tags) {
        float radDegrees = metersToDegrees(radMeters);

        for (String t : tags) {
            if (!space(t).intersecting(new RectND(
                    new float[]{Float.NEGATIVE_INFINITY, lon - radDegrees, lat - radDegrees, Float.NEGATIVE_INFINITY},
                    new float[]{Float.POSITIVE_INFINITY, lon + radDegrees, lat + radDegrees, Float.POSITIVE_INFINITY}
            ), l))
                return;
        }
    }

    @Override @NotNull
    public void intersecting(float[] lon, float[] lat, Predicate<NObject> l, String[] tags) {

        //System.out.println(lon[0] + "," + lat[0] + " .. " + lon[1] + "," + lat[1] );
        for (String t : tags) {
            if (!space(t).intersecting(new RectND(
                    new float[]{Float.NEGATIVE_INFINITY, lon[0], lat[0], Float.NEGATIVE_INFINITY},
                    new float[]{Float.POSITIVE_INFINITY, lon[1], lat[1], Float.POSITIVE_INFINITY}
            ), l))
                return;
        }
    }


    private static float metersToDegrees(float radMeters) {
        return radMeters / 110648f;
    }


    static class MyOctBox extends OctBox {

        public MyOctBox(Vec3D origin, Vec3D extents, Vec3D resolution) {
            super(origin, extents, resolution);
        }

        @NotNull
        @Override
        protected OctBox newBox(OctBox parent, Vec3D off, Vec3D extent) {
            return new MyOctBox(parent, off, extent);
        }

        @Override protected void onModified() {
            System.out.println(this + " modified");
        }

    }
}
