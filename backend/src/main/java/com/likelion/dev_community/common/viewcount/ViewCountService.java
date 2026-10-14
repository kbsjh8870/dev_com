package com.likelion.dev_community.common.viewcount;

public interface ViewCountService {

    boolean shouldIncrease(Long questionId, String viewerKey);

    void increase(Long questionId);

    long pendingCount(Long questionId);
}
