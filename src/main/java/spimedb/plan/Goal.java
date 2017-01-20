package spimedb.plan;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Created by me on 1/20/17.
 */
public abstract class Goal<A extends Agent> implements Serializable {



    @NotNull abstract public String id();

    @NotNull
    abstract public void DO(@NotNull A context, Consumer<Iterable<Goal<? super A>>> next) throws RuntimeException;

//    public void UNDO(A context) {
//
//    }

}
