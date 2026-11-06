package org.geysermc.extension.connect.utils;
import java.util.*;

public final class PlayerServerList {
    private final List<Server> records;
    private boolean dirty;
    public PlayerServerList(List<Server> records) { this.records=new ArrayList<>(records); }
    public List<Server> all() { return List.copyOf(records); }
    public List<Server> visible() { return records.stream().filter(Server::bedrock).toList(); }
    private int rawIndex(int visibleIndex) {
        int found=0;
        for(int i=0;i<records.size();i++) {
            if(records.get(i).bedrock() && found++==visibleIndex) return i;
        }
        throw new IndexOutOfBoundsException(visibleIndex);
    }
    public void add(Server server,int max) {
        if(!server.bedrock() || visible().size()>=max) throw new IllegalArgumentException("Server limit or type");
        records.add(server); dirty=true;
    }
    public void replace(int index,Server server) {
        if(!server.bedrock()) throw new IllegalArgumentException("Bedrock target required");
        records.set(rawIndex(index),server); dirty=true;
    }
    public void remove(int index) { records.remove(rawIndex(index)); dirty=true; }
    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty=false; }
}
