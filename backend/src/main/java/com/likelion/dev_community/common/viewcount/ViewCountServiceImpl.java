package com.likelion.dev_community.common.viewcount;

import com.likelion.dev_community.domain.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private static final String KEY_PREFIX = "view:";
    private static final Duration TTL = Duration.ofHours(24);

    // 아직 DB에 반영되지 않은 조회수 증가분(field=questionId, value=증가량)
    static final String PENDING_KEY = "view:pending";
    // 반영 중인 스냅샷. 반영 도중 실패하면 남아 있다가 다음 주기에 이어 처리
    static final String FLUSHING_KEY = "view:flushing";

    private final StringRedisTemplate redisTemplate;
    private final QuestionRepository questionRepository;

    @Override
    public boolean shouldIncrease(Long questionId, String viewerKey) {
        String key = KEY_PREFIX + questionId + ":" + viewerKey;

        try {
            Boolean firstView = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            return Boolean.TRUE.equals(firstView);
        } catch (DataAccessException e) {
            log.warn("조회수 중복 확인 실패 - questionId={}, 조회수 증가를 건너뜁니다", questionId, e);
            return false;
        }
    }

    // 조회 요청마다 DB row를 UPDATE하면 인기 글 하나에 X락 경합이 몰림 ->  Redis에 원자적 누적 후 flush()가 주기적으로 묶어 DB 반영
    @Override
    public void increase(Long questionId) {
        try {
            redisTemplate.opsForHash().increment(PENDING_KEY, questionId.toString(), 1);
        } catch (DataAccessException e) {
            log.warn("조회수 누적 실패 - questionId={}, 조회수 증가를 건너뜁니다", questionId, e);
        }
    }

    // 아직 DB에 반영되지 않은 증가분
    @Override
    public long pendingCount(Long questionId) {
        try {
            String field = questionId.toString();
            return toLong(redisTemplate.opsForHash().get(PENDING_KEY, field)) + toLong(redisTemplate.opsForHash().get(FLUSHING_KEY, field));
        } catch (DataAccessException e) {
            return 0;
        }
    }

    @Scheduled(fixedDelay = 10_000)
    public void flush() {
        try {
            // 이전 주기에 처리하다 남은 스냅샷이 없을 때만 새 스냅샷 생성.
            // RENAME은 원자적이라 스냅샷 이후 들어온 증가분은 새 PENDING_KEY에 쌓이게 됨.
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(FLUSHING_KEY))) {
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(PENDING_KEY))) {
                    return;
                }
                redisTemplate.rename(PENDING_KEY, FLUSHING_KEY);
            }

            Map<Object, Object> deltas = redisTemplate.opsForHash().entries(FLUSHING_KEY);

            for (Map.Entry<Object, Object> entry : deltas.entrySet()) {
                questionRepository.addViewCount(Long.valueOf((String) entry.getKey()), toLong(entry.getValue()));
                // 반영된 질문 지워서 중간에 실패해도 남은 질문만 다음 주기에 다시 반영
                redisTemplate.opsForHash().delete(FLUSHING_KEY, entry.getKey());
            }
        } catch (DataAccessException e) {
            log.warn("조회수 DB 반영 실패 - 다음 주기에 재시도합니다", e);
        }
    }

    private long toLong(Object value) {
        return value == null ? 0 : Long.parseLong((String) value);
    }
}
