package org.geysermc.extension.connect.utils;
public record BedrockEndpoint(String address,int port) {
    public BedrockEndpoint {
        if(address==null || address.isBlank() || port<1 || port>65535)
            throw new IllegalArgumentException("Invalid Bedrock endpoint");
        address=address.trim();
    }
    public static BedrockEndpoint fromForm(String address,String port) {
        return new BedrockEndpoint(address,port==null || port.isBlank()?19132:Integer.parseInt(port.trim()));
    }
    public static BedrockEndpoint fromCommand(String value) {
        if(value==null) throw new IllegalArgumentException("Missing address");
        value=value.trim();
        if(value.startsWith("[")) {
            int end=value.indexOf(']');
            if(end<0) throw new IllegalArgumentException("Invalid IPv6 address");
            String suffix=value.substring(end+1);
            if(!suffix.isEmpty() && !suffix.startsWith(":")) throw new IllegalArgumentException("Invalid port");
            return fromForm(value.substring(1,end),suffix.isEmpty()?"":suffix.substring(1));
        }
        int colon=value.indexOf(':');
        return colon>=0 && colon==value.lastIndexOf(':')
            ?fromForm(value.substring(0,colon),value.substring(colon+1)):fromForm(value,"");
    }
}
