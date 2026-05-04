package me.zonesafe.zonesafe_be.domain;

import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
public class PageResponseDto<T>{
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponseDto(Page<T> pageResult) {
        this.content = pageResult.getContent();
        this.page = pageResult.getNumber(); // 0부터 시작하는 현재 페이지 번호
        this.size = pageResult.getSize();
        this.totalElements = pageResult.getTotalElements();
        this.totalPages = pageResult.getTotalPages();
    }
}
