package com.likelion.dev_community.domain.question.entity;

import com.likelion.dev_community.common.entity.BaseTimeEntity;
import com.likelion.dev_community.common.exception.CustomException;
import com.likelion.dev_community.common.exception.ErrorCode;
import com.likelion.dev_community.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "questions")
@SQLRestriction("deleted_at is null")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status;

    @Column(nullable = false)
    private boolean isPremium;

    @Column(nullable = false)
    private boolean isAnonymous;

    // columnDefinition으로 varchar를 강제한다. 그냥 length만 지정하면 MySQL 네이티브 enum(...)으로
    // 생성되는데, 이 네이티브 enum은 값이 정수 인덱스로 저장돼서 ddl-auto가 리터럴 순서를 다시 쓸 때마다
    // 기존 행의 값이 통째로 뒤바뀌는 사고가 난다(실제로 겪음 - 기존 GENERAL 질문이 CAREER_CONSULT로 보였음).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private QuestionType type;

    // 커리어상담 채팅이 한 번이라도 개설되면 true로 고정되어 이후 글 유형을 바꿀 수 없다.
    @Column(nullable = false)
    private boolean typeLocked;

    // AI 요약 결과 캐시. 최초 요청 시 생성해서 저장해두고 이후에는 재호출 없이 재사용한다.
    @Column(columnDefinition = "TEXT")
    private String summary;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionTag> questionTags = new ArrayList<>();

    @Builder
    public Question(User author, String title, String content, boolean isPremium, boolean isAnonymous, QuestionType type) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.viewCount = 0;
        this.likeCount = 0;
        this.status = QuestionStatus.OPEN;
        this.isPremium = isPremium;
        this.isAnonymous = isAnonymous;
        this.type = type != null ? type : QuestionType.GENERAL;
    }

    public void update(String title, String content, boolean isAnonymous, QuestionType type) {
        if (this.typeLocked && this.type != type) {
            throw new CustomException(ErrorCode.QUESTION_TYPE_LOCKED);
        }
        this.title = title;
        this.content = content;
        this.isAnonymous = isAnonymous;
        this.type = type;
        this.summary = null;
    }

    public void lockType() {
        this.typeLocked = true;
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    // F-09
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.status = QuestionStatus.DELETED;
    }

    public void resolve() {
        this.status = QuestionStatus.RESOLVED;
    }

    public void reopen() {
        this.status = QuestionStatus.OPEN;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
    }

    public void createTag(Tag tag, int sortOrder) {
        QuestionTag questionTag = QuestionTag.builder()
                .question(this)
                .tag(tag)
                .sortOrder(sortOrder)
                .build();
        this.questionTags.add(questionTag);
    }
}
