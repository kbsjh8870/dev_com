package com.likelion.dev_community.common.viewcount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import com.likelion.dev_community.domain.question.repository.QuestionRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewCountServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private QuestionRepository questionRepository;

    private ViewCountServiceImpl viewCountService;

    @BeforeEach
    void setUp() {
        viewCountService = new ViewCountServiceImpl(redisTemplate, questionRepository);
    }

    @Test
    void 처음_조회한_뷰어면_조회수를_증가시켜야_한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("view:10:127.0.0.1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        boolean result = viewCountService.shouldIncrease(10L, "127.0.0.1");

        assertThat(result).isTrue();
    }

    @Test
    void 이미_조회한_뷰어면_조회수를_증가시키지_않는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("view:10:127.0.0.1"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        boolean result = viewCountService.shouldIncrease(10L, "127.0.0.1");

        assertThat(result).isFalse();
    }

    @Test
    void redis_setIfAbsent이_null을_반환해도_증가시키지_않는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("view:10:127.0.0.1"), eq("1"), any(Duration.class)))
                .thenReturn(null);

        boolean result = viewCountService.shouldIncrease(10L, "127.0.0.1");

        assertThat(result).isFalse();
    }

    @Test
    void redis_장애시_예외를_전파하지_않고_조회수_증가를_건너뛴다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("view:10:127.0.0.1"), eq("1"), any(Duration.class)))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        boolean result = viewCountService.shouldIncrease(10L, "127.0.0.1");

        assertThat(result).isFalse();
    }

    @Test
    void 서로_다른_뷰어키는_서로_다른_redis_key로_구분된다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("view:10:userA"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.setIfAbsent(eq("view:10:userB"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(viewCountService.shouldIncrease(10L, "userA")).isTrue();
        assertThat(viewCountService.shouldIncrease(10L, "userB")).isTrue();
    }

    @Test
    void flush는_스냅샷을_떠서_질문별_증가분을_DB에_반영하고_반영된_항목만_지운다() {
        when(redisTemplate.hasKey(ViewCountServiceImpl.FLUSHING_KEY)).thenReturn(false);
        when(redisTemplate.hasKey(ViewCountServiceImpl.PENDING_KEY)).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> deltas = new LinkedHashMap<>();
        deltas.put("10", "7");
        deltas.put("20", "3");
        when(hashOperations.entries(ViewCountServiceImpl.FLUSHING_KEY)).thenReturn(deltas);
        when(questionRepository.addViewCount(10L, 7L)).thenReturn(1);
        // 두 번째 질문 반영 중 DB 장애
        doThrow(new QueryTimeoutException("db down")).when(questionRepository).addViewCount(20L, 3L);

        viewCountService.flush();

        verify(redisTemplate).rename(ViewCountServiceImpl.PENDING_KEY, ViewCountServiceImpl.FLUSHING_KEY);
        verify(questionRepository).addViewCount(10L, 7L);
        verify(hashOperations).delete(ViewCountServiceImpl.FLUSHING_KEY, "10");
        // 실패한 질문은 지우지 않아 다음 주기에 재시도된다
        verify(hashOperations, never()).delete(ViewCountServiceImpl.FLUSHING_KEY, "20");
    }

    @Test
    void 이전_flush가_남긴_스냅샷이_있으면_새_스냅샷을_만들지_않고_기존것부터_반영한다() {
        when(redisTemplate.hasKey(ViewCountServiceImpl.FLUSHING_KEY)).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(ViewCountServiceImpl.FLUSHING_KEY)).thenReturn(Map.of("20", "3"));

        viewCountService.flush();

        verify(redisTemplate, never()).rename(ViewCountServiceImpl.PENDING_KEY, ViewCountServiceImpl.FLUSHING_KEY);
        verify(questionRepository).addViewCount(20L, 3L);
    }
}
