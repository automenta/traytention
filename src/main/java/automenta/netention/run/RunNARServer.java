package automenta.netention.run;

import automenta.netention.web.shell.NARServer;
import nars.util.db.InfiniPeer;

/**
 * Created by me on 6/6/15.
 */
public class RunNARServer {

    public static void main(String[] args) throws Exception {
        new NARServer(InfiniPeer.local("nars"), 8080).start();
    }
}