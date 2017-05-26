package spimedb.media.geojson;

import jcog.tree.rtree.rect.RectDoubleND;
import org.junit.Before;
import org.junit.Test;
import spimedb.NObject;
import spimedb.SpimeDB;
import spimedb.media.GeoJSON;
import spimedb.query.Query;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by me on 12/29/16.
 */
public class GeoJSONTest {

    private SpimeDB db;

    @Before public void setup() throws IOException {
        db = new SpimeDB();
        db.add(GeoJSON.get(
                new BufferedInputStream(GeoJSONTest.class.getClassLoader().getResourceAsStream("geojson/eq.geojson"), 1024),
                GeoJSON.baseGeoJSONBuilder));
        db.sync(50);
    }

    @Test
    public void test1() throws IOException {




        int all = db.size();
        assertTrue(all > 50);

        //time query
        ArrayList<Object> r1 = new ArrayList<>();
        db.find(
                new Query()
                        .when(1.48252053E12f, 1.48250336E12f)
        ).forEachObject(r1::add);

        int aNum = r1.size();
        assertTrue(aNum > 0);
        assertTrue(aNum < all/4);


        System.out.println(r1);
        System.out.println(aNum + " / " + all + " found:");
//        System.out.println("\t" + a.result);
//
//        System.out.println();

        //time & space query (more restrictive): positive lon, positive lat quadrant
        List<NObject> res = new ArrayList();
        db.find(new Query().bounds(new RectDoubleND(
                new double[]{1.48252053E12f, 130, 0, Double.NEGATIVE_INFINITY},
                new double[]{1.48250336E12f, +180, +90, Double.POSITIVE_INFINITY}
        ))).forEachObject(res::add);

        int bNum = res.size();
        assertTrue(bNum > 0);
        System.out.println("\t" + res + "\n" + "bNum=" + bNum + ", aNum=" + aNum);
        assertTrue(bNum != aNum);

    }


}