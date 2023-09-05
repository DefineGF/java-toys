package component;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MBlockingQueue<T> {
    private final LinkedList<T> list;
    private final int capacity;
    private final Lock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    public MBlockingQueue(int cap) {
        this.capacity = cap;
        this.list = new LinkedList<>();
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
    }

    public void enqueue(T item) throws InterruptedException {
        lock.lock();
        try {
            while (list.size() == capacity) {
                notFull.await(); // 如果队列已满，则等待直到队列不满
            }
            list.offer(item);
            notEmpty.signal(); // 唤醒可能在等待数据的线程
        } finally {
            lock.unlock();
        }
    }

    public T dequeue() throws InterruptedException {
        lock.lock();
        try {
            while (list.isEmpty()) {
                notEmpty.await(); // 如果队列为空，则等待直到队列非空
            }
            T item = list.poll();
            notFull.signal(); // 唤醒可能在等待空间的线程
            return item;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }
}
