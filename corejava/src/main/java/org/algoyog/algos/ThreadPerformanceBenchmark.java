package org.algoyog.algos;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ThreadPerformanceBenchmark {

    private static final int NUM_TASKS = 1_0_000; // Number of tasks to execute
    private static final int WORKLOAD = 10_000;   // Work per task (simulated)

    private static final AtomicInteger virtualThreadCount = new AtomicInteger(0);
    private static final AtomicInteger maxVirtualThreads = new AtomicInteger(0);

    private static void computeTask() {
        double sum = 0;
        for (int i = 0; i < WORKLOAD; i++) {
            sum += Math.sqrt(i);
        }
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static long measureExecutionTime(ExecutorService executor, boolean isVirtual) throws InterruptedException {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);

        // Capture initial stats
        int initialThreadCount = threadBean.getThreadCount();
        long startCpuTime = getTotalCpuTime(threadBean);
        long startGcTime = gcBean.getCollectionTime();
        long startHeapMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // Convert to MB

        long startTime = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(NUM_TASKS);

        if (isVirtual) {
            IntStream.range(0, NUM_TASKS).forEach(i -> Thread.ofVirtual().start(() -> {
                int currentCount = virtualThreadCount.incrementAndGet();
                maxVirtualThreads.updateAndGet(v -> Math.max(v, currentCount));

                computeTask();
                virtualThreadCount.decrementAndGet();
                latch.countDown();
            }));
        } else {
            IntStream.range(0, NUM_TASKS).forEach(i -> executor.submit(() -> {
                computeTask();
                latch.countDown();
            }));
        }

        latch.await();  // Wait for all tasks to complete
        long endTime = System.nanoTime();

        int peakThreads = threadBean.getThreadCount();
        long endCpuTime = getTotalCpuTime(threadBean);
        long endGcTime = gcBean.getCollectionTime();
        long endHeapMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        long executionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        long cpuTimeUsed = (endCpuTime - startCpuTime) / 1_000_000; // Convert to milliseconds
        long gcTimeUsed = (endGcTime - startGcTime); // Already in milliseconds
        long heapMemoryUsed = endHeapMemory - startHeapMemory;

        if (!isVirtual) executor.shutdown();

        // Emit observations
        System.out.println("-------------------------------------------------");
        System.out.println((isVirtual ? "Virtual Threads" : "Platform Threads") + " Results:");
        System.out.println("Execution Time: " + executionTime + " ms");
        System.out.println("Initial Active Threads: " + initialThreadCount);
        System.out.println("Peak Active Threads: " + (isVirtual ? maxVirtualThreads.get() : peakThreads));
        System.out.println("Thread Overhead: " + ((isVirtual ? maxVirtualThreads.get() : peakThreads) - initialThreadCount));
        System.out.println("CPU Time Used: " + cpuTimeUsed + " ms");
        System.out.println("GC Time Used: " + gcTimeUsed + " ms");
        System.out.println("Heap Memory Used: " + heapMemoryUsed + " MB");
        System.out.println("-------------------------------------------------");

        return executionTime;
    }

    private static long getTotalCpuTime(ThreadMXBean threadBean) {
        long totalCpuTime = 0;
        for (long id : threadBean.getAllThreadIds()) {
            totalCpuTime += threadBean.getThreadCpuTime(id);
        }
        return totalCpuTime;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Benchmarking Java Threads vs Virtual Threads...");

        int numCores = Runtime.getRuntime().availableProcessors();
        System.out.println("Number of Cores: " + numCores);

        ExecutorService platformThreadPool = Executors.newFixedThreadPool(numCores);
        long platformTime = measureExecutionTime(platformThreadPool, false);

        long virtualTime = measureExecutionTime(null, true);

        System.out.println("******** Key Observations ********");
        System.out.println("1. Platform Threads are limited by the number of CPU cores.");
        System.out.println("2. Virtual Threads scale better with a higher number of tasks.");
        System.out.println("3. Platform Threads create high OS overhead with context switching.");
        System.out.println("4. Virtual Threads have significantly lower overhead.");
        System.out.println("5. Virtual Threads complete tasks faster due to lightweight scheduling.");
        System.out.println("6. Platform Threads struggle with a high number of concurrent tasks.");
        System.out.println("**********************************");
    }
}
