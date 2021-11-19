package com.vidyo.vidyoconnector.vitel.job;

import androidx.annotation.NonNull;

import com.vidyo.vidyoconnector.utils.Logger;

import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorImpl implements JobApi {

    private final ThreadPoolExecutor workThreadPool;

    private final ThreadPoolExecutor downloadThreadPool;

    private final WeakHashMap<Runnable, Future> taskMap = new WeakHashMap<>();

    public ExecutorImpl() {
        int cores = coreSize();
        /* For app tasks */
        workThreadPool = new LocalExecutor(cores, cores, 1 /* One sec alive */, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        /* Preferred for downloads */
        int downloadLimit = 1;
        downloadThreadPool = new LocalExecutor(downloadLimit, downloadLimit, 1 /* One sec alive */, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    public void postJob(Job job) {
        Future future = workThreadPool.submit(job);
        taskMap.put(job, future);
    }

    @Override
    public void postDownloadJob(Job job) {
        Future future = downloadThreadPool.submit(job);
        taskMap.put(job, future);
    }

    @Override
    public void cancel(Job job) {
        /* Call to on cancel stuff. */
        if (job != null) {
            job.onCanceled();

            Future task = taskMap.get(job);
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    @Override
    public int coreSize() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static class LocalExecutor extends ThreadPoolExecutor {

        private LocalExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @NonNull
        @Override
        public Future<?> submit(Runnable task) {
            return super.submit(wrap(task));
        }

        private Runnable wrap(Runnable task) {
            return () -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    if (task instanceof Job) {
                        ((Job) task).onThrow(ex);
                    }
                }
            };
        }
    }
}