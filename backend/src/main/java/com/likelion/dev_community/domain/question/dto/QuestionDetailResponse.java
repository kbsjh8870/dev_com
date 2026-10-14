package com.likelion.dev_community.domain.question.dto;

import com.likelion.dev_community.common.avatar.AvatarPalette;
import com.likelion.dev_community.domain.question.entity.Question;
import com.likelion.dev_community.domain.question.entity.QuestionStatus;
import com.likelion.dev_community.domain.question.entity.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class QuestionDetailResponse {

    @Schema(example = "1", description = "질문 ID")
    private final Long id;
    @Schema(example = "3", description = "작성자 회원 ID")
    private final Long authorId;
    @Schema(example = "BE24-Team4", description = "작성자 닉네임. isAnonymous=true면 익명으로 대체됨")
    private final String authorNickname;
    @Schema(example = "false", description = "작성자 전문가 인증 여부. 익명 작성 시 항상 false로 표시됨")
    private final boolean authorIsExpert;

    @Schema(example = "Spring Security 인증 관련 질문입니다", description = "질문 제목")
    private final String title;
    @Schema(example = "JWT 토큰 검증 시 401이 발생하는데 원인을 모르겠습니다.", description = "질문 본문")
    private final String content;
    private final QuestionStatus status;

    @Schema(example = "152", description = "조회수")
    private final int viewCount;
    @Schema(example = "24", description = "추천수")
    private final int likeCount;

    private final List<String> tags;
    @Schema(example = "2026-08-23T10:00:00", description = "질문 작성일시")
    private final LocalDateTime createdAt;
    @Schema(example = "false", description = "프리미엄(구독자 전용) 게시판 글 여부")
    private final boolean isPremium;
    @Schema(example = "false", description = "익명 작성 여부")
    private final boolean isAnonymous;

    private final QuestionType type;
    @Schema(example = "false", description = "질문 유형 변경 가능 여부. 커리어상담 채팅이 한 번이라도 개설되면 true로 고정되어 이후 유형 변경 불가")
    private final boolean typeLocked;
    @Schema(example = "5", description = "조회자가 이 질문(커리어상담)에 대해 이미 개설한 채팅방 ID. 없으면 null")
    private final Long myChatRoomId;

    @Schema(example = "#2563eb", description = "작성자 아바타 색(HEX). 멤버십 색상을 뽑지 않았거나 비멤버면 null(프론트 기본 파란색), 익명 글이면 공용 회색으로 고정됨")
    private final String authorAvatarColor;

    public static QuestionDetailResponse of(Question question, List<String> tags) {
        return of(question, tags, null);
    }

    public static QuestionDetailResponse of(Question question, List<String> tags, Long myChatRoomId) {
        return of(question, tags, myChatRoomId, question.getViewCount());
    }

    public static QuestionDetailResponse of(Question question, List<String> tags, Long myChatRoomId, int viewCount) {
        return new QuestionDetailResponse(
                question.getId(),
                question.getAuthor().getId(),
                question.isAnonymous() ? "익명" : question.getAuthor().getDisplayNickname(),
                !question.isAnonymous() && question.getAuthor().isExpert(),
                question.getTitle(),
                question.getContent(),
                question.getStatus(),
                viewCount,
                question.getLikeCount(),
                tags,
                question.getCreatedAt(),
                question.isPremium(),
                question.isAnonymous(),
                question.getType(),
                question.isTypeLocked(),
                myChatRoomId,
                AvatarPalette.resolveAuthorColor(question.isAnonymous(), question.getAuthor().getAvatarColorHex())
        );
    }
}