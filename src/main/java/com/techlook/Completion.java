package com.techlook;

public class Completion {
    private boolean inProgress = true;
    private int timeout;

    public Completion() {
    }

    public Completion(int timeout) {
        this.timeout = timeout;
    }

    public synchronized void finish() {
        inProgress = false;

        notifyAll();
    }

    public synchronized void await() throws InterruptedException {
        if (inProgress) {
            if (timeout >= 0) {
                wait(timeout);
            } else {
                wait();
            }
        }
    }
}
