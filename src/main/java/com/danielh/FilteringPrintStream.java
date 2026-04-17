package com.danielh;

import java.io.*;

public class FilteringPrintStream extends PrintStream {
    private final PrintStream original;
    
    public FilteringPrintStream(PrintStream original) {
        super(new FilteringOutputStream(original), true);
        this.original = original;
    }
    
    private boolean shouldFilter(String str) {
        return str != null && (str.contains("Loading") || str.contains("loading"));
    }
    
    @Override
    public void println(String x) {
        if (!shouldFilter(x)) {
            original.println(x);
        }
    }
    
    @Override
    public void println(Object x) {
        String str = x == null ? "null" : x.toString();
        if (!shouldFilter(str)) {
            original.println(str);
        }
    }
    
    @Override
    public void print(String s) {
        if (!shouldFilter(s)) {
            original.print(s);
        }
    }
    
    @Override
    public void print(Object obj) {
        String str = obj == null ? "null" : obj.toString();
        if (!shouldFilter(str)) {
            original.print(str);
        }
    }
    
    @Override
    public void println() {
        original.println();
    }
    
    @Override
    public void flush() {
        original.flush();
    }
    
    // Inner class to handle binary output
    private static class FilteringOutputStream extends OutputStream {
        private final PrintStream original;
        
        FilteringOutputStream(PrintStream original) {
            this.original = original;
        }
        
        @Override
        public void write(int b) throws IOException {
            // Pass through - low level writes usually aren't filtered
        }
        
        @Override
        public void flush() throws IOException {
            original.flush();
        }
    }
}
