package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.ClipResponseDto;
import me.zonesafe.zonesafe_be.dto.PageResponseDto;
import me.zonesafe.zonesafe_be.service.ClipService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clips")
@RequiredArgsConstructor
public class ClipController {
    private final ClipService clipService;

    //클립 목록 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PageResponseDto<ClipResponseDto> getClips(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ClipResponseDto> pageResult = clipService.getClips(cameraId, from, to, pageable);
        return new PageResponseDto<>(pageResult);
    }

    //클립 다운로드
    @GetMapping("/{clipId}/download")
    public ResponseEntity<Resource> downloadClip(@PathVariable Long clipId) {
        Resource resource = clipService.loadClipResource(clipId);
        String filename = "clip_" + clipId + ".mp4";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    //클립 스트리밍 (Range Request 지원)
    private static final long STREAM_CHUNK_SIZE = 1024L * 1024L;

    @GetMapping("/{clipId}/stream")
    public ResponseEntity<ResourceRegion> streamClip(
            @PathVariable Long clipId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        Resource resource = clipService.loadClipResource(clipId);
        long contentLength = resource.contentLength();

        ResourceRegion region;
        HttpStatus status;
        if (rangeHeader == null || rangeHeader.isBlank()) {
            long rangeLength = Math.min(STREAM_CHUNK_SIZE, contentLength);
            region = new ResourceRegion(resource, 0, rangeLength);
            status = HttpStatus.OK;
        } else {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(STREAM_CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    //클립 썸네일
    @GetMapping("/{clipId}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long clipId) {
        Resource resource = clipService.loadThumbnailResource(clipId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    //클립 삭제 (TODO: Spring Security 도입 후 @PreAuthorize("hasRole('ADMIN')") 적용)
    @DeleteMapping("/{clipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClip(@PathVariable Long clipId) {
        clipService.deleteClip(clipId);
    }
}
