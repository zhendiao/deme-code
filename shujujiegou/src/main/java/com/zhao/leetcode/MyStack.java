package com.zhao.leetcode;

import java.util.LinkedList;
import java.util.Queue;

class MyStack {
    Queue<Integer> queue;
    /** Initialize your data structure here. */
    public MyStack() {
            queue = new LinkedList<Integer>();
    }
    
    /** Push element x onto stack. */
    public void push(int x) {
            queue.offer(x);
    }
    
    /** Removes the element on top of the stack and returns that element. */
    public int pop() {
        int n = queue.size();
        for (int i=0;i<n-1;i++){
            queue.offer(queue.poll());
        }
        return queue.poll();
    }
    
    /** Get the top element. */
    public int top() {
        int n = queue.size();
        for (int i=0;i<n-1;i++){
            queue.offer(queue.poll());
        }
       int ret = queue.poll();
        queue.offer(ret);
        return ret;
    }
    
    /** Returns whether the stack is empty. */
    public boolean empty() {
           return queue.isEmpty();
    }
}

/**
 * Your MyStack object will be instantiated and called as such:
 * MyStack obj = new MyStack();
 * obj.push(x);
 * int param_2 = obj.pop();
 * int param_3 = obj.top();
 * boolean param_4 = obj.empty();
 */