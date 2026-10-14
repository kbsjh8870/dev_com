package com.likelion.dev_community.domain.question.repository;

import com.likelion.dev_community.domain.question.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuestionRepository extends JpaRepository<Question, Long>, QuestionRepositoryCustom {

    // 최신순
    @EntityGraph(attributePaths = "author")
    Page<Question> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 추천순
    @EntityGraph(attributePaths = "author")
    Page<Question> findAllByOrderByLikeCountDesc(Pageable pageable);

    // 미해결순
    @Query(value = "select q from Question q join fetch q.author " +
            "order by case when q.status = com.likelion.dev_community.domain.question.entity.QuestionStatus.OPEN then 0 else 1 end, " +
            "q.createdAt desc",
            countQuery = "select count(q) from Question q")
    Page<Question> findAllOrderByUnresolvedFirst(Pageable pageable);

    // 내 질문 목록
    @EntityGraph(attributePaths = "author")
    Page<Question> findAllByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update Question q set q.viewCount = q.viewCount + :delta where q.id = :id")
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);
}
