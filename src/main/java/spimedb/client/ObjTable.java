package spimedb.client;

import org.jetbrains.annotations.Nullable;
import org.teavm.jso.dom.html.HTMLBodyElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Node;
import spimedb.bag.ChangeBatcher;

import java.util.function.BiConsumer;

/**
 * Created by me on 1/18/17.
 */
public class ObjTable {

    private final ChangeBatcher<NObj, Node> updater;
    private final HTMLDocument doc;

    public ObjTable(Client client, HTMLBodyElement target) {

        doc = target.getOwnerDocument();

        HTMLElement content = doc.createElement("div");
        content.setAttribute("class", "sidebar");
        target.appendChild(content);

        updater = new ChangeBatcher<NObj,Node>(100, Node[]::new, client.obj) {

            @Nullable
            @Override
            public Node build(NObj n) {
                return ObjTable.this.build(n);
            }

            @Override
            public void update(Node[] added, Node[] removed) {
                for (Node h : removed)
                    content.removeChild(h);
                for (Node h : added)
                    content.appendChild(h);
            }

        };
    }

    public void forEach(BiConsumer<NObj, Node> each) {
        updater.forEach(each);
    }

    protected Node build(NObj n) {

        if (n.isLeaf())
            return null;

        String[] in = n.inh(true);
        String[] out = n.inh(false);
        if (in == null && out == null) {
            //leaf node, hide
            return null;
        }

        HTMLElement d = doc.createElement("div");

        HTMLElement link = doc.createElement("a").withText(n.name());

        if (in!=null)
            d.appendChild( doc.createElement("a").withText("<-- (" + in.length + ") ") ) ;
                //Arrays.toString(in)

        d.appendChild(link);

        if (out!=null)
            d.appendChild( doc.createElement("a").withText(" (" + out.length  + ") -->") );

        return d;
    }


}
