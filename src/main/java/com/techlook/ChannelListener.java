package com.techlook;


public interface ChannelListener {
    void channelError(Fault error);

    void response(byte[] chunk);

    void close();
}
