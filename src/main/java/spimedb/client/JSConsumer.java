package spimedb.client;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Created by me on 1/13/17.
 */
@JSFunctor
@FunctionalInterface
public interface JSConsumer extends JSObject {

    void accept(JSObject x);

}
