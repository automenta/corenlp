package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by me on 8/11/15.
 */
public class Int2 implements IPair<Integer,Integer> {

    public final int a;
    public final int b;

    public Int2(int a, int b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public Integer first() {
        return a;
    }

    @Override
    public Integer second() {
        return b;
    }

    @Override
    public List<Object> asList() {
        ArrayList x = new ArrayList(2);
        x.add(a);
        x.add(b);
        return x;
    }

    @Override
    public void save(DataOutputStream out) {
        try {
            out.writeInt(a);
            out.writeInt(b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public int compareTo(IPair<Integer, Integer> another) {

        int comp = Integer.compare(a, another.first());
        if (comp != 0)
            return comp;

        comp = Integer.compare(b, another.second());
        return comp;
    }

    @Override
    public void prettyLog(Redwood.RedwoodChannels channels, String description) {
        throw new RuntimeException("unimplemented");
    }


}
