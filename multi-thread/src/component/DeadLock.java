package component;

public class DeadLock {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    public static void main(String[] args) {
        new DeadLock().test();
    }

    public void test() {
        new Thread(() -> {
            synchronized (lock1) {
                try {
                    Thread.sleep(1000);
                    System.out.println("thread1 欲获取 lock2");
                    synchronized (lock2) {
                        System.out.println("获取 lock2 成功!");
                    }
                } catch (InterruptedException e) {
                    System.out.println("暂停异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            synchronized (lock2) {
                try {
                    Thread.sleep(1000);
                    System.out.println("thread2 欲获取 lock1");
                    synchronized (lock1) {
                        System.out.println("获取 lock1 成功!");
                    }
                } catch (InterruptedException e) {
                    System.out.println("暂停异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
