package com.vidyo.vidyoconnector.vitel.job;

public interface JobApi {

    void postJob(Job job);

    void postDownloadJob(Job job);

    void cancel(Job job);

    int coreSize();
}